package com.jiushi.adminpanel.network;

import com.jiushi.adminpanel.AdminMod;
import com.jiushi.adminpanel.server.AdminManager;
import com.jiushi.adminpanel.server.BanManager;
import com.jiushi.adminpanel.server.MoneyManager;
import com.jiushi.adminpanel.server.SetupManager;
import com.jiushi.adminpanel.server.VoucherManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraftforge.network.NetworkEvent;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 管理员操作网络包
 * <p>
 * 客户端→服务端单向包. 支持的操作:
 * KICK(踢出) / BAN(封禁) / BROADCAST(广播) / TIMED_MSG(定时公告) /
 * MONEY(金币操作) / VOUCHER(兑换券) / ADD_OP_INVITE(生成邀请码) / REVOKE_ADMIN(撤销管理员)
 */
public class AdminPacket {

    /** 操作类型 */
    public enum Action {
        KICK, BAN, BROADCAST, TIMED_MSG, MONEY, VOUCHER, ADD_OP_INVITE, REVOKE_ADMIN
    }

    private Action action;      // 操作类型
    private String target;      // 目标玩家名 (可为空)
    private String message;     // 附加消息 (广播内容/封禁原因/金额等)
    private long interval;       // 附加数值 (定时间隔秒数/封禁分钟数)

    public AdminPacket() {}

    public AdminPacket(Action action, String target, String message, long interval) {
        this.action = action;
        this.target = target;
        this.message = message;
        this.interval = interval;
    }

