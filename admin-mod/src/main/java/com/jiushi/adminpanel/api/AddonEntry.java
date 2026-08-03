package com.jiushi.adminpanel.api;

/**
 * DLC 扩展入口定义
 * <p>
 * 每个 DLC 模组在启动时通过 {@link AddonRegistry#register} 注册一个或多个入口.
 * 入口会在主面板的 EXTENSIONS 标签页中显示为按钮.
 */
public class AddonEntry {
    /** 入口唯一ID */
    public final String id;
    /** 按钮显示名称 */
    public final String name;
    /** 点击后执行的打开动作 (通常是打开DLC的Screen) */
    public final Runnable openAction;

    /**
     * @param id         唯一标识 (如 "friends", "territory_manage")
     * @param name       显示名称 (如 "好友", "领地管理")
     * @param openAction 点击回调 (设置为新Screen)
     */
    public AddonEntry(String id, String name, Runnable openAction) {
        this.id = id;
        this.name = name;
        this.openAction = openAction;
    }
}
