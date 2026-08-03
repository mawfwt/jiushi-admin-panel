package com.jiushi.adminpanel.client;

import com.jiushi.adminpanel.server.ShopManager;
import com.jiushi.adminpanel.server.WarpManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;

/**
 * 客户端数据中转站
 * <p>
 * 服务端异步发送网络包到客户端时, 数据写入这里的 volatile 字段.
 * 客户端 Screen 的 tick() 方法轮询 ready 标志, 发现数据就绪后读取并重建界面.
 * <p>
 * 这种模式避免了在渲染线程直接修改GUI组件导致的问题.
 */
@OnlyIn(Dist.CLIENT)
public class ClientData {

    // -- 商店/面板数据 (ShopPacket) --

    /** 待处理的商品列表 */
    public static volatile List<ShopManager.ShopListing> pendingShopListings;
    /** 待处理的管理员验证状态 */
    public static volatile Boolean pendingShopVerified;
    /** 待处理的状态消息文本 */
    public static volatile String pendingStatusMsg;
    /** 待处理的金币余额 */
    public static volatile int pendingMoney;
    /** 待处理的玩家角色 */
    public static volatile String pendingRole;
    /** 待处理的管理员列表 */
    public static volatile List<MainScreen.AdminInfo> pendingAdminList;
    /** 商店数据就绪标志 */
    public static volatile boolean shopDataReady;

    // -- 传送点数据 (WarpPacket) --

    /** 待处理的传送点列表 */
    public static volatile Map<String, WarpManager.WarpPoint> pendingWarps;
    /** 传送点数据就绪标志 */
    public static volatile boolean warpDataReady;
}
