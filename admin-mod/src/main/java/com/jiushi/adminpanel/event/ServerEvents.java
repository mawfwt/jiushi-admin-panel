package com.jiushi.adminpanel.event;

import com.jiushi.adminpanel.AdminMod;
import com.jiushi.adminpanel.server.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * 服务端事件处理器
 * <p>
 * 处理模组所有的服务端生命周期事件: 服务器启动/玩家加入/玩家离开/命令注册/右键物品/tick
 * <p>
 * 注册的命令:
 * <ul>
 *   <li>/tpa accept | deny - 接受或拒绝传送请求</li>
 *   <li>/admin add &lt;player&gt; - 直接添加管理员 (服主)</li>
 *   <li>/admin remove &lt;player&gt; - 移除管理员</li>
 *   <li>/admin list - 列出所有管理员</li>
 *   <li>/admin perm &lt;player&gt; &lt;perm&gt; &lt;true|false&gt; - 设置权限 (服主)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AdminMod.MODID)
public class ServerEvents {

    /**
     * 服务器启动时初始化所有管理器.
     * 配置目录: config/jiushi_admin/
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        java.nio.file.Path configPath = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("jiushi_admin");

        SetupManager.init(configPath);       // 管理员注册/认证
        ShopManager.init(configPath);        // 商店
        WarpManager.init(configPath);        // 传送点
        VoucherManager.init(configPath);     // 兑换券
        PermissionManager.init(configPath);  // 权限
        BanManager.init(configPath);         // 名字封禁
        AdminMod.LOGGER.info("All managers initialized");
    }

    /**
     * 玩家加入时:
     * 1. 确保计分板存在金币计分项
     * 2. 自动添加OP玩家为管理员
     * 3. 提示OP玩家注册
     * 4. 显示当前金币余额
     */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 名字封禁校验: 解决在线模式下离线封禁UUID不匹配的问题
        BanManager.BanEntry ban = BanManager.isBanned(player.getGameProfile().getName());
        if (ban != null) {
            String reason = ban.reason != null ? ban.reason : "你已被封禁";
            if (player.connection != null) {
                player.connection.disconnect(Component.literal(
                        "§c你已被封禁: " + reason
                                + (ban.expiry > 0 ? " (封禁至 " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
                                .format(new java.util.Date(ban.expiry)) + ")" : " (永久)")));
            }
            return;
        }

        MoneyManager.ensureMoneyObjective(player.getServer().getScoreboard());

        String playerName = player.getGameProfile().getName();
        boolean isOp = player.getServer().getPlayerList().isOp(player.getGameProfile());
        // 自动晋升: 首次OP → owner, 后续OP → admin
        SetupManager.tryAutoAdd(playerName, isOp);

        // OP但未注册 → 提示
        if (isOp && !SetupManager.isAdmin(playerName)) {
            player.sendSystemMessage(Component.literal(
                    "§e你有OP权限但未注册为管理员，请联系服主获取邀请码"));
        }

