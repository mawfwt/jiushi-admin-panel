package com.jiushi.adminpanel.network;

import com.jiushi.adminpanel.AdminMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * TPA 传送请求网络包
 * <p>
 * 支持三种操作: REQUEST(发起请求) / ACCEPT(接受) / DENY(拒绝)
 * <p>
 * 流程: A 通过面板点击 TPA → 服务端收到 REQUEST → B 收到提示 → B 输入 /tpa accept/deny → A 被传送到 B
 * <p>
 * TPAManager 内部类管理待处理请求映射: 目标玩家名 → 请求者玩家名
 */
public class TpaPacket {

    public enum Action {
        REQUEST, ACCEPT, DENY
    }

    private Action action;          // 操作类型
    private String targetPlayer;    // 目标玩家名 (REQUEST时为目标, ACCEPT/DENY时不使用)

    public TpaPacket() {}

    public TpaPacket(Action action, String targetPlayer) {
        this.action = action;
        this.targetPlayer = targetPlayer;
    }

    public TpaPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.targetPlayer = buf.readUtf();
    }

    public static void encode(TpaPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action);
        buf.writeUtf(packet.targetPlayer != null ? packet.targetPlayer : "");
    }

    /** 服务端处理 TPA 请求 */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            switch (action) {
                case REQUEST: {
                    // 查找目标玩家, 发送传送请求
                    ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(targetPlayer);
                    if (target != null) {
                        String targetName = target.getGameProfile().getName();
                        // 检查目标是否已有待处理的请求 (同一时间只能有一个)
                        String existingRequester = TpaManager.getRequester(targetName);
                        if (existingRequester != null) {
                            sender.sendSystemMessage(Component.literal(
                                    "§c该玩家已有待处理的传送请求 (来自: " + existingRequester + ")"));
                            break;
                        }
                        TpaManager.addRequest(sender.getGameProfile().getName(), targetName);
                        target.sendSystemMessage(Component.literal(
                                "§e" + sender.getGameProfile().getName()
                                        + " 请求传送到你身边，输入 /tpa accept 接受"));
                        sender.sendSystemMessage(Component.literal(
                                "§a已向 " + targetPlayer + " 发送传送请求"));
                    } else {
                        sender.sendSystemMessage(Component.literal(
                                "§c玩家 " + targetPlayer + " 不在线"));
                    }
                    break;
                }

                case ACCEPT: {
                    // 接受传送: 找到请求者 → 传送到接受者位置
                    ServerPlayer requester = sender.getServer().getPlayerList().getPlayerByName(
                            TpaManager.getRequester(sender.getGameProfile().getName()));
                    if (requester != null) {
                        requester.teleportTo(sender.serverLevel(),
                                sender.getX(), sender.getY(), sender.getZ(),
                                sender.getYRot(), sender.getXRot());
                        requester.sendSystemMessage(Component.literal(
                                "§a" + sender.getGameProfile().getName() + " 接受了你的传送请求"));
                        sender.sendSystemMessage(Component.literal("§a已接受传送请求"));
                    } else {
                        sender.sendSystemMessage(Component.literal(
                                "§c没有待处理的传送请求或请求者已离线"));
                    }
                    TpaManager.removeRequest(sender.getGameProfile().getName());
                    break;
                }

                case DENY: {
                    // 拒绝传送: 通知请求者
                    ServerPlayer reqPlayer = sender.getServer().getPlayerList().getPlayerByName(
                            TpaManager.getRequester(sender.getGameProfile().getName()));
                    if (reqPlayer != null) {
                        reqPlayer.sendSystemMessage(Component.literal(
                                "§c" + sender.getGameProfile().getName() + " 拒绝了你的传送请求"));
                    }
                    TpaManager.removeRequest(sender.getGameProfile().getName());
                    sender.sendSystemMessage(Component.literal("§e已拒绝传送请求"));
                    break;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * TPA 请求管理器
     * <p>
     * 管理等待中的传送请求: 目标玩家名 → 请求者玩家名
     * 使用 ConcurrentHashMap 保证线程安全
     */
    public static class TpaManager {
        /** 请求映射: 目标 → 请求者 */
        private static final java.util.Map<String, String> requests = new ConcurrentHashMap<>();

        /** 添加请求 */
        public static void addRequest(String from, String to) {
            requests.put(to, from);
        }

        /** 获取指向指定目标的请求者 (没有返回null) */
        public static String getRequester(String target) {
            return requests.get(target);
        }

        /** 移除指向指定目标的请求 */
        public static void removeRequest(String target) {
            requests.remove(target);
        }

        /**
         * 玩家离线时清理关联的请求:
         * 1. 移除指向该玩家的请求
         * 2. 移除该玩家发起的请求 (通过 values 中的 removeIf)
         */
        public static void cleanupPlayer(String playerName) {
            requests.remove(playerName);
            requests.values().removeIf(playerName::equals);
        }
    }
}