    /** 反序列化: 从网络字节流解析 */
    public AdminPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.target = buf.readUtf();
        this.message = buf.readUtf();
        this.interval = buf.readLong();
    }

    /** 序列化: 写入网络字节流 */
    public static void encode(AdminPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action);
        buf.writeUtf(packet.target != null ? packet.target : "");
        buf.writeUtf(packet.message != null ? packet.message : "");
        buf.writeLong(packet.interval);
    }

    /** 判断玩家是否为管理员 (本模组认证 或 原生OP) */
    private static boolean isAdmin(ServerPlayer player) {
        return com.jiushi.adminpanel.server.SetupManager.isAdmin(player.getGameProfile().getName())
                || player.getServer().getPlayerList().isOp(player.getGameProfile());
    }

    /**
     * 处理数据包 (服务端执行)
     * <p>
     * 根据 action 分发到不同的处理逻辑.
     * 所有操作在执行前都会检查发起者是否为管理员.
     */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (action) {
                case KICK: {
                    // === 踢出玩家 ===
                    if (!isAdmin(player)) return;
                    // 防止踢出服主/开发者
                    if (SetupManager.isOwner(target)) {
                        player.sendSystemMessage(Component.literal("§c无法踢出服主/开发者"));
                        break;
                    }
                    ServerPlayer targetPlayer = player.getServer().getPlayerList().getPlayerByName(target);
                    if (targetPlayer != null && targetPlayer.connection != null) {
                        targetPlayer.connection.disconnect(Component.literal(
                                message != null && !message.isEmpty() ? message : "你已被管理员踢出"));
                    } else {
                        player.sendSystemMessage(Component.literal("§c玩家 " + target + " 不在线"));
                    }
                    break;
                }

                case BAN: {
                    // === 封禁玩家 ===
                    if (!isAdmin(player)) return;
                    // 防止封禁服主/开发者
                    if (SetupManager.isOwner(target)) {
                        player.sendSystemMessage(Component.literal("§c无法封禁服主/开发者"));
                        break;
                    }
                    ServerPlayer banTarget = player.getServer().getPlayerList().getPlayerByName(target);
                    Date banExpiry = interval > 0
                            ? new Date(System.currentTimeMillis() + interval * 60000L) : null;
                    GameProfile banProfile;
                    if (banTarget != null) {
                        banProfile = banTarget.getGameProfile();
                    } else {
                        Optional<GameProfile> cached = player.getServer().getProfileCache().get(target);
                        if (cached.isPresent()) {
                            banProfile = cached.get();
                        } else {
                            banProfile = new GameProfile(UUID.nameUUIDFromBytes(
                                    ("OfflinePlayer:" + target).getBytes(StandardCharsets.UTF_8)), target);
                        }
                    }
                    if (!player.getServer().getPlayerList().getBans().isBanned(banProfile)) {
                        player.getServer().getPlayerList().getBans().add(
                                new UserBanListEntry(banProfile, new Date(), "AdminMod", banExpiry,
                                        message != null && !message.isEmpty() ? message : "你已被封禁"));
                    }
                    // 名字封禁 (解决在线模式下未上线过玩家的UUID无法匹配问题, 登录时校验)
                    BanManager.ban(target,
                            message != null && !message.isEmpty() ? message : "你已被封禁",
                            interval > 0 ? System.currentTimeMillis() + interval * 60000L : 0);
                    // 如果在线则立刻踢出
                    if (banTarget != null && banTarget.connection != null) {
                        banTarget.connection.disconnect(Component.literal(
                                message != null && !message.isEmpty() ? message : "你已被封禁"));
                    }
                    player.sendSystemMessage(Component.literal("§a已封禁 " + target
                            + (banExpiry != null ? " (" + interval + "分钟)" : " (永久)")));
                    break;
                }

                case BROADCAST: {
                    // === 广播公告 (全体可见黄色消息) ===
                    if (!isAdmin(player)) return;
                    player.getServer().getPlayerList().broadcastSystemMessage(
                            Component.literal(message).withStyle(style -> style.withColor(ChatFormatting.YELLOW)),
                            false);
                    break;
                }

                case TIMED_MSG: {
                    // === 定时公告 (启动/停止循环广播) ===
                    if (!isAdmin(player)) return;
                    // message为空则停止, interval=0也停止
                    AdminManager.setTimedMessage(message, interval);
                    break;
                }

                case MONEY: {
                    // === 金币操作 (给自己加/向他人转账) ===
                    if (!isAdmin(player)) return;
                    try {
                        int amount = Integer.parseInt(message);
                        if (amount <= 0) {
                            player.sendSystemMessage(Component.literal("§c金额必须大于0"));
                            break;
                        }
                        if (target != null && !target.isEmpty()) {
                            // 转账: 从自己扣 → 加给对方
                            int balance = MoneyManager.getMoney(player);
                            if (balance < amount) {
                                player.sendSystemMessage(Component.literal(
                                        "§c余额不足，当前余额: " + balance + " 币"));
                                moneyRefresh(player);
                                break;
                            }
                            MoneyManager.takeMoney(player, amount);
                            ServerPlayer targetPl = player.getServer().getPlayerList().getPlayerByName(target);
                            if (targetPl != null) {
                                MoneyManager.addMoney(targetPl, amount);
                                targetPl.sendSystemMessage(Component.literal("§a收到来自 "
                                        + player.getGameProfile().getName() + " 的 " + amount + " 币"));
                            } else {
                                // 目标离线: 按名加钱 (下次上线生效)
                                MoneyManager.addMoneyByName(player.getServer(), target, amount);
                            }
                            player.sendSystemMessage(Component.literal(
                                    "§a已向 " + target + " 转账 " + amount + " 币"));
                        } else {
                            // target为空: 给自己加钱
                            AdminMod.LOGGER.info("Admin {} self-added {} coins",
                                    player.getGameProfile().getName(), amount);
                            MoneyManager.addMoney(player, amount);
                        }
                        moneyRefresh(player);
                    } catch (NumberFormatException e) {
                        player.sendSystemMessage(Component.literal("§c无效的金额"));
                        AdminMod.LOGGER.warn("Invalid money amount from {}: {}",
                                player.getGameProfile().getName(), message);
                    }
                    break;
                }

                case VOUCHER: {
                    // === 生成兑换券 ===
                    // 设计意图: 兑换券定位为"现金/支票"，任何玩家消耗自身金币即可生成，可交易给他人兑换。
                    // 因此本操作不校验管理员权限，所有玩家均可使用。
                    try {
                        int voucherAmount = Integer.parseInt(message);
                        VoucherManager.createVoucher(player, voucherAmount);
                        moneyRefresh(player);
                    } catch (NumberFormatException e) {
                        player.sendSystemMessage(Component.literal("§c无效的金额"));
                        AdminMod.LOGGER.warn("Invalid voucher amount from {}: {}",
                                player.getGameProfile().getName(), message);
                    }
                    break;
                }

                case ADD_OP_INVITE: {
                    // === 生成管理员邀请码 (仅服主) ===
                    if (!SetupManager.isOwner(player.getGameProfile().getName())) return;
                    if (target == null || target.isEmpty()) break;
                    if (SetupManager.isAdmin(target)) {
                        player.sendSystemMessage(Component.literal("§c该玩家已是管理员"));
                        break;
                    }
                    String code = SetupManager.invitePlayer(target);
                    // 向服主显示邀请码
                    player.sendSystemMessage(Component.literal(
                            "§a邀请码: §e§l" + code + "§a  目标: " + target + "  有效期5分钟"));
                    // 同时刷新面板状态
                    ShopPacket.buildAndSendListResponse(player,
                            "§a邀请码: §e§l" + code + "§a  目标: " + target);
                    break;
                }

                case REVOKE_ADMIN: {
                    // === 撤销管理员权限 (仅服主) ===
                    if (!SetupManager.isOwner(player.getGameProfile().getName())) return;
                    if (SetupManager.removeAdmin(target, player.getGameProfile().getName())) {
                        player.sendSystemMessage(Component.literal(
                                "§a已移除 " + target + " 的管理员权限"));
                    } else {
                        player.sendSystemMessage(Component.literal("§c无法移除该管理员"));
                    }
                    break;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /** 刷新金币数据: 重新发送商店/面板数据给玩家 */
    private static void moneyRefresh(ServerPlayer player) {
        ShopPacket.buildAndSendListResponse(player, "");
    }
}
