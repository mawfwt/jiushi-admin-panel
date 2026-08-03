package com.jiushi.friends;

import com.jiushi.adminpanel.api.AddonEntry;
import com.jiushi.adminpanel.api.AddonRegistry;
import com.jiushi.friends.client.FriendScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 好友系统 DLC 模组入口
 * <p>
 * 作为 jiushi_admin 的扩展包运行, 通过 {@link AddonRegistry} 注册到主面板 EXTENSIONS 标签页.
 * <p>
 * 网络通道注册2个包:
 * <ul>
 *   <li>FriendPacket - 好友操作 (请求/接受/拒绝/删除)</li>
 *   <li>FriendListPacket - 好友列表同步 (服务端→客户端)</li>
 * </ul>
 *
 * @author MA
 */
@Mod("jiushi_friends")
public class FriendsMod {

    /** 好友系统网络通道 */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("jiushi_friends", "main"),
            () -> "1", "1"::equals, "1"::equals
    );

    /** 构造方法: 注册网络包 + 注册到主面板扩展入口 */
    public FriendsMod() {
        // 注册好友操作包
        CHANNEL.registerMessage(0, FriendScreen.FriendPacket.class,
                FriendScreen.FriendPacket::encode,
                FriendScreen.FriendPacket::new,
                FriendScreen.FriendPacket::handle);
        // 注册好友列表同步包
        CHANNEL.registerMessage(1, FriendScreen.FriendListPacket.class,
                FriendScreen.FriendListPacket::encode,
                FriendScreen.FriendListPacket::new,
                FriendScreen.FriendListPacket::handle);
        // 注册到主面板: 点击"好友"按钮 → 打开 FriendScreen
        AddonRegistry.register(new AddonEntry("friends", "好友", () -> {
            net.minecraft.client.Minecraft.getInstance().setScreen(new FriendScreen());
        }));
    }
}
