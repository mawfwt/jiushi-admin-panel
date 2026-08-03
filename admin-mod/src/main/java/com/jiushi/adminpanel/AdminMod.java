package com.jiushi.adminpanel;

import com.jiushi.adminpanel.network.*;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

/**
 * 九氏管理面板 - 主模组入口
 * <p>
 * 功能概览: 管理员分级管理 · 邀请码验证 · 公告广播 · 金币系统 · 玩家商店 ·
 * 传送点与TPA · 踢出/封禁 · 兑换券 · DLC扩展支持
 *
 * @author MA
 */
@Mod(AdminMod.MODID)
public class AdminMod {

    /** 模组ID, mods.toml 中定义 */
    public static final String MODID = "jiushi_admin";
    /** 日志记录器 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 网络协议版本, 客户端/服务端必须一致才能通信 */
    private static final String PROTOCOL_VERSION = "1";
    /**
     * 模组网络通道 - 所有数据包通过此通道在客户端与服务端之间传输.
     * 每个包注册一个唯一ID, 这里注册了4个包: 管理员操作 / 商店 / TPA传送 / 传送点
     */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    /** 构造方法: 注册事件总线 + 注册4个网络数据包 */
    public AdminMod() {
        // 向 Forge 事件总线注册此模组
        MinecraftForge.EVENT_BUS.register(this);

        // 注册网络包: 管理员操作 (广播/踢出/封禁/金币/邀请码等)
        CHANNEL.registerMessage(0, AdminPacket.class,
                AdminPacket::encode,
                AdminPacket::new,
                AdminPacket::handle);

        // 注册网络包: 商店操作 (购买/上架/下架/验证激活码)
        CHANNEL.registerMessage(1, ShopPacket.class,
                ShopPacket::encode,
                ShopPacket::new,
                ShopPacket::handle);

        // 注册网络包: TPA 传送请求/接受/拒绝
        CHANNEL.registerMessage(2, TpaPacket.class,
                TpaPacket::encode,
                TpaPacket::new,
                TpaPacket::handle);

        // 注册网络包: 传送点 设置/跳转/删除/列表
        CHANNEL.registerMessage(3, WarpPacket.class,
                WarpPacket::encode,
                WarpPacket::new,
                WarpPacket::handle);
    }
}
