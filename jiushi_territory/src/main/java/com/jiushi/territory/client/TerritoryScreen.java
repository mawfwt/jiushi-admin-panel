package com.jiushi.territory.client;

import com.jiushi.territory.ShopAddonMod;
import com.jiushi.territory.server.TerritoryManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.network.FriendlyByteBuf;
import java.util.*;
import java.util.function.Supplier;

/**
 * 领地创建/列表/删除界面 (客户端)
 * <p>
 * 功能:
 * <ul>
 *   <li>选区起点/终点显示</li>
 *   <li>XZ差 + 超限提示</li>
 *   <li>私人/官方领地创建</li>
 *   <li>领地列表 + 删除</li>
 * </ul>
 * <p>
 * 包含内部网络包: TerritoryDataPacket (操作) 和 TerritoryListPacket (列表同步)
 */
@OnlyIn(Dist.CLIENT)
public class TerritoryScreen extends Screen {

    private static final int PANEL_W = 360;
    private int left;
    private String statusText = "";
    private int statusTimer;
    private List<TerritoryInfo> territoryList = new ArrayList<>();
    private BlockPos selStart, selEnd; // 选区起终点

    public TerritoryScreen() {
        super(Component.literal("领地管理"));
    }

    @Override
    protected void init() {
        left = width / 2 - PANEL_W / 2;
        sendListRequest();
        // 加载选区数据 (如果有)
        if (TerritorySelectionTracker.ready) {
            selStart = TerritorySelectionTracker.startPos;
            selEnd = TerritorySelectionTracker.endPos;
            TerritorySelectionTracker.consume();
        }
        buildWidgets();
    }

    /** 构建界面控件 */
    private void buildWidgets() {
        int rowY = 32;
        if (selStart != null && selEnd != null) {
            // === 选区信息 + 创建表单 ===
            int s = TerritorySelectionTracker.sizeXZ();
            String hint = "选区: (" + selStart.getX() + "," + selStart.getZ() + ") - ("
                    + selEnd.getX() + "," + selEnd.getZ() + ")  XZ差=" + s;
            if (s > 128) hint += " §c超限!";
            addRenderableWidget(Button.builder(Component.literal(hint), btn -> {})
                    .bounds(left + 10, rowY, 300, 18).build());
            rowY += 22;

            // 领地名称 + 创建按钮
            EditBox nameBox = new EditBox(font, left + 10, rowY, 140, 18, Component.literal("领地名称"));
            nameBox.setMaxLength(20); addRenderableWidget(nameBox);
            // 私人领地按钮 (超限时变灰)
            addRenderableWidget(Button.builder(Component.literal(s > 128 ? "§7个人(超限)" : "个人"), btn -> {
                String n = nameBox.getValue();
                if (!n.isEmpty()) { doCreate(n, false); }
            }).bounds(left + 155, rowY, s > 128 ? 80 : 35, 20).build());
            // 官方领地按钮 (仅OP可见)
            if (hasOp()) {
                addRenderableWidget(Button.builder(Component.literal("§6官方"), btn -> {
                    String n = nameBox.getValue();
                    if (!n.isEmpty()) { doCreate(n, true); }
                }).bounds(left + (s > 128 ? 240 : 195), rowY, 35, 20).build());
            }
            // 取消选区
            addRenderableWidget(Button.builder(Component.literal("§c取消"), btn -> {
                selStart = null; selEnd = null; TerritorySelectionTracker.cancel();
                clearWidgets(); buildWidgets();
            }).bounds(left + 240, rowY, 40, 20).build());
            rowY += 24;
        } else {
            // 未选区: 显示开始选区按钮
            addRenderableWidget(Button.builder(Component.literal("§a开始选区"), btn -> {
                TerritorySelectionTracker.begin();
                if (minecraft != null) minecraft.setScreen(null); // 关闭GUI回到游戏选点
            }).bounds(left + 10, rowY, 70, 20).build());
            rowY += 24;
        }

        // === 领地列表 (含删除按钮) ===
        for (TerritoryInfo ti : territoryList) {
            if (rowY > height - 50) break;
            String label = (ti.official ? "§6[官] " : "§a") + ti.name + " §7-" + ti.owner;
            addRenderableWidget(Button.builder(Component.literal(label), btn -> {})
                    .bounds(left + 10, rowY, 190, 18).build());
            if (ti.canManage) {
                addRenderableWidget(Button.builder(Component.literal("§cX"), btn -> {
                    sendPacket(new TerritoryDataPacket(TerritoryDataPacket.Action.DELETE,
                            String.valueOf(ti.id)));
                    setStatus("已删除");
                }).bounds(left + 205, rowY, 20, 18).build());
            }
            rowY += 20;
        }
    }

