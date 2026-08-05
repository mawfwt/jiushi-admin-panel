package com.jiushi.adminpanel.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * 金币管理器 - 基于 Minecraft 原生计分板 (Scoreboard)
 * <p>
 * 使用计分项 "JiuShi_money" 存储每个玩家的金币余额.
 * 计分板数据由 Minecraft 自动持久化 (level.dat), 无需额外JSON文件.
 * 金币值不会低于0.
 */
public class MoneyManager {

    /** 计分板上的金币计分项名称 */
    public static final String MONEY_OBJECTIVE = "JiuShi_money";

    /** 确保计分板存在金币计分项, 不存在则创建 */
    public static void ensureMoneyObjective(Scoreboard scoreboard) {
        Objective objective = scoreboard.getObjective(MONEY_OBJECTIVE);
        if (objective == null) {
            scoreboard.addObjective(MONEY_OBJECTIVE, ObjectiveCriteria.DUMMY,
                    net.minecraft.network.chat.Component.literal("Money"),
                    ObjectiveCriteria.RenderType.INTEGER); // 显示为整数
        }
    }

    /** 获取玩家金币余额 */
    public static synchronized int getMoney(ServerPlayer player) {
        return getMoneyUnlocked(player);
    }

    /** 设置玩家金币余额 (不低于0) */
    public static synchronized void setMoney(ServerPlayer player, int amount) {
        setMoneyUnlocked(player, amount);
    }

    /** 内部读金币: 获取或创建计分项得分 */
    private static int getMoneyUnlocked(ServerPlayer player) {
        Scoreboard scoreboard = player.getServer().getScoreboard();
        ensureMoneyObjective(scoreboard);
        Score score = scoreboard.getOrCreatePlayerScore(player.getGameProfile().getName(),
                scoreboard.getObjective(MONEY_OBJECTIVE));
        return score.getScore();
    }

    /** 内部写金币: 用long防溢出, 最低为0 */
    private static void setMoneyUnlocked(ServerPlayer player, long amount) {
        Scoreboard scoreboard = player.getServer().getScoreboard();
        ensureMoneyObjective(scoreboard);
        Score score = scoreboard.getOrCreatePlayerScore(player.getGameProfile().getName(),
                scoreboard.getObjective(MONEY_OBJECTIVE));
        score.setScore((int) Math.min(Math.max(0L, amount), Integer.MAX_VALUE));
    }

    /** 给在线玩家增加金币 (最低0) */
    public static synchronized void addMoney(ServerPlayer player, int amount) {
        if (player == null) return;
        long current = getMoneyUnlocked(player);
        setMoneyUnlocked(player, current + (long) amount);
    }

    /** 从在线玩家扣除金币 (最低0) */
    public static synchronized void takeMoney(ServerPlayer player, int amount) {
        if (player == null) return;
        long current = getMoneyUnlocked(player);
        setMoneyUnlocked(player, current - (long) amount);
    }

    /**
     * 按玩家名增加金币 (支持离线玩家).
     * 用于商品交易中向离线卖家转账.
     */
    public static synchronized void addMoneyByName(net.minecraft.server.MinecraftServer server, String playerName, int amount) {
        if (playerName == null || playerName.isEmpty()) return;
        Scoreboard scoreboard = server.getScoreboard();
        ensureMoneyObjective(scoreboard);
        Score score = scoreboard.getOrCreatePlayerScore(playerName, scoreboard.getObjective(MONEY_OBJECTIVE));
        long current = score.getScore();
        score.setScore((int) Math.min(Math.max(0L, current + (long) amount), Integer.MAX_VALUE));
    }
}
