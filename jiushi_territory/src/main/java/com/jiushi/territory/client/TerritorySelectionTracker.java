package com.jiushi.territory.client;

import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 领地选区状态跟踪器 (仅客户端)
 * <p>
 * 玩家蹲下+左键→设起点, 再蹲下+左键→设终点, 蹲下+右键→重置.
 * 选区完成后调用 TerritoryCreateScreen 或 TerritoryScreen 创建领地.
 * <p>
 * 静态字段保持跨 Screen 实例状态.
 */
@OnlyIn(Dist.CLIENT)
public class TerritorySelectionTracker {
    /** 选区模式是否激活 */
    public static boolean active;
    /** 选区起点坐标 */
    public static BlockPos startPos;
    /** 选区终点坐标 */
    public static BlockPos endPos;
    /** 选区是否完成 (startPos和endPos都已设置) */
    public static boolean ready;

    /** 开始选区模式 */
    public static void begin() {
        active = true;
        startPos = null;
        endPos = null;
        ready = false;
    }

    /** 取消选区模式, 清除所有状态 */
    public static void cancel() {
        active = false;
        startPos = null;
        endPos = null;
        ready = false;
    }

    /** 消费 ready 标志 (Screen读取后重置) */
    public static void consume() {
        ready = false;
    }

    /** 计算选区 XZ 轴差总和 */
    public static int sizeXZ() {
        if (startPos == null || endPos == null) return 0;
        return Math.abs(endPos.getX() - startPos.getX()) + Math.abs(endPos.getZ() - startPos.getZ());
    }
}