        // 显示余额
        int money = MoneyManager.getMoney(player);
        player.sendSystemMessage(Component.literal("§a当前余额: " + money + " 币"));
    }

    /** 玩家离开时: 清理其TPA相关请求 */
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        com.jiushi.adminpanel.network.TpaPacket.TpaManager.cleanupPlayer(
                player.getGameProfile().getName());
    }

    /**
     * 右键物品拦截: 检测兑换券.
     * 若手持物品为纸且NBT中包含 voucher_code → 触发兑换
     */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItemStack();
        if (stack.getItem() == Items.PAPER && stack.hasTag()
                && stack.getTag().contains("voucher_code")) {
            VoucherManager.redeemVoucher(player, stack);
            // 取消右键默认行为, 防止纸被消耗
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    /** 注册 /tpa 和 /admin 命令 */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // ===== /tpa accept | deny =====
        dispatcher.register(literal("tpa")
                // /tpa accept - 接受传送请求 → 请求者传送到接受者位置
                .then(literal("accept")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String requester = com.jiushi.adminpanel.network.TpaPacket.TpaManager
                                    .getRequester(player.getGameProfile().getName());
                            if (requester == null) {
                                player.sendSystemMessage(Component.literal(
                                        "§c没有待处理的传送请求"));
                                return 0;
                            }
                            ServerPlayer reqPlayer = player.getServer().getPlayerList()
                                    .getPlayerByName(requester);
                            if (reqPlayer != null) {
                                reqPlayer.teleportTo(player.serverLevel(),
                                        player.getX(), player.getY(), player.getZ(),
                                        player.getYRot(), player.getXRot());
                                reqPlayer.sendSystemMessage(Component.literal(
                                        "§a" + player.getGameProfile().getName()
                                                + " 接受了你的传送请求"));
                            }
                            com.jiushi.adminpanel.network.TpaPacket.TpaManager
                                    .removeRequest(player.getGameProfile().getName());
                            player.sendSystemMessage(Component.literal("§a已接受传送请求"));
                            return 1;
                        })
                )
                // /tpa deny - 拒绝传送请求
                .then(literal("deny")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String requester = com.jiushi.adminpanel.network.TpaPacket.TpaManager
                                    .getRequester(player.getGameProfile().getName());
                            if (requester != null) {
                                ServerPlayer reqPlayer = player.getServer().getPlayerList()
                                        .getPlayerByName(requester);
                                if (reqPlayer != null) {
                                    reqPlayer.sendSystemMessage(Component.literal(
                                            "§c" + player.getGameProfile().getName()
                                                    + " 拒绝了你的传送请求"));
                                }
                            }
                            com.jiushi.adminpanel.network.TpaPacket.TpaManager
                                    .removeRequest(player.getGameProfile().getName());
                            player.sendSystemMessage(Component.literal("§e已拒绝传送请求"));
                            return 1;
                        })
                )
        );

        // ===== /admin add | remove | list | perm =====
        dispatcher.register(literal("admin")
                // /admin add <player> - 直接添加管理员 (服主)
                .then(literal("add")
                        .then(argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    if (!SetupManager.isOwner(player.getGameProfile().getName())) {
                                        player.sendSystemMessage(Component.literal(
                                                "§c只有服主可以执行此操作"));
                                        return 0;
                                    }
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    SetupManager.directAddAdmin(
                                            target.getGameProfile().getName(),
                                            player.getGameProfile().getName());
                                    // 直接添加后自动设为OP
                                    target.getServer().getPlayerList().op(target.getGameProfile());
                                    player.sendSystemMessage(Component.literal(
                                            "§a已将 " + target.getGameProfile().getName()
                                                    + " 添加为管理员"));
                                    target.sendSystemMessage(Component.literal(
                                            "§a你已被添加为管理员"));
                                    return 1;
                                })
                        )
                )
                // /admin remove <player> - 移除管理员
                .then(literal("remove")
                        .then(argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    if (SetupManager.removeAdmin(
                                            target.getGameProfile().getName(),
                                            player.getGameProfile().getName())) {
                                        player.sendSystemMessage(Component.literal(
                                                "§a已移除 " + target.getGameProfile().getName()
                                                        + " 的管理员权限"));
                                    } else {
                                        player.sendSystemMessage(Component.literal("§c无法移除"));
                                    }
                                    return 1;
                                })
                        )
                )
                // /admin list - 列出所有管理员
                .then(literal("list")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            player.sendSystemMessage(Component.literal("§e=== 管理员列表 ==="));
                            for (var e : SetupManager.getAdmins().entrySet()) {
                                String prefix;
                                switch (e.getValue()) {
                                    case "developer": prefix = "§d[开发者]"; break;
                                    case "owner": prefix = "§c[服主]"; break;
                                    default: prefix = "§b[OP]"; break;
                                }
                                player.sendSystemMessage(Component.literal(
                                        prefix + " " + e.getKey()));
                            }
                            return 1;
                        })
                )
                // /admin perm <player> <perm> <true|false> - 设置权限
                .then(literal("perm")
                        .then(argument("player", EntityArgument.player())
                                .then(argument("perm", StringArgumentType.word())
                                        .then(argument("value", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource()
                                                            .getPlayerOrException();
                                                    if (!SetupManager.isOwner(
                                                            player.getGameProfile().getName())) {
                                                        player.sendSystemMessage(Component.literal(
                                                                "§c只有服主可以修改权限"));
                                                        return 0;
                                                    }
                                                    ServerPlayer target = EntityArgument
                                                            .getPlayer(ctx, "player");
                                                    String perm = StringArgumentType
                                                            .getString(ctx, "perm");
                                                    boolean value = Boolean.parseBoolean(
                                                            StringArgumentType
                                                                    .getString(ctx, "value"));
                                                    PermissionManager.setPermission(
                                                            target.getGameProfile().getName(),
                                                            perm, value);
                                                    player.sendSystemMessage(Component.literal(
                                                            "§a已设置 "
                                                                    + target.getGameProfile().getName()
                                                                    + " 的权限 " + perm + " = " + value));
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
        );
    }

    /** 服务器 tick: 每 tick 传递给 AdminManager 处理定时公告 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            AdminManager.onServerTick(event.getServer());
        }
    }
}
