package com.jiushi.adminpanel.client;

import com.jiushi.adminpanel.AdminMod;
import com.jiushi.adminpanel.api.AddonEntry;
import com.jiushi.adminpanel.api.AddonRegistry;
import com.jiushi.adminpanel.network.*;
import com.jiushi.adminpanel.server.ShopManager;
import com.jiushi.adminpanel.server.WarpManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;

/**
 * 管理面板主界面 (客户端)
 * <p>
 * 5个标签页:
 * <ul>
 *   <li>ADMIN     - 管理: 广播/定时公告/激活码/邀请码/玩家踢出封禁</li>
 *   <li>SHOP      - 商店: 金币余额/上架物品/购买/下架/转账/兑换券</li>
 *   <li>TELEPORT  - 传送: TPA请求/传送点设置与跳转</li>
 *   <li>OP_MANAGE - OP管理: 在线玩家邀请/管理员列表查看</li>
 *   <li>EXTENSIONS- 扩展: DLC注册的扩展入口按钮</li>
 * </ul>
 * <p>
 * 数据流: 通过 ClientData 的 volatile 字段接收服务端异步数据, tick() 轮询重建界面.
 * 关键静态字段 (currentTab, transferState等) 用于跨 Screen 实例保持状态.
 */
@OnlyIn(Dist.CLIENT)
public class MainScreen extends Screen {

    /** 标签页类型 */
    public enum Tab { ADMIN, SHOP, TELEPORT, OP_MANAGE, EXTENSIONS }

    /** 当前选中的标签页 (static: 关闭面板重开后保留) */
    public static Tab currentTab = Tab.ADMIN;
    /** 面板像素宽度 */
    private static final int PANEL_W = 400;
    /** 面板左侧X坐标 */
    private int left;
    /** 内容区顶部Y坐标 */
    private int contentTop;

    // ---- 商店/面板数据 (由 ShopPacket 填充) ----

    private static List<ShopManager.ShopListing> shopListings = new ArrayList<>();
    private static boolean shopVerified;        // 当前是否已验证为管理员
    private static int playerMoney;             // 当前玩家金币
    private static String playerRole = "";      // 当前玩家角色
    private static Map<String, WarpManager.WarpPoint> warps = new LinkedHashMap<>();
    private static List<AdminInfo> adminList = new ArrayList<>();
    private int lastPlayerCount = -1;           // 上一次渲染的在线玩家数 (用于检测变化)
    private String statusText = "";             // 底部状态文字
    private int statusTimer;                    // 状态文字显示倒计时 (tick)
    private int warpVisibility = 1;             // 传送点可见性选择 (0=私人 1=公开 2=官方)
    private String deleteConfirmName = null;    // 待二次确认删除的传送点名
    private boolean initialDataRequested = false; // 是否已请求过初始数据

    // ---- 封禁子表单 ----
    private String banTargetName = null;        // 当前封禁的玩家名
    private boolean banPermanent = false;       // 是否永久封禁
    private boolean showVerifyInput = false;    // 是否显示激活码输入框

    // ---- 邀请码状态 (static: 跨开关面板保持) ----
    private static String inviteCodeText = "";  // 生成的邀请码文本
    private static String inviteCodeTarget = "";// 邀请码目标玩家
    private static int inviteCodeTimer = 0;     // 邀请码显示倒计时 (tick)

    // ---- 转账子流程 (static: 跨重建) ----
    private static int transferState = 0;       // 0=隐藏 1=选玩家 2=输入金额
    private static String transferTarget = null;// 转账目标玩家名

    /** 管理员信息摘要 (用于OP管理页显示) */
    public static class AdminInfo {
        public String name;
        public String role;
    }

    public MainScreen() {
        super(Component.literal("管理面板"));
    }

    /**
     * GUI初始化: 计算面板位置 → 请求初始数据 → 按当前Tab构建界面
     */
    @Override
    protected void init() {
        left = width / 2 - PANEL_W / 2;
        contentTop = 52;
        // 仅首次初始化时请求服务端数据
        if (!initialDataRequested && minecraft != null && minecraft.player != null) {
            initialDataRequested = true;
            AdminMod.CHANNEL.sendToServer(new ShopPacket(ShopPacket.Action.LIST, 0, 0, null));
            AdminMod.CHANNEL.sendToServer(new WarpPacket(WarpPacket.Action.LIST, null));
        }
        switch (currentTab) {
            case ADMIN -> buildAdminWidgets();
            case SHOP -> buildShopWidgets();
            case TELEPORT -> buildTeleportWidgets();
            case OP_MANAGE -> buildOpManageWidgets();
            case EXTENSIONS -> buildExtensionsWidgets();
        }
    }

