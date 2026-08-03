package com.jiushi.adminpanel.network;

import com.jiushi.adminpanel.AdminMod;
import com.jiushi.adminpanel.client.ClientData;
import com.jiushi.adminpanel.server.WarpManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 传送点网络包 (双向)
 * <p>
 * 客户端→服务端: LIST(请求列表) / GO(传送) / SET(设置新传送点) / DEL(删除)
 * 服务端→客户端: LIST(返回传送点列表, 按可见性+权限过滤)
 * <p>
 * 传送点三级可见性: PRIVATE(0, 仅owner) / PUBLIC(1, 所有人) / OFFICIAL(2, 官方, 管理员设置)
 */
public class WarpPacket {

    public enum Action { LIST, GO, SET, DEL }

    private Action action;
    private String warpName;        // 传送点名称
    private int visibility;         // 可见性 (SET时使用)
    private Map<String, WarpManager.WarpPoint> warps; // LIST响应时使用

    public WarpPacket() {}

    /** 简化的构造器, 默认visibility为PUBLIC */
    public WarpPacket(Action action, String warpName) {
        this(action, warpName, WarpManager.PUBLIC);
    }

    public WarpPacket(Action action, String warpName, int visibility) {
        this.action = action;
        this.warpName = warpName;
        this.visibility = visibility;
    }

    /** 反序列化 */
    public WarpPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.warpName = buf.readUtf();
        this.visibility = buf.readInt();
        if (this.action == Action.LIST) {
            int size = buf.readInt();
            this.warps = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                WarpManager.WarpPoint wp = new WarpManager.WarpPoint();
                wp.name = buf.readUtf();
                wp.dimension = buf.readUtf();
                wp.x = buf.readDouble();
                wp.y = buf.readDouble();
                wp.z = buf.readDouble();
                wp.yaw = buf.readFloat();
                wp.pitch = buf.readFloat();
                wp.visibility = buf.readInt();
                wp.owner = buf.readUtf();
                this.warps.put(wp.name, wp);
            }
        }
    }

    /** 序列化 */
    public static void encode(WarpPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action);
        buf.writeUtf(packet.warpName != null ? packet.warpName : "");
        buf.writeInt(packet.visibility);
        if (packet.action == Action.LIST && packet.warps != null) {
            buf.writeInt(packet.warps.size());
            for (WarpManager.WarpPoint wp : packet.warps.values()) {
                buf.writeUtf(wp.name);
                buf.writeUtf(wp.dimension);
                buf.writeDouble(wp.x);
                buf.writeDouble(wp.y);
                buf.writeDouble(wp.z);
                buf.writeFloat(wp.yaw);
                buf.writeFloat(wp.pitch);
                buf.writeInt(wp.visibility);
                buf.writeUtf(wp.owner != null ? wp.owner : "");
            }
        } else if (packet.action == Action.LIST) {
            buf.writeInt(0);
        }
    }

    /** 处理请求 (客户端或服务端) */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            if (player == null) {
                // === 客户端接收 LIST 响应 ===
                if (this.action == Action.LIST) {
                    ClientData.pendingWarps = this.warps;
                    ClientData.warpDataReady = true;
                }
                ctx.get().setPacketHandled(true);
                return;
            }

            // === 服务端处理 ===
            // 管理员权限判定 (本模组 或 原生OP)
            boolean isAdmin = com.jiushi.adminpanel.server.SetupManager.isAdmin(player.getGameProfile().getName())
                    || player.getServer().getPlayerList().isOp(player.getGameProfile());

            switch (action) {
                case LIST: {
                    // 返回按可见性和权限过滤后的传送点列表
                    Map<String, WarpManager.WarpPoint> allWarps = WarpManager.getAllWarps(player);
                    WarpPacket response = new WarpPacket(Action.LIST, null);
                    response.warps = allWarps;
                    AdminMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), response);
                    break;
                }

                case SET: {
                    // 设置新传送点 (或覆盖自己的传送点)
                    WarpManager.WarpPoint existingWp = WarpManager.getWarp(warpName);
                    if (existingWp != null) {
                        // 覆盖权限: 管理员 或 传送点owner本人
                        boolean canOverwrite = isAdmin
                                || (existingWp.owner != null
                                && existingWp.owner.equals(player.getName().getString()));
                        if (!canOverwrite) {
                            player.sendSystemMessage(Component.literal("§c你没有权限覆盖此传送点"));
                            break;
                        }
                    }
                    // 非管理员不能设置官方传送点, 限定最高为 PUBLIC
                    int clampedVis = visibility;
                    if (clampedVis > WarpManager.PUBLIC && !isAdmin) {
                        clampedVis = WarpManager.PUBLIC;
                    }
                    if (clampedVis < WarpManager.PRIVATE) {
                        clampedVis = WarpManager.PRIVATE;
                    }
                    WarpManager.setWarp(warpName, player, clampedVis);
                    player.sendSystemMessage(Component.literal("§a传送点 " + warpName + " 已设置"));
                    break;
                }

                case DEL: {
                    // 删除传送点: 管理员 或 owner本人
                    WarpManager.WarpPoint existingWarp = WarpManager.getWarp(warpName);
                    if (existingWarp == null) {
                        player.sendSystemMessage(Component.literal("§c传送点 " + warpName + " 不存在"));
                        break;
                    }
                    boolean canDelete = isAdmin
                            || (existingWarp.owner != null
                            && existingWarp.owner.equals(player.getName().getString()));
                    if (!canDelete) {
                        player.sendSystemMessage(Component.literal("§c你没有权限删除此传送点"));
                        break;
                    }
                    WarpManager.removeWarp(warpName);
                    player.sendSystemMessage(Component.literal("§a传送点 " + warpName + " 已删除"));
                    break;
                }

                case GO: {
                    // 传送到指定传送点 (跨维度)
                    WarpManager.WarpPoint wp = WarpManager.getWarp(warpName);
                    if (wp != null) {
                        // 权限校验: 官方/公开所有人可用; 私人仅创建者本人可用
                        boolean canUse = wp.visibility == WarpManager.PUBLIC
                                || wp.visibility == WarpManager.OFFICIAL
                                || (wp.owner != null
                                && wp.owner.equals(player.getName().getString()));
                        if (!canUse) {
                            player.sendSystemMessage(Component.literal("§c你没有权限传送至该传送点"));
                            break;
                        }
                        // 解析维度 ResourceKey → 获取 ServerLevel → 执行传送
                        ServerLevel targetLevel = player.getServer().getLevel(
                                ResourceKey.create(Registries.DIMENSION, new ResourceLocation(wp.dimension)));
                        if (targetLevel != null) {
                            player.teleportTo(targetLevel, wp.x, wp.y, wp.z, wp.yaw, wp.pitch);
                            player.sendSystemMessage(Component.literal("§a已传送到 " + warpName));
                        } else {
                            player.sendSystemMessage(Component.literal("§c目标维度不存在"));
                        }
                    } else {
                        player.sendSystemMessage(Component.literal("§c传送点 " + warpName + " 不存在"));
                    }
                    break;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
