package com.jiushi.friends.client;

import com.jiushi.friends.FriendsMod;
import com.jiushi.friends.server.FriendManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.function.Supplier;

/**
 * 好友系统主界面 (客户端)
 * <p>
 * 界面布局:
 * <ul>
 *   <li>顶部: 输入框 + 发送请求按钮 + 返回面板按钮</li>
 *   <li>中段: 待处理请求列表 (同意/拒绝按钮)</li>
 *   <li>底部: 好友列表 (在线●/离线○ + 删除按钮 + 私聊按钮)</li>
 * </ul>
 * <p>
 * 包含两个内部网络包类: FriendPacket (操作) 和 FriendListPacket (列表同步)
 */
@OnlyIn(Dist.CLIENT)
public class FriendScreen extends Screen {

    private static final int PANEL_W = 400;
    private int left;
    private String statusText = "";
    private int statusTimer;
    private List<FriendEntry> friendList = new ArrayList<>();
    private List<String> pendingList = new ArrayList<>();
    private EditBox addInput;

    /** 发送操作包到服务端的快捷方法 */
    public static void sendPacket(FriendPacket pkt) {
        if (Minecraft.getInstance().getConnection() != null)
            FriendsMod.CHANNEL.sendToServer(pkt);
    }

    /** 好友条目: 名字 + 在线状态 */
    public static class FriendEntry {
        public String name;
        public boolean online;
    }

    public FriendScreen() { super(Component.literal("好友")); }

    @Override
    protected void init() {
        left = width / 2 - PANEL_W / 2;
        sendPacket(new FriendPacket(FriendPacket.Action.LIST, ""));
        buildWidgets();
    }