    /** 构建5个标签页的切换按钮栏 */
    private void addTabBar() {
        int tabW = 50;
        Tab[] tabs = Tab.values();
        int startX = width / 2 - (tabs.length * (tabW + 3)) / 2;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int x = startX + i * (tabW + 3);
            String label;
            switch (t) {
                case ADMIN: label = "管理"; break;
                case SHOP: label = "商店"; break;
                case TELEPORT: label = "传送"; break;
                case OP_MANAGE: label = "OP"; break;
                default: label = "扩展"; break;
            }
            boolean active = t == currentTab;
            addRenderableWidget(Button.builder(
                    Component.literal((active ? "[" : "") + label + (active ? "]" : "")), btn -> {
                        if (t != currentTab) {
                            currentTab = t;
                            banTargetName = null; // 切换时清除封禁子界面
                            rebuildWidgets();
                        }
                    }).bounds(x, 30, tabW, 20).build());
        }
    }

    // ---- 权限判断辅助方法 ----

    /** 当前玩家是否为已验证的管理员 */
    private boolean isAdmin() { return shopVerified; }
    /** 当前玩家是否为服主/开发者 */
    private boolean isOwner() { return "owner".equals(playerRole) || "developer".equals(playerRole); }
    /** 指定玩家是否为受保护角色 (服主/开发者 - 不可踢/不可封) */
    private boolean isProtectedRole(String playerName) {
        for (AdminInfo ai : adminList) {
            if (ai.name.equalsIgnoreCase(playerName)) {
                return "owner".equals(ai.role) || "developer".equals(ai.role);
            }
        }
        return false;
    }

    // ---- 标签页1: 管理 ----

    private void buildAdminWidgets() {
        addTabBar();
        boolean admin = isAdmin();
        int rowY = contentTop + 4;
        if (admin) {
            // === 广播输入 (全体公告) ===
            EditBox broadcast = new EditBox(font, left + 10, rowY, 260, 18, Component.literal("公告内容"));
            broadcast.setMaxLength(200); addRenderableWidget(broadcast);
            addRenderableWidget(Button.builder(Component.literal("发送"), btn -> {
                if (!broadcast.getValue().isEmpty()) {
                    AdminMod.CHANNEL.sendToServer(new AdminPacket(
                            AdminPacket.Action.BROADCAST, "", broadcast.getValue(), 0));
                    broadcast.setValue(""); setStatus("公告已发送");
                }
            }).bounds(left + 275, rowY - 1, 45, 20).build());
            rowY += 30;

            // === 定时公告 (消息 + 秒数 + 启动/停止) ===
            EditBox timed = new EditBox(font, left + 10, rowY, 180, 18, Component.literal("定时公告"));
            timed.setMaxLength(200); addRenderableWidget(timed);
            EditBox interval = new EditBox(font, left + 195, rowY, 40, 18, Component.literal("秒"));
            interval.setMaxLength(5); interval.setValue("60"); addRenderableWidget(interval);
            addRenderableWidget(Button.builder(Component.literal("启动"), btn -> {
                String msg = timed.getValue();
                int sec; try { sec = Integer.parseInt(interval.getValue()); } catch (Exception e) { sec = 60; }
                if (!msg.isEmpty()) {
                    AdminMod.CHANNEL.sendToServer(new AdminPacket(
                            AdminPacket.Action.TIMED_MSG, "", msg, sec));
                    setStatus("定时已启动");
                }
            }).bounds(left + 240, rowY - 1, 35, 20).build());
            addRenderableWidget(Button.builder(Component.literal("停"), btn -> {
                AdminMod.CHANNEL.sendToServer(new AdminPacket(
                        AdminPacket.Action.TIMED_MSG, "", "", 0));
                timed.setValue(""); interval.setValue("60"); setStatus("已停止");
            }).bounds(left + 278, rowY - 1, 30, 20).build());
            rowY += 26;

            // === 激活码输入区域 (非服主的管理员可为自己验证) ===
            if (!isOwner()) {
                addRenderableWidget(Button.builder(Component.literal(
                        showVerifyInput ? "§e▼ 激活码" : "激活码"), btn -> {
                    showVerifyInput = !showVerifyInput;
                    inviteCodeText = ""; inviteCodeTimer = 0;
                    rebuildWidgets();
                }).bounds(left + 10, rowY, 65, 20).build());
                if (showVerifyInput) {
                    EditBox verify = new EditBox(font, left + 80, rowY, 160, 18, Component.literal("输入激活码"));
                    verify.setMaxLength(20); addRenderableWidget(verify);
                    addRenderableWidget(Button.builder(Component.literal("§a确定"), btn -> {
                        AdminMod.CHANNEL.sendToServer(new ShopPacket(
                                ShopPacket.Action.VERIFY, 0, 0, verify.getValue()));
                        verify.setValue(""); showVerifyInput = false; rebuildWidgets();
                    }).bounds(left + 245, rowY - 1, 45, 20).build());
                }
                rowY += 22;
            }

            // === 邀请码生成 (仅服主可见) ===
            if (isOwner()) {
                // 手动输入玩家名 + 生成按钮
                EditBox inviteName = new EditBox(font, left + 10, rowY, 160, 18, Component.literal("输入玩家名或点右侧"));
                inviteName.setMaxLength(30); addRenderableWidget(inviteName);
                addRenderableWidget(Button.builder(Component.literal("生成"), btn -> {
                    String name = inviteName.getValue();
                    if (!name.isEmpty()) {
                        inviteCodeText = ""; inviteCodeTarget = name; inviteCodeTimer = 0;
                        AdminMod.CHANNEL.sendToServer(new AdminPacket(
                                AdminPacket.Action.ADD_OP_INVITE, name, "", 0));
                        setStatus("正在生成...");
                        inviteName.setValue("");
                    }
                }).bounds(left + 175, rowY - 1, 40, 20).build());
                rowY += 22;
                // 在线玩家快捷选择按钮
                if (minecraft != null && minecraft.getConnection() != null) {
                    var online = new ArrayList<>(minecraft.getConnection().getOnlinePlayers());
                    int cols = 3;
                    int btnW = (PANEL_W - 20 - (cols - 1) * 4) / cols;
                    int startY = rowY;
                    for (int i = 0; i < online.size(); i++) {
                        String p = online.get(i).getProfile().getName();
                        if (p.equals(minecraft.player.getName().getString())) continue;
                        int cx = left + 10 + (i % cols) * (btnW + 4);
                        int cy = rowY + (i / cols) * 22;
                        if (cy > height - 60) break;
                        addRenderableWidget(Button.builder(Component.literal(p), btn -> {
                            inviteCodeText = ""; inviteCodeTarget = p; inviteCodeTimer = 0;
                            AdminMod.CHANNEL.sendToServer(new AdminPacket(
                                    AdminPacket.Action.ADD_OP_INVITE, p, "", 0));
                            setStatus("正在生成...");
                        }).bounds(cx, cy, btnW, 18).build());
                    }
                    rowY += ((online.size() + cols - 1) / cols) * 22 + 6;
                }
            }

            // === 踢出/封禁子界面 或 在线玩家列表 ===
            if (banTargetName != null) {
                rowY += 2;
                buildBanSubForm(rowY);
            } else {
                buildPlayerList(rowY);
            }
        } else {
            // === 非管理员视图: 仅显示激活码输入 ===
            if (!showVerifyInput) {
                addRenderableWidget(Button.builder(Component.literal("§e  输入激活码  "), btn -> {
                    showVerifyInput = true; rebuildWidgets();
                }).bounds(left + 80, contentTop + 10, 200, 30).build());
            } else {
                EditBox verify = new EditBox(font, left + 10, contentTop + 12, 200, 18,
                        Component.literal("请输入激活码"));
                verify.setMaxLength(20); addRenderableWidget(verify);
                addRenderableWidget(Button.builder(Component.literal("§a验证"), btn -> {
                    AdminMod.CHANNEL.sendToServer(new ShopPacket(
                            ShopPacket.Action.VERIFY, 0, 0, verify.getValue()));
                    verify.setValue(""); showVerifyInput = false; rebuildWidgets();
                }).bounds(left + 220, contentTop + 10, 60, 20).build());
            }
        }
    }

    /** 封禁子表单: 原因 + 年/月/时/分钟 + 永久开关 + 确认/取消 */
    private void buildBanSubForm(int baseY) {
        // 封禁原因输入
        EditBox banReason = new EditBox(font, left + 10, baseY, 300, 18, Component.literal("封禁原因"));
        banReason.setMaxLength(100); addRenderableWidget(banReason);
        baseY += 22;
        // 时长输入: 年/月/时/分钟 (永久时全部变灰)
        EditBox banY  = new EditBox(font, left + 10, baseY, 35, 18, Component.literal("年"));
        banY.setMaxLength(3); banY.setValue("0"); addRenderableWidget(banY);
        EditBox banM  = new EditBox(font, left + 50, baseY, 35, 18, Component.literal("月"));
        banM.setMaxLength(3); banM.setValue("0"); addRenderableWidget(banM);
        EditBox banH  = new EditBox(font, left + 90, baseY, 35, 18, Component.literal("时"));
        banH.setMaxLength(4); banH.setValue("0"); addRenderableWidget(banH);
        EditBox banMin = new EditBox(font, left + 130, baseY, 40, 18, Component.literal("分"));
        banMin.setMaxLength(6); banMin.setValue("0"); addRenderableWidget(banMin);
        if (banPermanent) {
            banY.setEditable(false); banY.setTextColor(0x888888);
            banM.setEditable(false); banM.setTextColor(0x888888);
            banH.setEditable(false); banH.setTextColor(0x888888);
            banMin.setEditable(false); banMin.setTextColor(0x888888);
        }
        baseY += 22;
        // 永久开关 + 确认 + 取消
        addRenderableWidget(Button.builder(Component.literal(
                banPermanent ? "✓ 永久" : "✗ 非永久"), btn -> {
            banPermanent = !banPermanent; rebuildWidgets();
        }).bounds(left + 10, baseY, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("确认封禁"), btn -> {
            // 解析时长: 年→月→时→分钟汇总
            int y; try { y = Integer.parseInt(banY.getValue()); } catch (Exception e) { y = 0; }
            int m; try { m = Integer.parseInt(banM.getValue()); } catch (Exception e) { m = 0; }
            int h; try { h = Integer.parseInt(banH.getValue()); } catch (Exception e) { h = 0; }
            int min; try { min = Integer.parseInt(banMin.getValue()); } catch (Exception e) { min = 0; }
            long totalMin = banPermanent ? 0
                    : ((long) y * 525600L + (long) m * 43200L + (long) h * 60L + (long) min);
            AdminMod.CHANNEL.sendToServer(new AdminPacket(
                    AdminPacket.Action.BAN, banTargetName, banReason.getValue(), totalMin));
            setStatus("已封禁 " + banTargetName
                    + (banPermanent ? " (永久)" : " (" + totalMin + "分钟)"));
            banTargetName = null; banPermanent = false; rebuildWidgets();
        }).bounds(left + 85, baseY, 65, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), btn -> {
            banTargetName = null; banPermanent = false; rebuildWidgets();
        }).bounds(left + 155, baseY, 40, 20).build());
    }

    /** 在线玩家列表 (含踢出/封禁按钮) */
    private void buildPlayerList(int baseY) {
        if (minecraft != null && minecraft.getConnection() != null) {
            boolean admin = isAdmin();
            var players = new ArrayList<>(minecraft.getConnection().getOnlinePlayers());
            lastPlayerCount = players.size();
            int y = baseY + 18;
            for (int i = 0; i < players.size(); i++) {
                final String pName = players.get(i).getProfile().getName();
                int py = y + i * 18;
                if (py > height - 50) break;
                if (admin && !isProtectedRole(pName)) {
                    addRenderableWidget(Button.builder(Component.literal("踢出"), btn -> {
                        AdminMod.CHANNEL.sendToServer(new AdminPacket(
                                AdminPacket.Action.KICK, pName, "", 0));
                        setStatus("已踢出 " + pName);
                    }).bounds(left + 230, py, 35, 16).build());
                    addRenderableWidget(Button.builder(Component.literal("封禁"), btn -> {
                        banTargetName = pName; banPermanent = false; rebuildWidgets();
                    }).bounds(left + 270, py, 35, 16).build());
                }
            }
        }
    }

    // ---- 标签页4: OP管理 ----

    private void buildOpManageWidgets() {
        addTabBar();
    }

    // ---- 标签页5: 扩展 (DLC注册入口) ----

    private void buildExtensionsWidgets() {
        addTabBar();
        List<AddonEntry> addons = AddonRegistry.getEntries();
        int y = contentTop + 12;
        if (addons.isEmpty()) {
            return;
        }
        for (int i = 0; i < addons.size(); i++) {
            AddonEntry entry = addons.get(i);
            int py = y + i * 28;
            if (py > height - 50) break;
            // DLC入口按钮: 点击调用 openAction (打开子界面)
            addRenderableWidget(Button.builder(Component.literal("§6▶ " + entry.name), btn -> {
                if (entry.openAction != null) entry.openAction.run();
            }).bounds(left + 20, py, 200, 22).build());
        }
    }

    // ---- 标签页2: 商店 ----

    private void buildShopWidgets() {
        addTabBar();
        if (transferState == 1) { buildTransferSelectPlayer(); return; }
        if (transferState == 2) { buildTransferEnterAmount(); return; }
        // 上架金额输入
        EditBox price = new EditBox(font, left + 60, contentTop + 8, 100, 18, Component.literal("金额"));
        price.setMaxLength(10); addRenderableWidget(price);
        // 上架按钮 (手持物品)
        addRenderableWidget(Button.builder(Component.literal("上架"), btn -> {
            try {
                int p = Integer.parseInt(price.getValue());
                AdminMod.CHANNEL.sendToServer(new ShopPacket(ShopPacket.Action.ADD, 0, p, null));
                price.setValue(""); setStatus("已上架，刷新查看");
            } catch (Exception e) { setStatus("价格无效"); }
        }).bounds(left + 165, contentTop + 7, 35, 20).build());
        // 刷新按钮
        addRenderableWidget(Button.builder(Component.literal("刷新"), btn -> requestShopData())
                .bounds(left + 255, contentTop + 7, 35, 20).build());
        // 转账按钮 (仅管理员)
        if (isAdmin()) {
            addRenderableWidget(Button.builder(Component.literal("转账"), btn -> {
                transferState = 1; minecraft.setScreen(new MainScreen());
            }).bounds(left + 295, contentTop + 7, 45, 20).build());
        }
        // 兑换券生成 (全玩家可用: 消耗自身金币造券, 定位为现金/支票)
        addRenderableWidget(Button.builder(Component.literal("兑换券"), btn -> {
            try {
                int p = Integer.parseInt(price.getValue());
                AdminMod.CHANNEL.sendToServer(new AdminPacket(
                        AdminPacket.Action.VOUCHER, "", String.valueOf(p), 0));
                setStatus("兑换券已生成");
            } catch (Exception e) { setStatus("金额无效"); }
        }).bounds(left + 205, contentTop + 29, 45, 20).build());
        // 管理员金币加钱快捷按钮
        if (isAdmin()) {
            addRenderableWidget(Button.builder(Component.literal("+10"),
                    btn -> AdminMod.CHANNEL.sendToServer(
                            new AdminPacket(AdminPacket.Action.MONEY, "", "10", 0)))
                    .bounds(left + 255, contentTop + 29, 30, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+100"),
                    btn -> AdminMod.CHANNEL.sendToServer(
                            new AdminPacket(AdminPacket.Action.MONEY, "", "100", 0)))
                    .bounds(left + 290, contentTop + 29, 35, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+1K"),
                    btn -> AdminMod.CHANNEL.sendToServer(
                            new AdminPacket(AdminPacket.Action.MONEY, "", "1000", 0)))
                    .bounds(left + 255, contentTop + 51, 30, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+10K"),
                    btn -> AdminMod.CHANNEL.sendToServer(
                            new AdminPacket(AdminPacket.Action.MONEY, "", "10000", 0)))
                    .bounds(left + 290, contentTop + 51, 35, 20).build());
        }
        // 商品列表渲染
        int y = isAdmin() ? contentTop + 73 : contentTop + 51;
        for (var l : shopListings) {
            if (y > height - 50) break;
            // 购买按钮
            addRenderableWidget(Button.builder(Component.literal("购买"), btn -> {
                AdminMod.CHANNEL.sendToServer(new ShopPacket(ShopPacket.Action.BUY, l.id, 0, null));
                setStatus("购买请求已发送");
            }).bounds(left + 280, y, 35, 18).build());
            // 下架按钮 (卖家本人或管理员)
            boolean canDelist = isAdmin()
                    || (l.sellerName != null && minecraft != null && minecraft.player != null
                    && l.sellerName.equalsIgnoreCase(minecraft.player.getName().getString()));
            if (canDelist) {
                addRenderableWidget(Button.builder(Component.literal("下架"), btn -> {
                    AdminMod.CHANNEL.sendToServer(new ShopPacket(
                            ShopPacket.Action.REMOVE, l.id, 0, null));
                    setStatus("已下架");
                }).bounds(left + 320, y, 35, 18).build());
            }
            y += 22;
        }
    }

    /** 转账子流程1: 选择目标玩家 */
    private void buildTransferSelectPlayer() {
        addRenderableWidget(Button.builder(Component.literal("[选择转账对象]"), btn -> {})
                .bounds(left + 8, contentTop + 8, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), btn -> {
            transferState = 0; transferTarget = null; minecraft.setScreen(new MainScreen());
        }).bounds(left + 220, contentTop + 8, 40, 20).build());
        if (minecraft != null && minecraft.getConnection() != null) {
            int ty = contentTop + 34;
            for (var pi : minecraft.getConnection().getOnlinePlayers()) {
                if (ty > height - 50) break;
                String pName = pi.getProfile().getName();
                if (pName.equals(minecraft.player.getName().getString())) continue; // 不显示自己
                final String target = pName;
                addRenderableWidget(Button.builder(Component.literal(target), btn -> {
                    transferTarget = target; transferState = 2; minecraft.setScreen(new MainScreen());
                }).bounds(left + 15, ty, 200, 18).build());
                ty += 22;
            }
        }
    }

    /** 转账子流程2: 输入金额 (预设100~10000 + 自定义) */
    private void buildTransferEnterAmount() {
        addRenderableWidget(Button.builder(Component.literal("转给: " + transferTarget), btn -> {})
                .bounds(left + 8, contentTop + 8, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), btn -> {
            transferState = 1; minecraft.setScreen(new MainScreen());
        }).bounds(left + 220, contentTop + 8, 40, 20).build());
        // 预设金额按钮
        int[] amounts = {100, 500, 1000, 5000, 10000};
        for (int i = 0; i < amounts.length; i++) {
            int row = contentTop + 34 + i * 24;
            final int amt = amounts[i];
            addRenderableWidget(Button.builder(Component.literal("§6" + amt + " 币"), btn -> {
                AdminMod.CHANNEL.sendToServer(new AdminPacket(
                        AdminPacket.Action.MONEY, transferTarget, String.valueOf(amt), 0));
                setStatus("已向 " + transferTarget + " 转账 " + amt + " 币");
                transferState = 0; transferTarget = null; minecraft.setScreen(new MainScreen());
            }).bounds(left + 15, row, 100, 20).build());
        }
        // 自定义金额输入 + 转账按钮
        EditBox customAmt = new EditBox(font, left + 15, contentTop + 34 + 5 * 24 + 4, 100, 18,
                Component.literal("自定义金额"));
        customAmt.setMaxLength(10); addRenderableWidget(customAmt);
        final EditBox ca = customAmt;
        addRenderableWidget(Button.builder(Component.literal("转账"), btn -> {
            try {
                int a = Integer.parseInt(ca.getValue());
                if (a > 0 && transferTarget != null) {
                    AdminMod.CHANNEL.sendToServer(new AdminPacket(
                            AdminPacket.Action.MONEY, transferTarget, String.valueOf(a), 0));
                    setStatus("已向 " + transferTarget + " 转账 " + a + " 币");
                    transferState = 0; transferTarget = null;
                    minecraft.setScreen(new MainScreen());
                }
            } catch (Exception ignored) {}
        }).bounds(left + 120, contentTop + 34 + 5 * 24 + 2, 50, 20).build());
    }

    // ---- 标签页3: 传送 ----

    private void buildTeleportWidgets() {
        addTabBar();
        boolean admin = isAdmin();
        // 传送点名称 + 设置按钮
        EditBox name = new EditBox(font, left + 80, contentTop + 8, 120, 18, Component.literal("名称"));
        name.setMaxLength(30); addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("设置"), btn -> {
            if (!name.getValue().isEmpty()) {
                String n = name.getValue();
                if (warps.containsKey(n)) {
                    WarpManager.WarpPoint existing = warps.get(n);
                    // 覆盖权限: 管理员 或 传送点owner本人 (与服务端 WarpPacket SET 校验一致)
                    boolean canOverwrite = admin
                            || (minecraft != null && minecraft.player != null
                            && existing.owner != null
                            && existing.owner.equalsIgnoreCase(minecraft.player.getName().getString()));
                    if (canOverwrite) {
                        AdminMod.CHANNEL.sendToServer(new WarpPacket(
                                WarpPacket.Action.SET, n, warpVisibility));
                        name.setValue(""); setStatus("传送点已覆盖");
                    } else {
                        setStatus("§c该名称已存在，请换一个");
                    }
                } else {
                    AdminMod.CHANNEL.sendToServer(new WarpPacket(
                            WarpPacket.Action.SET, n, warpVisibility));
                    name.setValue(""); setStatus("传送点已设置");
                }
            }
        }).bounds(left + 205, contentTop + 7, 35, 20).build());
        // 可见性切换按钮
        addRenderableWidget(Button.builder(Component.literal(vLabel(warpVisibility)), btn -> {
            if (admin) warpVisibility = (warpVisibility + 1) % 3; // 三档循环
            else warpVisibility = warpVisibility == 0 ? 1 : 0;    // 非管理员仅 私人/公开
            rebuildWidgets();
        }).bounds(left + 245, contentTop + 7, 40, 20).build());
        // 刷新按钮
        addRenderableWidget(Button.builder(Component.literal("刷新"), btn -> requestWarpData())
                .bounds(left + 290, contentTop + 7, 35, 20).build());

        // TPA 玩家列表
        int tpaY = contentTop + 42;
        if (minecraft != null && minecraft.getConnection() != null) {
            var players = new ArrayList<>(minecraft.getConnection().getOnlinePlayers());
            for (int i = 0; i < players.size(); i++) {
                String pName = players.get(i).getProfile().getName();
                if (pName.equals(minecraft.player.getName().getString())) continue;
                int py = tpaY + i * 20;
                if (py > height - 80) break;
                addRenderableWidget(Button.builder(Component.literal("TPA"), btn -> {
                    AdminMod.CHANNEL.sendToServer(new TpaPacket(TpaPacket.Action.REQUEST, pName));
                    setStatus("已请求 " + pName);
                }).bounds(left + 260, py, 40, 16).build());
            }
            tpaY += Math.max(0, players.size()) * 20 + 10;
        }

        // 传送点列表 (含传送/删除按钮, 删除需二次确认)
        for (var e : warps.entrySet()) {
            if (tpaY > height - 60) break;
            // 传送按钮
            addRenderableWidget(Button.builder(Component.literal("传送"), btn -> {
                AdminMod.CHANNEL.sendToServer(new WarpPacket(WarpPacket.Action.GO, e.getKey()));
                setStatus("已传送到 " + e.getKey());
            }).bounds(left + 220, tpaY, 35, 16).build());
            // 删除按钮 (二次确认)
            String wk = e.getKey();
            boolean c = wk.equals(deleteConfirmName);
            addRenderableWidget(Button.builder(Component.literal(c ? "确认删除?" : "删除"), btn -> {
                if (c) {
                    AdminMod.CHANNEL.sendToServer(new WarpPacket(WarpPacket.Action.DEL, wk));
                    deleteConfirmName = null; setStatus("已删除 " + wk);
                } else {
                    deleteConfirmName = wk; setStatus("再次点击确认删除 " + wk);
                }
            }).bounds(left + 260, tpaY, c ? 50 : 30, 16).build());
            tpaY += 22;
        }
    }

    // ---- 渲染 ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        // 面板背景
        g.fill(left, 20, left + PANEL_W, height - 40, 0xC0101010);
        g.renderOutline(left, 20, PANEL_W, height - 60, 0xFF444444);
        // 标题行: 根据角色显示不同颜色
        String roleLabel; int roleColor;
        if ("developer".equals(playerRole))      { roleLabel = "[面板开发者]"; roleColor = 0xFFFF5555; }
        else if ("owner".equals(playerRole))     { roleLabel = "[服主]";       roleColor = 0xFFFFFF55; }
        else if (isAdmin())                      { roleLabel = "[OP]";         roleColor = 0xFF55AAFF; }
        else                                     { roleLabel = "玩家";         roleColor = 0xFF55FFFF; }
        g.drawCenteredString(font, Component.literal("-- 管理面板 --  " + roleLabel),
                width / 2, 24, roleColor);

        // OP管理页特殊渲染: 管理员列表含在线状态
        if (currentTab == Tab.OP_MANAGE) {
            g.drawString(font, "在线玩家:", left + 8, contentTop + 13, 0xFFAAAAAA);
            int z = contentTop + 45;
            g.drawString(font, "合作管理组:", left + 8, z, 0xFF888888); z += 16;
            java.util.Set<String> onlineNames = new HashSet<>();
            if (minecraft != null && minecraft.getConnection() != null) {
                for (var pi : minecraft.getConnection().getOnlinePlayers())
                    onlineNames.add(pi.getProfile().getName());
            }
            for (AdminInfo ai : adminList) {
                if (z > height - 40) break;
                String prefix; int color;
                switch (ai.role) {
                    case "developer": prefix = "[面板开发者]"; color = 0xFFFF5555; break;
                    case "owner":     prefix = "[服主]";       color = 0xFFFFFF55; break;
                    default:          prefix = "[OP]";         color = 0xFF55AAFF; break;
                }
                boolean online = onlineNames.contains(ai.name);
                String label = prefix + " " + ai.name + (online ? " §a●" : " §7●离线");
                g.drawString(font, label, left + 15, z, online ? color : 0xFF666666);
                z += 16;
            }
        } else {
            // 其他标签页的文字提示
            switch (currentTab) {
                case ADMIN -> {
                    if (isAdmin()) {
                        int labelX = left + 10;
                        int r1 = contentTop - 8;
                        int r2 = contentTop + 36;
                        int r3 = contentTop + 62;
                        int r4 = r3 + 24;
                        if (isOwner()) r4 = r3 + 22 + 22;
                        g.drawString(font, "§7▶ 公告广播", labelX, r1, 0xFFAAAAAA);
                        g.drawString(font, "§7▶ 定时公告 (秒)", labelX, r2, 0xFFAAAAAA);
                        if (isOwner()) {
                            g.drawString(font, "§7▶ 发放邀请码 (点击下方玩家或手动输入)",
                                    labelX, r4 - 22, 0xFFAAAAAA);
                        }
                        int rowY = r4 + 6;
                        if (banTargetName != null) {
                            g.drawString(font, "§c封禁: " + banTargetName, labelX, rowY - 4, 0xFFFF5555);
                        } else {
                            g.drawString(font, "在线玩家:", labelX, rowY, 0xFFAAAAAA);
                            if (minecraft != null && minecraft.getConnection() != null) {
                                for (var pi : minecraft.getConnection().getOnlinePlayers()) {
                                    g.drawString(font, pi.getProfile().getName(),
                                            left + 20, rowY += 18, 0xFFFFFFFF);
                                }
                            }
                        }
                        // 邀请码弹出框 (服主生成后显示)
                        if (isOwner() && inviteCodeTimer > 0 && !inviteCodeText.isEmpty()) {
                            int codeY = height - 90;
                            g.fill(left + 8, codeY, left + PANEL_W - 8, codeY + 36, 0xCC000000);
                            g.renderOutline(left + 8, codeY, PANEL_W - 16, 36, 0xFFFFAA00);
                            g.drawCenteredString(font, Component.literal(inviteCodeText),
                                    width / 2, codeY + 8, 0xFF55FF55);
                            g.drawCenteredString(font, Component.literal(
                                    "请复制此码发送给 " + inviteCodeTarget),
                                    width / 2, codeY + 22, 0xFFAAAAAA);
                        }
                    } else {
                        g.drawString(font, "你还没有管理员权限", left + 10, contentTop - 8, 0xFFFFAA00);
                        if (!showVerifyInput) {
                            g.drawCenteredString(font, Component.literal("点击下方按钮输入服主提供的验证码"),
                                    width / 2, contentTop + 2, 0xFFAAAAAA);
                        }
                    }
                }
                case SHOP -> {
                    if (transferState > 0) {
                        if (transferState == 1) {
                            g.drawString(font, "请选择转账目标玩家:", left + 8, contentTop + 13, 0xFFAAAAAA);
                        } else {
                            g.drawString(font, "输入转账金额:", left + 8, contentTop + 13, 0xFFAAAAAA);
                            g.drawString(font, "当前余额: " + playerMoney + " 币",
                                    left + 15, contentTop + 34 + 6 * 24 + 4, 0xFFFFAA00);
                        }
                    } else {
                        g.drawString(font, "余额: " + playerMoney + " 币",
                                left + 8, contentTop + 5, 0xFFFFAA00);
                        g.drawString(font, "金额/券额:", left + 8, contentTop + 25, 0xFFAAAAAA);
                        int y = isAdmin() ? contentTop + 75 : contentTop + 53;
                        for (var l : shopListings) {
                            if (y > height - 60) break;
                            g.drawString(font, clipText("#" + l.id + " " + l.itemName, 175)
                                    + " x" + l.quantity, left + 10, y + 4, 0xFFFFFFFF);
                            g.drawString(font, clipText(l.price + " 币", 60),
                                    left + 195, y + 4, 0xFFFFAA00);
                            y += 22;
                        }
                        if (shopListings.isEmpty())
                            g.drawCenteredString(font, Component.literal("暂无商品"),
                                    width / 2, y + 10, 0xFF888888);
                    }
                }
                case TELEPORT -> {
                    g.drawString(font, "传送点名:", left + 8, contentTop + 5, 0xFFAAAAAA);
                    int y = contentTop + 34;
                    g.drawString(font, "TPA:", left + 8, y, 0xFFAAAAAA);
                    if (minecraft != null && minecraft.getConnection() != null) {
                        for (var pi : minecraft.getConnection().getOnlinePlayers()) {
                            if (pi.getProfile().getName().equals(
                                    minecraft.player.getName().getString())) continue;
                            g.drawString(font, pi.getProfile().getName(),
                                    left + 20, y += 20, 0xFFFFFFFF);
                        }
                        y += 10;
                    }
                    g.drawString(font, "传送点:", left + 8, y, 0xFFAAAAAA); y += 14;
                    for (var e : warps.entrySet()) {
                        if (y > height - 50) break;
                        String label = e.getKey();
                        if (e.getValue().visibility == 2) label = "§6[官方]§r " + label;
                        else if (e.getValue().owner != null && !e.getValue().owner.isEmpty())
                            label = label + " §7[由: " + e.getValue().owner + "]§r";
                        g.drawString(font, clipText(label, 195), left + 15, y, 0xFFFFFFFF);
                        y += 22;
                    }
                    if (warps.isEmpty())
                        g.drawCenteredString(font, Component.literal("暂无"),
                                width / 2, y, 0xFF888888);
                }
                case EXTENSIONS -> {
                    List<AddonEntry> addons = AddonRegistry.getEntries();
                    if (addons.isEmpty()) {
                        g.drawCenteredString(font, Component.literal("暂无可用的扩展包"),
                                width / 2, contentTop + 40, 0xFF888888);
                    } else {
                        g.drawString(font, "已安装的扩展:", left + 10, contentTop + 2, 0xFFAAAAAA);
                    }
                }
            }
        }
        // 底部状态文字
        if (statusTimer > 0)
            g.drawCenteredString(font, Component.literal(statusText),
                    width / 2, height - 25, 0xFF55FF55);
        super.render(g, mouseX, mouseY, partial);
    }

    // ---- tick: 每帧轮询 ClientData, 有新数据则重建界面 ----

    @Override
    public void tick() {
        // 商店/面板数据就绪 → 刷新
        if (ClientData.shopDataReady) {
            boolean wasVerified = shopVerified; // 记录旧验证状态 (用于检测权限变化)
            shopListings = ClientData.pendingShopListings != null
                    ? ClientData.pendingShopListings : List.of();
            shopVerified = ClientData.pendingShopVerified != null
                    && ClientData.pendingShopVerified;
            playerMoney = ClientData.pendingMoney;
            playerRole = ClientData.pendingRole;
            adminList = ClientData.pendingAdminList != null
                    ? ClientData.pendingAdminList : List.of();
            String msg = ClientData.pendingStatusMsg;
            ClientData.shopDataReady = false;
            if (msg != null && !msg.isEmpty()) {
                if (msg.startsWith("§a邀请码:")) {
                    // 邀请码特殊处理: 显示3600 tick (3分钟)
                    inviteCodeText = msg;
                    inviteCodeTimer = 3600;
                } else {
                    setStatus(msg);
                }
            }
            boolean becameAdmin = !wasVerified && shopVerified;
            // 涉及权限变化 / 商店/OP页 → 重建
            if (currentTab == Tab.SHOP || currentTab == Tab.OP_MANAGE
                    || (currentTab == Tab.ADMIN && becameAdmin)) rebuildWidgets();
        }
        // 传送点数据就绪 → 刷新
        if (ClientData.warpDataReady) {
            warps = ClientData.pendingWarps != null
                    ? ClientData.pendingWarps : Map.of();
            ClientData.warpDataReady = false;
            if (currentTab == Tab.TELEPORT) rebuildWidgets();
        }
        // 管理员页: 在线玩家数量变化 → 重建列表
        if (minecraft != null && minecraft.getConnection() != null
                && currentTab == Tab.ADMIN && banTargetName == null && isAdmin()) {
            int count = minecraft.getConnection().getOnlinePlayers().size();
            if (count != lastPlayerCount) rebuildWidgets();
        }
        // 状态文字/邀请码倒计时
        if (statusTimer > 0) { statusTimer--; if (statusTimer == 0) statusText = ""; }
        if (inviteCodeTimer > 0) inviteCodeTimer--;
        super.tick();
    }

    // ---- 辅助方法 ----

    /** 向服务端请求商店数据 */
    private void requestShopData() {
        AdminMod.CHANNEL.sendToServer(new ShopPacket(ShopPacket.Action.LIST, 0, 0, null));
    }
    /** 向服务端请求传送点数据 */
    private void requestWarpData() {
        AdminMod.CHANNEL.sendToServer(new WarpPacket(WarpPacket.Action.LIST, null));
    }
    /** 设置底部状态文字 (显示60 tick约3秒) */
    private void setStatus(String text) { statusText = text; statusTimer = 60; }
    /** 可见性枚举 → 中文标签 */
    private String vLabel(int v) { return v == 0 ? "私人" : v == 1 ? "公开" : "官方"; }

    /** 文本截断: 按像素宽度截断并加".."后缀 (处理§颜色码) */
    private String clipText(String text, int maxPixels) {
        if (text == null) return "";
        if (font.width(text) <= maxPixels) return text;
        StringBuilder sb = new StringBuilder();
        boolean skipNext = false; // 跳过§颜色码的下一个字符
        for (char c : text.toCharArray()) {
            if (c == '§') { skipNext = true; sb.append(c); continue; }
            if (skipNext) { skipNext = false; sb.append(c); continue; }
            if (font.width(sb.toString() + c + "..") > maxPixels) break;
            sb.append(c);
        }
        return sb + "..";
    }

    /** J键按下时关闭面板 (与KeyBindings一致) */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyBindings.OPEN_ADMIN.matches(keyCode, scanCode)) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 面板不暂停游戏 */
    @Override
    public boolean isPauseScreen() { return false; }
}
