package com.jiushi.adminpanel.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DLC 扩展注册表
 * <p>
 * 全局静态注册表, DLC 模组在 @Mod 构造方法中调用 {@link #register} 注册入口.
 * 主面板通过 {@link #getEntries} 获取所有已注册的扩展入口, 渲染到 EXTENSIONS 标签页.
 * <p>
 * 线程安全: 所有操作在模组加载阶段(FML构造方法)执行, 此时为单线程.
 */
public class AddonRegistry {
    /** 已注册的扩展入口列表 */
    private static final List<AddonEntry> entries = new ArrayList<>();

    /**
     * 注册扩展入口.
     * 通常在 DLC 模组的 @Mod 构造方法中调用.
     */
    public static void register(AddonEntry entry) {
        entries.add(entry);
    }

    /**
     * 获取所有已注册的扩展入口 (只读).
     * 在主面板 EXTENSIONS 标签页遍历渲染.
     */
    public static List<AddonEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }
}