    /** 执行创建: 打包坐标+名称+类型 → 发送服务端 */
    private void doCreate(String name, boolean official) {
        if (selStart == null || selEnd == null) return;
        sendPacket(new TerritoryDataPacket(TerritoryDataPacket.Action.CREATE,
                name + "|" + (official ? "1" : "0") + "|"
                        + selStart.getX() + "," + selStart.getZ() + "|"
                        + selEnd.getX() + "," + selEnd.getZ()));
        selStart = null; selEnd = null;
        TerritorySelectionTracker.cancel();
        clearWidgets(); buildWidgets();
    }

    @Override
    public void tick() {
        if (TerritoryClientData.ready) {
            territoryList = TerritoryClientData.list != null
                    ? TerritoryClientData.list : new ArrayList<>();
            TerritoryClientData.ready = false;
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
        if (statusTimer > 0) g.drawCenteredString(font, Component.literal(statusText),
                width / 2, height - 25, 0xFF55FF55);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override public boolean isPauseScreen() { return false; }

    private void sendListRequest() { sendPacket(new TerritoryDataPacket(TerritoryDataPacket.Action.LIST, "")); }
    private void setStatus(String text) { statusText = text; statusTimer = 60; }
    /** 判断当前玩家是否为 OP (客户端权限等级≥2) */
    private boolean hasOp() {
        return Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.hasPermissions(2);
    }

    /** 发送操作包到服务端 */
    public static void sendPacket(TerritoryDataPacket packet) {
        if (Minecraft.getInstance().getConnection() != null)
            ShopAddonMod.CHANNEL.sendToServer(packet);
    }

    /** 领地信息摘要 (用于UI显示) */
    public static class TerritoryInfo {
        public int id; public String name; public String owner;
        public boolean official; public boolean canManage;
        public int x1, z1, x2, z2; public String world;
    }

    /**
     * 领地操作网络包 (客户端→服务端)
     * 支持的操作: LIST(请求列表) / CREATE(创建) / DELETE(删除)
     */
    public static class TerritoryDataPacket {
        public enum Action { LIST, CREATE, DELETE }
        Action action;
        String data; // LIST="" / CREATE="名称|官方0/1|起X,起Z|终X,终Z" / DELETE="领地ID"

        public TerritoryDataPacket() {}
        public TerritoryDataPacket(Action action, String data) { this.action = action; this.data = data; }
        public TerritoryDataPacket(FriendlyByteBuf buf) {
            action = buf.readEnum(Action.class);
            data = buf.readUtf();
        }
        public static void encode(TerritoryDataPacket pkt, FriendlyByteBuf buf) {
            buf.writeEnum(pkt.action);
            buf.writeUtf(pkt.data != null ? pkt.data : "");
        }

        /** 服务端处理领地操作 */
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                net.minecraft.server.level.ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                switch (action) {
                    case LIST: {
                        // 返回领地列表
                        List<TerritoryInfo> list = new ArrayList<>();
                        for (TerritoryManager.Territory t : TerritoryManager.getAllTerritories()) {
                            TerritoryInfo ti = new TerritoryInfo();
                            ti.id = t.id; ti.name = t.name; ti.owner = t.owner;
                            ti.official = t.official;
                            ti.x1 = t.x1; ti.z1 = t.z1;
                            ti.x2 = t.x2; ti.z2 = t.z2;
                            ti.world = t.world;
                            String pn = player.getGameProfile().getName();
                            ti.canManage = t.owner.equals(pn)
                                    || com.jiushi.adminpanel.server.SetupManager.isOwner(pn);
                            list.add(ti);
                        }
                        ShopAddonMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                new TerritoryListPacket(list));
                        break;
                    }
                    case CREATE: {
                        // 解析创建参数并调用 TerritoryManager
                        if (data != null && !data.isEmpty()) {
                            String[] parts = data.split("\\|");
                            if (parts.length == 4) {
                                String name = parts[0];
                                boolean official = "1".equals(parts[1]);
                                String[] s1 = parts[2].split(",");
                                String[] s2 = parts[3].split(",");
                                if (s1.length < 2 || s2.length < 2) break;
                                BlockPos start = new BlockPos(
                                        Integer.parseInt(s1[0]), 0, Integer.parseInt(s1[1]));
                                BlockPos end = new BlockPos(
                                        Integer.parseInt(s2[0]), 0, Integer.parseInt(s2[1]));
                                String err = TerritoryManager.createTerritory(
                                        name, player, start, end, official);
                                player.sendSystemMessage(Component.literal(err != null ? err
                                        : ("§a领地 " + name + " 创建成功")));
                                // 创建后重新获取列表
                                List<TerritoryInfo> list = new ArrayList<>();
                                for (TerritoryManager.Territory t : TerritoryManager.getAllTerritories()) {
                                    TerritoryInfo ti = new TerritoryInfo();
                                    ti.id = t.id; ti.name = t.name; ti.owner = t.owner;
                                    ti.official = t.official;
                                    ti.x1 = t.x1; ti.z1 = t.z1;
                                    ti.x2 = t.x2; ti.z2 = t.z2;
                                    ti.world = t.world;
                                    String pn = player.getGameProfile().getName();
                                    ti.canManage = t.owner.equals(pn)
                                            || com.jiushi.adminpanel.server.SetupManager.isOwner(pn);
                                    list.add(ti);
                                }
                                ShopAddonMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                        new TerritoryListPacket(list));
                            }
                        }
                        break;
                    }
                    case DELETE: {
                        // 按ID删除领地
                        try {
                            int id = Integer.parseInt(data);
                            String err = TerritoryManager.deleteTerritory(id, player);
                            player.sendSystemMessage(Component.literal(err != null ? err : "§a领地已删除"));
                        } catch (NumberFormatException ignored) {}
                        break;
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 领地列表同步包 (服务端→客户端)
     */
    public static class TerritoryListPacket {
        List<TerritoryInfo> list;
        public TerritoryListPacket() {}
        public TerritoryListPacket(List<TerritoryInfo> list) { this.list = list; }
        public TerritoryListPacket(FriendlyByteBuf buf) {
            int s = buf.readInt(); list = new ArrayList<>();
            for (int i = 0; i < s; i++) {
                TerritoryInfo ti = new TerritoryInfo();
                ti.id = buf.readInt(); ti.name = buf.readUtf(); ti.owner = buf.readUtf();
                ti.official = buf.readBoolean(); ti.canManage = buf.readBoolean();
                ti.x1 = buf.readInt(); ti.z1 = buf.readInt();
                ti.x2 = buf.readInt(); ti.z2 = buf.readInt();
                ti.world = buf.readUtf();
                list.add(ti);
            }
        }
        public static void encode(TerritoryListPacket p, FriendlyByteBuf buf) {
            buf.writeInt(p.list.size());
            for (TerritoryInfo ti : p.list) {
                buf.writeInt(ti.id); buf.writeUtf(ti.name); buf.writeUtf(ti.owner);
                buf.writeBoolean(ti.official); buf.writeBoolean(ti.canManage);
                buf.writeInt(ti.x1); buf.writeInt(ti.z1);
                buf.writeInt(ti.x2); buf.writeInt(ti.z2);
                buf.writeUtf(ti.world != null ? ti.world : "");
            }
        }
        /** 客户端接收: 写入静态数据, 下一 tick 生效 */
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                TerritoryClientData.list = this.list;
                TerritoryClientData.ready = true;
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端数据中转 */
    public static class TerritoryClientData {
        public static volatile List<TerritoryInfo> list;
        public static volatile boolean ready;
    }
}
