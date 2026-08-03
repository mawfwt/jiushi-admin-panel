package com.jiushi.territory.event;

import com.jiushi.territory.client.TerritoryManageScreen;
import com.jiushi.territory.client.TerritoryScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 领地边界渲染器 (仅客户端)
 * <p>
 * 在 TerritoryManageScreen 中勾选领地后, 在世界内渲染对应领地的彩色边界框.
 * 官方领地: 红色框 (r=1, g=0.6, b=0)
 * 私人领地: 青色框 (r=0.3, g=1, b=0.3)
 * <p>
 * 渲染时机: AFTER_TRANSLUCENT_BLOCKS → 在透明方块之后绘制线条, 确保可见.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = "jiushi_territory")
public class TerritoryRenderEvents {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (TerritoryManageScreen.highlightedIds.isEmpty()) return; // 没有高亮的领地 → 跳过

        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        var player = mc.player;
        var poseStack = event.getPoseStack();
        var cam = event.getCamera().getPosition();

        // 将渲染偏移到玩家视角
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        // 获取线框渲染缓冲区
        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.LINES);

        // 防御: 数据未加载 (从未打开过管理界面/切换服务器) 时 list 可能为 null
        List<TerritoryScreen.TerritoryInfo> list = TerritoryScreen.TerritoryClientData.list;
        if (list == null) return;

        // 遍历高亮ID, 只渲染当前维度内的领地
        for (TerritoryScreen.TerritoryInfo ti : list) {
            if (ti == null) continue;
            if (!TerritoryManageScreen.highlightedIds.contains(ti.id)) continue;
            if (!ti.world.equals(player.level().dimension().location().toString())) continue;

            // 计算领地 AABB (Y轴从-64到320覆盖全高度)
            int minX = Math.min(ti.x1, ti.x2);
            int maxX = Math.max(ti.x1, ti.x2);
            int minZ = Math.min(ti.z1, ti.z2);
            int maxZ = Math.max(ti.z1, ti.z2);

            AABB box = new AABB(minX, -64, minZ, maxX + 1, 320, maxZ + 1);
            // 官方=红, 私人=青色
            float r = ti.official ? 1f : 0.3f;
            float g = ti.official ? 0.6f : 1f;
            float b = ti.official ? 0f : 0.3f;
            LevelRenderer.renderLineBox(poseStack, buffer, box, r, g, b, 0.8f);
        }

        poseStack.popPose();
    }
}
