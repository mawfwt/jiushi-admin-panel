package com.jiushi.territory.client;

import com.jiushi.territory.client.TerritoryCreateScreen;
import com.jiushi.territory.client.TerritorySelectionTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 领地选区客户端事件处理器
 * <p>
 * 选区操作: 蹲下(Shift/Crouch) + 左键 → 设起点/终点
 * 蹲下 + 右键 → 重置选区
 * <p>
 * 使用 GLFW 底层鼠标事件检测 (而非 Minecraft 的 ClickBlockEvent), 避免干扰正常游戏操作.
 * 同时拦截原版攻击输入, 防止选点时不慎破坏方块.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = "jiushi_territory")
public class AddonClientEvents {

    private static boolean leftWasDown;   // 上一帧左键状态 (用于边沿检测)
    private static boolean rightWasDown;  // 上一帧右键状态

    /**
     * 拦截攻击输入: 选区模式 + 蹲下时, 左键不触发原版攻击/破坏
     * (修复: 之前选点时会把准星对准的方块真的挖掉)
     */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!TerritorySelectionTracker.active) return;
        if (mc.screen != null) return;
        if (!mc.player.isCrouching()) return;
        event.setCanceled(true); // 取消原版攻击, 只用于选区
    }

    /** 客户端 tick: 在选区激活模式下检测鼠标点击 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!TerritorySelectionTracker.active) return; // 选区未激活
        if (mc.screen != null) return;                  // 有GUI打开则不处理
        if (!mc.player.isCrouching()) return;           // 必须蹲下

        long window = mc.getWindow().getWindow();
        // GLFW 鼠标状态查询 (非事件, 每tick轮询)
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        // 边沿检测: 按下那一帧触发 (避免持续按住时重复触发)
        if (leftDown && !leftWasDown) onLeftClick(mc);
        if (rightDown && !rightWasDown) onRightClick(mc);

        leftWasDown = leftDown;
        rightWasDown = rightDown;
        if (!leftDown) leftWasDown = false;
        if (!rightDown) rightWasDown = false;
    }

    /** 左键: 设置选区点 */
    private static void onLeftClick(Minecraft mc) {
        var hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
        var pos = ((BlockHitResult) hit).getBlockPos();

        if (TerritorySelectionTracker.startPos == null) {
            // 第一次: 设起点
            TerritorySelectionTracker.startPos = pos;
            mc.player.displayClientMessage(Component.literal(
                    "§a起点: " + pos.getX() + "," + pos.getZ()
                            + "  (再次蹲下左键设终点, 右键重置)"), true);
        } else {
            // 第二次: 设终点 → 选区完成 → 打开创建界面
            TerritorySelectionTracker.endPos = pos;
            int s = TerritorySelectionTracker.sizeXZ();
            String msg = "§a终点: " + pos.getX() + "," + pos.getZ() + "  XZ差=" + s;
            if (s > 128) msg += " §c超限!";
            TerritorySelectionTracker.ready = true;
            mc.player.displayClientMessage(Component.literal(msg), true);
            mc.setScreen(new TerritoryCreateScreen()); // 自动打开创建界面
        }
    }

    /** 右键: 重置选区 */
    private static void onRightClick(Minecraft mc) {
        TerritorySelectionTracker.cancel();
        mc.player.displayClientMessage(Component.literal(
                "§e选区已重置，蹲下左键重新设置起点"), true);
    }
}
