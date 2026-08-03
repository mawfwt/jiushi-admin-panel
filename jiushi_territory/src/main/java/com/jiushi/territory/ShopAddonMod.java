package com.jiushi.territory;

import com.jiushi.adminpanel.api.AddonEntry;
import com.jiushi.adminpanel.api.AddonRegistry;
import com.jiushi.territory.client.TerritoryCreateScreen;
import com.jiushi.territory.client.TerritoryManageScreen;
import com.jiushi.territory.client.TerritoryScreen;
import com.jiushi.territory.client.TerritorySelectionTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 领地系统 DLC 模组入口
 * <p>
 * 作为 jiushi_admin 的扩展包运行, 通过 {@link AddonRegistry} 注册两个入口:
 * "领地管理" → TerritoryManageScreen (列表/高亮/删除)
 * "创建领地" → TerritoryCreateScreen (选区+命名+类型选择)
 * <p>
 * 网络通道注册2个包:
 * <ul>
 *   <li>TerritoryDataPacket - 领地操作 (列表/创建/删除)</li>
 *   <li>TerritoryListPacket - 领地列表同步 (服务端→客户端)</li>
 * </ul>
 *
 * @author MA
 */
@Mod("jiushi_territory")
public class ShopAddonMod {

    /** 领地系统网络通道 (协议版本2, 与admin面板版本1区分) */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("jiushi_addon", "territory"),
            () -> "2", "2"::equals, "2"::equals
    );

    /** 构造方法: 注册网络包 + 注册两个扩展入口到主面板 */
    public ShopAddonMod() {
        // 注册领地操作包
        CHANNEL.registerMessage(0, TerritoryScreen.TerritoryDataPacket.class,
                TerritoryScreen.TerritoryDataPacket::encode,
                TerritoryScreen.TerritoryDataPacket::new,
                TerritoryScreen.TerritoryDataPacket::handle);
        // 注册领地列表同步包
        CHANNEL.registerMessage(1, TerritoryScreen.TerritoryListPacket.class,
                TerritoryScreen.TerritoryListPacket::encode,
                TerritoryScreen.TerritoryListPacket::new,
                TerritoryScreen.TerritoryListPacket::handle);
        // 注册"领地管理"入口 → TerritoryManageScreen
        AddonRegistry.register(new AddonEntry("territory_manage", "领地管理", () -> {
            net.minecraft.client.Minecraft.getInstance().setScreen(new TerritoryManageScreen());
        }));
        // 注册"创建领地"入口 → TerritoryCreateScreen (先取消旧选区)
        AddonRegistry.register(new AddonEntry("territory_create", "创建领地", () -> {
            TerritorySelectionTracker.cancel();
            net.minecraft.client.Minecraft.getInstance().setScreen(new TerritoryCreateScreen());
        }));
    }
}
