package com.jiushi.adminpanel.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * 定时公告管理器
 * <p>
 * 服务器 tick 循环中定时广播预设消息.
 * 通过 {@link #setTimedMessage} 设置消息内容和间隔秒数.
 * message为空或interval≤0时停止广播.
 */
public class AdminManager {

    /** 定时公告消息内容 */
    private static volatile String timedMessage;
    /** 广播间隔秒数 */
    private static volatile int intervalSeconds;
    /** tick 计数器 (每秒20ticks) */
    private static volatile int tickCounter;
    /** 是否正在广播 */
    private static volatile boolean active;

    /**
     * 设置定时公告
     * @param message 消息内容 (为空或空字符串则停止)
     * @param interval 间隔秒数 (≤0则停止)
     */
    public static synchronized void setTimedMessage(String message, int interval) {
        timedMessage = message;
        intervalSeconds = interval;
        tickCounter = 0;
        active = (message != null && !message.isEmpty() && interval > 0);
    }

    /**
     * 每服务器 tick 调用 (ServerEvents.onServerTick).
     * 当 tickCounter 达到 intervalSeconds*20 tick 时, 广播消息并重置计数.
     */
    public static synchronized void onServerTick(MinecraftServer server) {
        if (!active || timedMessage == null || intervalSeconds <= 0) return;

        tickCounter++;
        int ticksForInterval = intervalSeconds * 20; // 秒 → tick 转换

        if (tickCounter >= ticksForInterval) {
            tickCounter = 0;
            // 绿色广播消息
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(timedMessage).withStyle(style -> style.withColor(ChatFormatting.GREEN)),
                    false);
        }
    }
}
