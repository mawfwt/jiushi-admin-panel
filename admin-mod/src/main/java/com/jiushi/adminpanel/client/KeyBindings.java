package com.jiushi.adminpanel.client;

import com.jiushi.adminpanel.AdminMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定注册
 * <p>
 * 默认 J 键打开/关闭管理面板.
 * 可在 Minecraft 设置 → 按键绑定中重设.
 */
@Mod.EventBusSubscriber(modid = AdminMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyBindings {

    /** 打开管理面板的热键 (默认 J, 显示名取自语言文件) */
    public static final KeyMapping OPEN_ADMIN = new KeyMapping(
            "key.jiushi_admin.open_admin",
            GLFW.GLFW_KEY_J,
            "key.categories.jiushi_admin"
    );

    /** 在 Forge 按键注册事件中注册本模组的热键 */
    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_ADMIN);
    }
}
