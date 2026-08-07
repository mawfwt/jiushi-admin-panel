package com.jiushi.territory.event;

import com.jiushi.territory.server.TerritoryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 领地服务端事件处理器
 * <p>
 * 功能:
 * <ul>
 *   <li>破坏/放置/交互拦截 → 无权限则取消</li>
 *   <li>流体放置/活塞推入/爆炸破坏拦截 → 防止绕过领地保护</li>
 *   <li>官方领地闯入者强制冒险模式</li>
 *   <li>每10tick检查一次玩家位置, 1格移动阈值避免频繁切换</li>
 *   <li>玩家下线时恢复原始游戏模式</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "jiushi_territory")
public class TerritoryEvents {

    /** 被强制设为冒险模式的玩家: UUID → 原始游戏模式 (离开领地后恢复) */
    private static final Map<UUID, GameType> adventureForced = new ConcurrentHashMap<>();
    /** 上次检查的玩家坐标 (用于判断是否需要重新检查) */
    private static final Map<UUID, BlockPos> lastCheckedPos = new ConcurrentHashMap<>();
    /** 每个玩家的 tick 计数器 */
    private static final Map<UUID, Long> tickCounters = new ConcurrentHashMap<>();
    /** 领地检查间隔 (tick), 10tick=0.5秒 */
    private static final int CHECK_INTERVAL_TICKS = 10;
    /** 移动阈值平方, 移动小于1格不重新检查 */
    private static final double MOVE_THRESHOLD = 1.0;