    /** 构建界面控件 */
    private void buildWidgets() {
        int rowY = 38;

        // === 添加好友区域 ===
        addInput = new EditBox(font, left + 10, rowY, 180, 18, Component.literal("玩家名"));
        addInput.setMaxLength(30); addRenderableWidget(addInput);
        addRenderableWidget(Button.builder(Component.literal("§a+发送请求"), btn -> {
            String n = addInput.getValue().trim();
            if (!n.isEmpty()) {
                sendPacket(new FriendPacket(FriendPacket.Action.REQUEST, n));
                addInput.setValue("");
            }
        }).bounds(left + 195, rowY - 1, 65, 20).build());
        // 返回主面板按钮
        addRenderableWidget(Button.builder(Component.literal("§c返回"), btn -> {
            com.jiushi.adminpanel.client.MainScreen.currentTab =
                    com.jiushi.adminpanel.client.MainScreen.Tab.EXTENSIONS;
            Minecraft.getInstance().setScreen(new com.jiushi.adminpanel.client.MainScreen());
        }).bounds(left + PANEL_W - 65, rowY - 1, 50, 20).build());
        rowY += 28;

        // === 待处理请求区域 ===
        if (!pendingList.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("§6--- 待处理请求 ---"), btn -> {})
                    .bounds(left + 10, rowY, PANEL_W - 20, 16).build());
            rowY += 18;
            for (String from : pendingList) {
                if (rowY > height - 50) break;
                addRenderableWidget(Button.builder(
                        Component.literal("§e" + from + " 请求添加你为好友"), btn -> {})
                        .bounds(left + 10, rowY, 180, 18).build());
                addRenderableWidget(Button.builder(Component.literal("§a同意"), btn -> {
                    sendPacket(new FriendPacket(FriendPacket.Action.ACCEPT, from));
                    setStatus("已同意 " + from);
                }).bounds(left + 195, rowY, 38, 18).build());
                addRenderableWidget(Button.builder(Component.literal("§c拒绝"), btn -> {
                    sendPacket(new FriendPacket(FriendPacket.Action.DENY, from));
                    setStatus("已拒绝 " + from);
                }).bounds(left + 238, rowY, 38, 18).build());
                rowY += 22;
            }
            rowY += 4;
        }

        // === 好友列表区域 ===
        if (pendingList.isEmpty() && friendList.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("§7暂无好友"), btn -> {})
                    .bounds(left + 10, rowY, 200, 18).build());
        } else if (!friendList.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("§a--- 好友列表 ---"), btn -> {})
                    .bounds(left + 10, rowY, PANEL_W - 20, 16).build());
            rowY += 18;
        }

        for (var f : friendList) {
            if (rowY > height - 50) break;
            String label = f.online ? "§a● " : "§7○ "; // 在线/离线图标
            addRenderableWidget(Button.builder(Component.literal(label + f.name), btn -> {})
                    .bounds(left + 10, rowY, 200, 18).build());
            // 删除好友按钮
            addRenderableWidget(Button.builder(Component.literal("§cX"), btn -> {
                sendPacket(new FriendPacket(FriendPacket.Action.REMOVE, f.name));
                setStatus("已删除 " + f.name);
            }).bounds(left + 215, rowY, 20, 18).build());
            // 私聊按钮 (仅在线时显示)
            if (f.online) {
                addRenderableWidget(Button.builder(Component.literal("§e✉"), btn -> {
                    // 先关闭好友面板, 再打开聊天输入框并预填 /msg <好友名>
                    onClose();
                    Minecraft.getInstance().setScreen(new ChatScreen("/msg " + f.name + " "));
                }).bounds(left + 240, rowY, 20, 18).build());
            }
            rowY += 20;
        }
    }

    /** tick: 轮询客户端数据, 有新数据则重建界面 */
    @Override
    public void tick() {
        if (FriendClientData.ready) {
            friendList = FriendClientData.list != null
                    ? FriendClientData.list : new ArrayList<>();
            pendingList = FriendClientData.pendingFrom != null
                    ? FriendClientData.pendingFrom : new ArrayList<>();
            FriendClientData.ready = false;
            clearWidgets(); buildWidgets();
        }
        if (statusTimer > 0) { statusTimer--; if (statusTimer == 0) statusText = ""; }
        super.tick();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float p) {
        renderBackground(g);
        g.fill(left, 20, left + PANEL_W, height - 40, 0xC0101010);
        g.renderOutline(left, 20, PANEL_W, height - 60, 0xFF444444);
        g.drawCenteredString(font, Component.literal("-- 好友 --"), width / 2, 18, 0xFF55AAFF);
        if (statusTimer > 0) g.drawCenteredString(font, Component.literal(statusText),
                width / 2, height - 25, 0xFF55FF55);
        super.render(g, mx, my, p);
    }

    @Override public boolean isPauseScreen() { return false; }

    private void setStatus(String text) { statusText = text; statusTimer = 60; }

    /** 客户端数据中转 (与主面板 ClientData 同模式) */
    public static class FriendClientData {
        public static List<FriendEntry> list;
        public static List<String> pendingFrom;
        public static boolean ready;
    }

    /**
     * 好友操作网络包 (客户端→服务端)
     * 支持的操作: LIST(请求列表) / REQUEST(发请求) / ACCEPT(接受) / DENY(拒绝) / REMOVE(删除)
     */
    public static class FriendPacket {
        enum Action { LIST, REQUEST, ACCEPT, DENY, REMOVE }
        Action action;
        String data; // 目标玩家名

        public FriendPacket() {}
        public FriendPacket(Action a, String d) { action = a; data = d; }
        public FriendPacket(FriendlyByteBuf buf) {
            action = buf.readEnum(Action.class);
            data = buf.readUtf();
        }
        public static void encode(FriendPacket p, FriendlyByteBuf buf) {
            buf.writeEnum(p.action);
            buf.writeUtf(p.data != null ? p.data : "");
        }

        /** 服务端处理好友操作 */
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                String name = player.getGameProfile().getName();
                var server = player.getServer();
                switch (action) {
                    case LIST: {
                        // 请求刷新好友列表
                        sendList(player);
                        break;
                    }
                    case REQUEST: {
                        // 发送好友请求: 验证目标在线 → 调用 FriendManager
                        if (data == null || data.isEmpty()) break;
                        if (server.getPlayerList().getPlayerByName(data) == null) {
                            player.sendSystemMessage(Component.literal(
                                    "§c玩家 " + data + " 不在线或不存在"));
                            sendList(player);
                            break;
                        }
                        int result = FriendManager.sendRequest(name, data);
                        if (result == 1) {
                            player.sendSystemMessage(Component.literal("§c不能添加自己为好友"));
                        } else if (result == 2) {
                            player.sendSystemMessage(Component.literal("§c你们已经是好友了"));
                        } else if (result == 3) {
                            player.sendSystemMessage(Component.literal(
                                    "§c你已经发送过请求了，请等待对方处理"));
                        } else if (result == 4) {
                            player.sendSystemMessage(Component.literal(
                                    "§e对方已向你发送过好友请求，请在待处理请求中同意"));
                        } else {
                            player.sendSystemMessage(Component.literal(
                                    "§a已向 " + data + " 发送好友请求"));
                            ServerPlayer target = server.getPlayerList().getPlayerByName(data);
                            if (target != null) {
                                target.sendSystemMessage(Component.literal(
                                        "§e" + name + " 请求添加你为好友，请打开好友面板处理"));
                            }
                        }
                        sendList(player);
                        break;
                    }
                    case ACCEPT: {
                        // 接受好友请求
                        if (data == null || data.isEmpty()) break;
                        if (FriendManager.acceptRequest(name, data)) {
                            player.sendSystemMessage(Component.literal(
                                    "§a已同意 " + data + " 的好友请求"));
                            ServerPlayer from = server.getPlayerList().getPlayerByName(data);
                            if (from != null) {
                                from.sendSystemMessage(Component.literal(
                                        "§a" + name + " 同意了你的好友请求"));
                            }
                        } else {
                            player.sendSystemMessage(Component.literal(
                                    "§c未找到该好友请求"));
                        }
                        sendList(player);
                        break;
                    }
                    case DENY: {
                        // 拒绝好友请求
                        if (data == null || data.isEmpty()) break;
                        if (FriendManager.denyRequest(name, data)) {
                            player.sendSystemMessage(Component.literal(
                                    "§7已拒绝 " + data + " 的好友请求"));
                        }
                        sendList(player);
                        break;
                    }
                    case REMOVE: {
                        // 删除好友
                        if (data == null || data.isEmpty()) break;
                        FriendManager.removeFriend(name, data);
                        player.sendSystemMessage(Component.literal("§e已删除好友 " + data));
                        ServerPlayer target = server.getPlayerList().getPlayerByName(data);
                        if (target != null) {
                            target.sendSystemMessage(Component.literal(
                                    "§7" + name + " 已将你从好友列表中移除"));
                        }
                        sendList(player);
                        break;
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 构建并发送完整好友列表给指定玩家 */
    private static void sendList(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        var server = player.getServer();
        // 构建好友条目列表 (含在线状态)
        List<FriendEntry> list = new ArrayList<>();
        for (String fn : FriendManager.getFriends(name)) {
            FriendEntry fe = new FriendEntry();
            fe.name = FriendManager.getFriendName(fn); // 还原真实大小写用于显示
            fe.online = server.getPlayerList().getPlayerByName(fn) != null;
            list.add(fe);
        }
        List<String> pendingFrom = new ArrayList<>(FriendManager.getPendingRequests(name));
        FriendsMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new FriendListPacket(list, pendingFrom));
    }

    /**
     * 好友列表同步包 (服务端→客户端)
     * 包含两列表: 好友(含在线状态) + 待处理请求
     */
    public static class FriendListPacket {
        List<FriendEntry> list;
        List<String> pendingFrom;

        public FriendListPacket() {}
        public FriendListPacket(List<FriendEntry> l, List<String> p) { list = l; pendingFrom = p; }

        public FriendListPacket(FriendlyByteBuf buf) {
            // 反序列化好友列表
            int s = buf.readInt(); list = new ArrayList<>();
            for (int i = 0; i < s; i++) {
                FriendEntry fe = new FriendEntry();
                fe.name = buf.readUtf();
                fe.online = buf.readBoolean();
                list.add(fe);
            }
            // 反序列化待处理请求列表
            int ps = buf.readInt(); pendingFrom = new ArrayList<>();
            for (int i = 0; i < ps; i++) {
                pendingFrom.add(buf.readUtf());
            }
        }

        public static void encode(FriendListPacket p, FriendlyByteBuf buf) {
            buf.writeInt(p.list.size());
            for (var fe : p.list) { buf.writeUtf(fe.name); buf.writeBoolean(fe.online); }
            buf.writeInt(p.pendingFrom.size());
            for (var pr : p.pendingFrom) { buf.writeUtf(pr); }
        }

        /** 客户端接收: 写入 FriendClientData, 下一 tick 生效 */
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                FriendClientData.list = list;
                FriendClientData.pendingFrom = pendingFrom;
                FriendClientData.ready = true;
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
