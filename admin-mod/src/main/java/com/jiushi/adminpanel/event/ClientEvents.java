package com.jiushi.adminpanel.event;

import com.jiushi.adminpanel.AdminMod;
import com.jiushi.adminpanel.client.KeyBindings;
import com.jiushi.adminpanel.client.MainScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端事件处理器
 * <p>
 * 监听按键输入事件, 按下 J 键时打开管理面板.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = AdminMod.MODID)
public class ClientEvents {

    /** 监听按键: J 键 → 打开面板 J 键 → 关闭面板 (MainScreen.keyPressed处理) */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (KeyBindings.OPEN_ADMIN.consumeClick()) {
            Minecraft.getInstance().setScreen(new MainScreen());
        }
    }
}
