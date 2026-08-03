package com.jiushi.friends.event;

import com.jiushi.friends.server.FriendManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 好友系统事件处理器
 * <p>
 * 服务器启动 → 初始化好友管理器
 * 玩家上线 → 通知所有好友
 * 玩家下线 → 通知所有好友
 */
@Mod.EventBusSubscriber(modid = "jiushi_friends")
public class FriendEvents {

    /** 服务器启动: 初始化友情管理器 (加载 friends.json 和 pending_requests.json) */
    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        FriendManager.init();
    }

    /** 玩家上线: 遍历好友列表, 向在线好友发送上线通知 */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String name = player.getGameProfile().getName();
        var server = player.getServer();
        for (var entry : FriendManager.getFriends(name)) {
            ServerPlayer friend = server.getPlayerList().getPlayerByName(entry);
            if (friend != null) {
                friend.sendSystemMessage(Component.literal("§a[好友] " + name + " 上线了"));
            }
        }
    }

    /** 玩家下线: 遍历好友列表, 向在线好友发送下线通知 */
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String name = player.getGameProfile().getName();
        var server = player.getServer();
        for (var entry : FriendManager.getFriends(name)) {
            ServerPlayer friend = server.getPlayerList().getPlayerByName(entry);
            if (friend != null) {
                friend.sendSystemMessage(Component.literal("§7[好友] " + name + " 下线了"));
            }
        }
    }
}