    /** 服务器启动: 初始化领地管理器 */
    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        TerritoryManager.init();
    }

    /** 破坏方块拦截 */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!TerritoryManager.canInteract(player, event.getPos())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("§c你无权在此领地破坏方块"));
        }
    }

    /** 放置方块拦截 */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!TerritoryManager.canInteract(player, event.getPos())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("§c你无权在此领地放置方块"));
        }
    }

    /** 右键交互拦截 (箱子/门/按钮等) */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getPos() == null) return;
        if (!TerritoryManager.canInteract(player, event.getPos())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("§c你无权在此领地交互"));
            return;
        }
        // 液体桶: 点击的目标格子可能在领地外, 但液体实际会放置到相邻格 (可能越过领地边界)
        if (event.getFace() != null && event.getItemStack().getItem() instanceof BucketItem
                && !TerritoryManager.canInteract(player, event.getPos().relative(event.getFace()))) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("§c你无权在此领地放置液体"));
        }
    }

    /**
     * 流体流动拦截 (水/岩浆蔓延进入领地)
     * <p>
     * 该事件拿不到放置者实体, 采用近似判断:
     * 附近8格内有对目标位置有权限的玩家 → 视为其本人操作放行, 否则取消.
     * 覆盖: 从领地外倒入的液体、自然蔓延、发射器等非玩家来源.
     */
    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return; // 仅服务端处理
        // 流体将要放置的位置: getPos() 为目标位置 (流体放置位)
        BlockPos pos = event.getPos();
        String world = sl.dimension().location().toString();
        TerritoryManager.Territory t = TerritoryManager.getTerritoryAt(world, pos);
        if (t == null) return; // 不在领地内 → 不拦截
        for (ServerPlayer p : new ArrayList<>(sl.players())) {
            if (p.level().dimension().location().toString().equals(world)
                    && p.blockPosition().distSqr(pos) <= 64 // 8格距离平方
                    && TerritoryManager.canInteract(p, pos)) {
                return; // 有权限玩家在附近 → 放行
            }
        }
        event.setCanceled(true); // 无权限玩家在附近 → 取消流体放置
    }

    /**
     * 活塞拦截: 防止活塞把方块推入他人的领地
     * <p>
     * 沿活塞朝向检查最多12格落点, 若落点属于不同于活塞所在领地的领地 → 取消推动.
     */
    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (event.getLevel().isClientSide()) return;
        String world = "";
        if (event.getLevel() instanceof net.minecraft.world.level.Level level) {
            world = level.dimension().location().toString();
        }
        if (world.isEmpty()) return;
        BlockPos pistonPos = event.getPos();
        TerritoryManager.Territory pistonT = TerritoryManager.getTerritoryAt(world, pistonPos);
        Direction dir = event.getDirection();
        for (int i = 1; i <= 12; i++) {
            TerritoryManager.Territory targetT = TerritoryManager.getTerritoryAt(
                    world, pistonPos.relative(dir, i));
            if (targetT != null && targetT != pistonT) {
                event.setCanceled(true); // 推入不同领地 (或从领地外推入) → 拦截
                return;
            }
        }
    }

    /**
     * 爆炸拦截: 移除领地内方块的破坏效果
     * <p>
     * 玩家引爆且对该位置有权限 → 保留破坏; 其余 (无权限玩家/苦力怕/TNT等) → 移除破坏效果.
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide) return;
        String world = event.getLevel().dimension().location().toString();
        var ds = event.getExplosion().getDamageSource();
        final Entity source = ds != null ? ds.getEntity() : null;
        event.getAffectedBlocks().removeIf(pos -> {
            TerritoryManager.Territory t = TerritoryManager.getTerritoryAt(world, pos);
            if (t == null) return false; // 不在领地内 → 保留
            // 玩家引爆且位置有权限 → 保留破坏
            if (source instanceof ServerPlayer sp && TerritoryManager.canInteract(sp, pos)) {
                return false;
            }
            return true; // 移除该方块的破坏效果
        });
    }

    /**
     * 玩家 tick 检查
     * <p>
     * 逻辑:
     * 1. 每10tick检查一次 (降低性能开销)
     * 2. 玩家移动超过1格才重新检查 (避免在同一位置反复检查)
     * 3. 在官方领地且非owner/非白名单/非服主 → 强制冒险模式
     * 4. 离开领地 (或进入个人领地/有权限) → 恢复原始游戏模式
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        UUID id = player.getUUID();
        long counter = tickCounters.getOrDefault(id, 0L) + 1;
        tickCounters.put(id, counter);
        if (counter % CHECK_INTERVAL_TICKS != 0) return;

        BlockPos currentPos = player.blockPosition();
        BlockPos lastPos = lastCheckedPos.get(id);
        if (lastPos != null && currentPos.distSqr(lastPos) < MOVE_THRESHOLD * MOVE_THRESHOLD) return;
        lastCheckedPos.put(id, currentPos);

        var territory = TerritoryManager.getTerritoryAt(
                player.level().dimension().location().toString(), currentPos);

        boolean shouldForce = territory != null && territory.official
                && !territory.owner.equalsIgnoreCase(player.getGameProfile().getName())
                && !allowedContainsIgnoreCase(territory.allowed, player.getGameProfile().getName())
                && !com.jiushi.adminpanel.server.SetupManager.isOwner(player.getGameProfile().getName());

        if (shouldForce) {
            if (!adventureForced.containsKey(id)) {
                // 首次进入: 记录原始模式 → 强制冒险
                adventureForced.put(id, player.gameMode.getGameModeForPlayer());
                player.setGameMode(GameType.ADVENTURE);
            }
        } else {
            if (adventureForced.containsKey(id)) {
                GameType original = adventureForced.remove(id);
                if (original != null) {
                    player.setGameMode(original);
                }
            }
        }
    }

    /** 大小写不敏感判断白名单是否包含指定玩家 */
    private static boolean allowedContainsIgnoreCase(java.util.Set<String> allowed, String name) {
        for (String a : allowed) {
            if (a.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** 玩家下线时: 恢复原始游戏模式 + 清理跟踪数据 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        GameType original = adventureForced.remove(id);
        if (original != null && event.getEntity() instanceof ServerPlayer player) {
            player.setGameMode(original);
        }
        lastCheckedPos.remove(id);
        tickCounters.remove(id);
    }
}
