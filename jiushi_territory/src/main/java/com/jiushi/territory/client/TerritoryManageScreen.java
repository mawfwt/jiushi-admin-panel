package com.jiushi.territory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.util.*;

/**
 * 领地管理界面 (客户端)
 * <p>
 * 功能:
 * <ul>
 *   <li>领地列表 (含官方/私人标识 + 所有人)</li>
 *   <li>边界高亮开关 (□/■ 按钮, 勾选后在世界内显示彩色边框)</li>
 *   <li>领地删除 (仅owner或服主可见删除按钮)</li>
 * </ul>
 * <p>
 * 高亮的领地通过 {@link TerritoryRenderEvents} 在世界内渲染彩色边框.
 * 高亮状态是静态的 (关闭面板后仍然显示, 直到取消所有高亮).
 */
@OnlyIn(Dist.CLIENT)
public class TerritoryManageScreen extends Screen {

    private static final int PANEL_W = 420;
    /** 静态高亮集合: 勾选的领地ID (跨Screen持久) */
    public static final Set<Integer> highlightedIds = new HashSet<>();
    private int left;
    private String statusText = "";
    private int statusTimer;
    private List<TerritoryScreen.TerritoryInfo> territoryList = new ArrayList<>();

    public TerritoryManageScreen() {
        super(Component.literal("领地管理"));
    }

    @Override
    protected void init() {
        left = width / 2 - PANEL_W / 2;
        sendListRequest();
        buildWidgets();
    }

    private void buildWidgets() {
        int rowY = 32;
        // 返回主面板按钮
        addRenderableWidget(Button.builder(Component.literal("§c返回"), btn -> {
            com.jiushi.adminpanel.client.MainScreen.currentTab =
                    com.jiushi.adminpanel.client.MainScreen.Tab.EXTENSIONS;
            Minecraft.getInstance().setScreen(new com.jiushi.adminpanel.client.MainScreen());
        }).bounds(left + PANEL_W - 70, rowY, 55, 20).build());
        rowY += 26;

        // 领地列表
        for (TerritoryScreen.TerritoryInfo ti : territoryList) {
            if (rowY > height - 50) break;
            String label = (ti.official ? "§6[官] " : "§a[私] ") + ti.name + " §7-" + ti.owner;
            addRenderableWidget(Button.builder(Component.literal(label), btn -> {})
                    .bounds(left + 10, rowY, 200, 18).build());

            // 边界高亮开关
            boolean highlight = highlightedIds.contains(ti.id);
            addRenderableWidget(Button.builder(Component.literal(highlight ? "§e■" : "§7□"), btn -> {
                if (highlight) highlightedIds.remove(ti.id);
                else highlightedIds.add(ti.id);
                clearWidgets(); buildWidgets();
            }).bounds(left + 215, rowY, 20, 18).build());

            // 删除按钮 (仅可管理)
            if (ti.canManage) {
                addRenderableWidget(Button.builder(Component.literal("§cX"), btn -> {
                    highlightedIds.remove(ti.id); // 删除时取消高亮
                    TerritoryScreen.sendPacket(new TerritoryScreen.TerritoryDataPacket(
                            TerritoryScreen.TerritoryDataPacket.Action.DELETE,
                            String.valueOf(ti.id)));
                    setStatus("已删除 " + ti.name);
                    sendListRequest();
                }).bounds(left + 240, rowY, 20, 18).build());
            }
            rowY += 20;
        }
        if (territoryList.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("§7暂无领地  □=显示边界"), btn -> {})
                    .bounds(left + 10, rowY, 280, 18).build());
        }
    }

    @Override
    public void tick() {
        if (TerritoryScreen.TerritoryClientData.ready) {
            territoryList = TerritoryScreen.TerritoryClientData.list != null
                    ? TerritoryScreen.TerritoryClientData.list : new ArrayList<>();
            TerritoryScreen.TerritoryClientData.ready = false;
            clearWidgets(); buildWidgets();
        }
        if (statusTimer > 0) { statusTimer--; if (statusTimer == 0) statusText = ""; }
        super.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(left, 20, left + PANEL_W, height - 40, 0xC0101010);
        g.renderOutline(left, 20, PANEL_W, height - 60, 0xFF444444);
        g.drawCenteredString(font, Component.literal("-- 领地管理 --"), width / 2, 18, 0xFF55AAFF);
        g.drawCenteredString(font, Component.literal("□=显示边界(关闭面板后保留)  ■=已开启"),
                width / 2, 24, 0xFF888888);
        if (statusTimer > 0) g.drawCenteredString(font, Component.literal(statusText),
                width / 2, height - 25, 0xFF55FF55);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override public boolean isPauseScreen() { return false; }

    private void sendListRequest() {
        TerritoryScreen.sendPacket(new TerritoryScreen.TerritoryDataPacket(
                TerritoryScreen.TerritoryDataPacket.Action.LIST, ""));
    }

    private void setStatus(String text) { statusText = text; statusTimer = 60; }
}
