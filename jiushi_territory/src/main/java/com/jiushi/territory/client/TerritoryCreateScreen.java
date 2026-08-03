package com.jiushi.territory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 领地创建界面 (客户端)
 * <p>
 * 流程: 点击"开始选区" → 关闭GUI回游戏 → 蹲下左键选起点/终点 → 选区完成自动弹出此界面
 * → 输入名称 → 选择私人/官方 → 发送创建请求 → 返回主面板
 * <p>
 * 与 TerritoryScreen 类似, 但专注于创建流程, 不显示领地列表.
 */
@OnlyIn(Dist.CLIENT)
public class TerritoryCreateScreen extends Screen {

    private static final int PANEL_W = 360;
    private int left;
    private String statusText = "";
    private int statusTimer;
    private boolean showOfficial;

    public TerritoryCreateScreen() {
        super(Component.literal("创建领地"));
    }

    @Override
    protected void init() {
        left = width / 2 - PANEL_W / 2;
        // 判断是否为OP (用于显示官方领地按钮)
        boolean isOp = Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.hasPermissions(2);
        showOfficial = isOp;
        buildWidgets();
    }

    private void buildWidgets() {
        int rowY = 32;
        int btnX2 = left + PANEL_W - 80;
        // 返回主面板按钮
        addRenderableWidget(Button.builder(Component.literal("§c返回"), btn -> {
            TerritorySelectionTracker.cancel();
            com.jiushi.adminpanel.client.MainScreen.currentTab =
                    com.jiushi.adminpanel.client.MainScreen.Tab.EXTENSIONS;
            Minecraft.getInstance().setScreen(new com.jiushi.adminpanel.client.MainScreen());
        }).bounds(btnX2, rowY, 65, 20).build());
        rowY += 22;

        if (TerritorySelectionTracker.ready) {
            // === 选区就绪 ===
            BlockPos s = TerritorySelectionTracker.startPos;
            BlockPos e = TerritorySelectionTracker.endPos;
            int size = TerritorySelectionTracker.sizeXZ();
            // 选区信息
            String info = "选区: (" + s.getX() + "," + s.getZ() + ") ~ ("
                    + e.getX() + "," + e.getZ() + ") 差=" + size;
            addRenderableWidget(Button.builder(Component.literal(info), btn -> {})
                    .bounds(left + 10, rowY, 300, 16).build());
            rowY += 18;

            // 领地名称输入
            EditBox nameBox = new EditBox(font, left + 10, rowY, 160, 18, Component.literal("领地名称"));
            nameBox.setMaxLength(20); addRenderableWidget(nameBox);

            // 私人领地按钮 (超限时不显示)
            if (size <= 128) {
                addRenderableWidget(Button.builder(Component.literal("§a私人"), btn -> {
                    String n = nameBox.getValue();
                    if (!n.isEmpty()) { doCreate(n, false); }
                }).bounds(left + 175, rowY, 35, 20).build());
            }
            // 官方领地按钮 (仅OP)
            if (isOpVisible()) {
                addRenderableWidget(Button.builder(Component.literal("§6官方"), btn -> {
                    String n = nameBox.getValue();
                    if (!n.isEmpty()) { doCreate(n, true); }
                }).bounds(left + 215, rowY, 35, 20).build());
            }
            // 清空重选
            addRenderableWidget(Button.builder(Component.literal("§7清空"), btn -> {
                TerritorySelectionTracker.cancel();
                clearWidgets(); buildWidgets();
            }).bounds(left + 260, rowY, 40, 20).build());
            rowY += 26;
        } else {
            // === 未选区 ===
            addRenderableWidget(Button.builder(Component.literal("§a▶ 开始选区"), btn -> {
                TerritorySelectionTracker.begin();
                if (minecraft != null) minecraft.setScreen(null); // 关闭GUI回游戏
            }).bounds(left + 80, rowY, 120, 24).build());
            rowY += 30;
            // 操作说明
            addRenderableWidget(Button.builder(Component.literal("操作说明"), btn -> {})
                    .bounds(left + 10, rowY, 80, 18).build());
            addRenderableWidget(Button.builder(Component.literal("说明: 蹲下左键选点, 右键重置"), btn -> {})
                    .bounds(left + 95, rowY, 200, 18).build());
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        renderBackground(gfx);
        gfx.fill(left, 20, left + PANEL_W, height - 40, 0xC0101010);
        gfx.renderOutline(left, 20, PANEL_W, height - 60, 0xFF444444);
        gfx.drawCenteredString(font, Component.literal("-- 创建领地 --"), width / 2, 18, 0xFF55AAFF);

        if (TerritorySelectionTracker.ready) {
            int s = TerritorySelectionTracker.sizeXZ();
            String info = "XZ差总和: " + s + (s > 128 ? " §c超限!" : " §aOK");
            gfx.drawCenteredString(font, Component.literal(info), width / 2, 30,
                    s > 128 ? 0xFFFF5555 : 0xFF55FF55);
        }
        if (statusTimer > 0) gfx.drawCenteredString(font, Component.literal(statusText),
                width / 2, height - 25, 0xFF55FF55);
        super.render(gfx, mouseX, mouseY, partial);
    }

    @Override
    public void tick() {
        if (statusTimer > 0) { statusTimer--; if (statusTimer == 0) statusText = ""; }
        super.tick();
    }

    @Override public boolean isPauseScreen() { return false; }

    /** 官方领地按钮可见性: 需要是OP且面板初始化时也判定为OP */
    private boolean isOpVisible() {
        if (!showOfficial) return false;
        if (Minecraft.getInstance().player == null) return false;
        return Minecraft.getInstance().player.hasPermissions(2);
    }

    /** 发送创建请求, 成功后返回主面板 */
    private void doCreate(String name, boolean official) {
        BlockPos s = TerritorySelectionTracker.startPos;
        BlockPos e = TerritorySelectionTracker.endPos;
        if (s == null || e == null) return;
        TerritoryScreen.sendPacket(new TerritoryScreen.TerritoryDataPacket(
                TerritoryScreen.TerritoryDataPacket.Action.CREATE,
                name + "|" + (official ? "1" : "0") + "|"
                        + s.getX() + "," + s.getZ() + "|"
                        + e.getX() + "," + e.getZ()));
        TerritorySelectionTracker.cancel();
        // 创建完成后返回主面板扩展页
        com.jiushi.adminpanel.client.MainScreen.currentTab =
                com.jiushi.adminpanel.client.MainScreen.Tab.EXTENSIONS;
        Minecraft.getInstance().setScreen(new com.jiushi.adminpanel.client.MainScreen());
    }

    private void setStatus(String text) { statusText = text; statusTimer = 60; }
}
