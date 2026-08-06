package com.jiushi.adminpanel.network;

import com.jiushi.adminpanel.AdminMod;
import com.jiushi.adminpanel.client.ClientData;
import com.jiushi.adminpanel.client.MainScreen;
import com.jiushi.adminpanel.server.MoneyManager;
import com.jiushi.adminpanel.server.SetupManager;
import com.jiushi.adminpanel.server.ShopManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 商店/验证网络包 (双向)
 * <p>
 * 客户端→服务端: LIST(请求刷新) / BUY(购买) / ADD(上架) / REMOVE(下架) / VERIFY(验证激活码)
 * <p>
 * 服务端→客户端: 同上协议, 服务端打包商店列表+金币+管理员列表+请求状态返回给客户端
 * <p>
 * 本包同时承载激活码验证功能 (Action.VERIFY), 因为验证成功后会触发面板数据全面刷新.
 */
public class ShopPacket {

    public enum Action {
        LIST, BUY, ADD, REMOVE, VERIFY
    }

    /** 待发送的状态消息队列: 玩家UUID → 消息文本 */
    private static final java.util.Map<UUID, String> pendingStatusMessages = new ConcurrentHashMap<>();
    /** LIST 分页大小: 每页最多携带的商品数 (防止单包过大被踢出) */
    private static final int LIST_PAGE_SIZE = 30;

    Action action;
    int index;          // 商品ID (用于购买/下架); LIST时作为页码 (0起)
    int price;          // 商品价格
    String code;        // 激活码文本 (用于VERIFY)
    // --- 以下字段仅在 LIST 响应中使用 ---
    List<ShopManager.ShopListing> listings; // 本页商品列表
    boolean more;       // 是否还有下一页 (分页标志)
    boolean verified;   // 当前玩家是否已验证为管理员
    String statusMsg;   // 状态提示消息
    int money;          // 玩家当前金币
    String role;        // 玩家管理员角色
    List<MainScreen.AdminInfo> adminList; // 完整管理员列表 (用于OP管理页)

    public ShopPacket() {}

    public ShopPacket(Action action) {
        this.action = action;
    }

    public ShopPacket(Action action, int index, int price, String code) {
        this.action = action;
        this.index = index;
        this.price = price;
        this.code = code;
    }

    /** 反序列化: 从网络字节流解析 (支持LIST复杂结构) */
    public ShopPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.index = buf.readInt();
        this.price = buf.readInt();
        this.code = buf.readUtf();
        // LIST 响应附加字段
        if (action == Action.LIST) {
            int size = buf.readInt();
            this.listings = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                ShopManager.ShopListing listing = new ShopManager.ShopListing();
                listing.id = buf.readInt();
                listing.sellerName = buf.readUtf();
                listing.itemName = buf.readUtf();
                listing.itemNbt = buf.readUtf();
                listing.price = buf.readInt();
                listing.quantity = buf.readInt();
                this.listings.add(listing);
            }
            this.verified = buf.readBoolean();
            this.statusMsg = buf.readUtf();
            this.money = buf.readInt();
            this.role = buf.readUtf();
            int adminCount = buf.readInt();
            this.adminList = new ArrayList<>();
            for (int i = 0; i < adminCount; i++) {
                MainScreen.AdminInfo ai = new MainScreen.AdminInfo();
                ai.name = buf.readUtf();
                ai.role = buf.readUtf();
                this.adminList.add(ai);
            }
            this.more = buf.readBoolean(); // 是否还有下一页
        }
    }

    /** 序列化: 写入网络字节流 */
    public static void encode(ShopPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action);
        buf.writeInt(packet.index);
        buf.writeInt(packet.price);
        buf.writeUtf(packet.code != null ? packet.code : "");
        if (packet.action == Action.LIST) {
            // 商品列表
            if (packet.listings != null) {
                buf.writeInt(packet.listings.size());
                for (ShopManager.ShopListing listing : packet.listings) {
                    buf.writeInt(listing.id);
                    buf.writeUtf(listing.sellerName);
                    buf.writeUtf(listing.itemName);
                    buf.writeUtf(listing.itemNbt != null ? listing.itemNbt : "");
                    buf.writeInt(listing.price);
                    buf.writeInt(listing.quantity);
                }
            } else {
                buf.writeInt(0);
            }
            // 管理员状态
            buf.writeBoolean(packet.verified);
            buf.writeUtf(packet.statusMsg != null ? packet.statusMsg : "");
            buf.writeInt(packet.money);
            buf.writeUtf(packet.role != null ? packet.role : "");
            // 管理员列表
            if (packet.adminList != null) {
                buf.writeInt(packet.adminList.size());
                for (MainScreen.AdminInfo ai : packet.adminList) {
                    buf.writeUtf(ai.name);
                    buf.writeUtf(ai.role);
                }
            } else {
                buf.writeInt(0);
            }
            buf.writeBoolean(packet.more); // 是否还有下一页
        }
    }

    /**
     * 处理数据包
     * <p>
     * 若 player==null, 说明是客户端在接收服务端的 LIST 响应.
     * 否则是服务端在接收客户端的操作请求.
     */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            if (player == null) {
                // === 客户端接收 LIST 响应 (分页累计) ===
                if (this.action == Action.LIST) {
                    if (this.index <= 0) {
                        // 第一页: 重置累计列表
                        ClientData.pendingShopListings = new ArrayList<>();
                    }
                    if (this.listings != null) {
                        if (ClientData.pendingShopListings == null) {
                            ClientData.pendingShopListings = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
                        }
                        ClientData.pendingShopListings.addAll(this.listings);
                    }
                    if (this.more) {
                        // 还有下一页 → 自动请求 (index 作为页码)
                        AdminMod.CHANNEL.sendToServer(
                                new ShopPacket(Action.LIST, this.index + 1, 0, null));
                    } else {
                        // 最后一页: 汇总元数据并标记就绪
                        ClientData.pendingShopVerified = this.verified;
                        ClientData.pendingStatusMsg = this.statusMsg;
                        ClientData.pendingMoney = this.money;
                        ClientData.pendingRole = this.role;
                        ClientData.pendingAdminList = this.adminList;
                        ClientData.shopDataReady = true;
                    }
                }
                ctx.get().setPacketHandled(true);
                return;
            }

            // === 以下为服务端处理 ===
            switch (action) {
                case LIST: {
                    // 客户端请求刷新面板数据 (index 作为页码, 从0开始)
                    buildAndSendListResponse(player,
                            pendingStatusMessages.remove(player.getUUID()), this.index);
                    break;
                }

                case BUY: {
                    // 购买商品: 扣款 → 转移物品 → 转账给卖家
                    // 先获取商品信息用于提示（购买后可能被移除）
                    ShopManager.ShopListing boughtBefore = ShopManager.getListing(index);
                    String itemDesc = boughtBefore != null ? boughtBefore.itemName : "商品";
                    ShopManager.PurchaseResult result = ShopManager.purchaseItem(player, index);
                    switch (result) {
                        case SUCCESS:
                            pendingStatusMessages.put(player.getUUID(), "§a购买成功: " + itemDesc);
                            player.sendSystemMessage(Component.literal("§a购买成功"));
                            break;
                        case INSUFFICIENT_FUNDS:
                            pendingStatusMessages.put(player.getUUID(), "§c余额不足");
                            player.sendSystemMessage(Component.literal("§c余额不足"));
                            break;
                        case SOLD_OUT:
                        case NOT_FOUND:
                            pendingStatusMessages.put(player.getUUID(), "§c商品已售罄或不存在");
                            player.sendSystemMessage(Component.literal("§c商品已售罄或不存在"));
                            break;
                        case DATA_ERROR:
                            pendingStatusMessages.put(player.getUUID(), "§c商品数据异常，无法购买");
                            player.sendSystemMessage(Component.literal("§c商品数据异常，无法购买"));
                            break;
                    }
                    buildAndSendListResponse(player, pendingStatusMessages.remove(player.getUUID()));
                    break;
                }

                case ADD: {
                    // 上架手持物品: 复制NBT → 加入商店列表 → 清空手持物品
                    if (price <= 0) {
                        pendingStatusMessages.put(player.getUUID(), "§c价格必须大于0");
                        player.sendSystemMessage(Component.literal("§c价格必须大于0"));
                        buildAndSendListResponse(player, pendingStatusMessages.remove(player.getUUID()));
                        break;
                    }
                    if (player.getMainHandItem() != null && !player.getMainHandItem().isEmpty()) {
                        ItemStack heldItem = player.getMainHandItem().copy();
                        if (ShopManager.addListing(player, heldItem, price)) {
                            player.getMainHandItem().setCount(0); // 上架后清空手持
                            pendingStatusMessages.put(player.getUUID(), "§a商品已上架");
                            player.sendSystemMessage(Component.literal("§a商品已上架"));
                        } else {
                            pendingStatusMessages.put(player.getUUID(), "§c上架失败，请检查物品和价格");
                            player.sendSystemMessage(Component.literal("§c上架失败，请检查物品和价格"));
                        }
                    } else {
                        pendingStatusMessages.put(player.getUUID(), "§c手中没有物品");
                        player.sendSystemMessage(Component.literal("§c手中没有物品"));
                    }
                    buildAndSendListResponse(player, pendingStatusMessages.remove(player.getUUID()));
                    break;
                }

                case REMOVE: {
                    ShopManager.ShopListing targetListing = ShopManager.getListing(index);
                    if (targetListing != null) {
                        boolean isAdmin = SetupManager.isAdmin(player.getName().getString())
                                || player.getServer().getPlayerList().isOp(player.getGameProfile());
                        boolean isOwner = targetListing.sellerName != null
                                && targetListing.sellerName.equalsIgnoreCase(player.getName().getString());
                        if (!isAdmin && !isOwner) {
                            pendingStatusMessages.put(player.getUUID(), "§c你没有权限下架此商品");
                            player.sendSystemMessage(Component.literal("§c你没有权限下架此商品"));
                            break;
                        }
                        if (targetListing.quantity > 0) {
                            returnItemToSeller(player, targetListing);
                        }
                        ShopManager.removeListing(index);
                        pendingStatusMessages.put(player.getUUID(), "§a商品已下架");
                        player.sendSystemMessage(Component.literal("§a商品已下架"));
                    } else {
                        pendingStatusMessages.put(player.getUUID(), "§c商品不存在");
                        player.sendSystemMessage(Component.literal("§c商品不存在"));
                    }
                    buildAndSendListResponse(player, pendingStatusMessages.remove(player.getUUID()));
                    break;
                }

                case VERIFY: {
                    // 验证激活码: 调用 SetupManager 尝试匹配
                    String verifyResult = SetupManager.verifyInviteCode(code, player.getName().getString());
                    if (verifyResult != null) {
                        if (!"already".equals(verifyResult)) {
                            // 验证成功 → 赋予OP权限
                            player.getServer().getPlayerList().op(player.getGameProfile());
                        }
                        pendingStatusMessages.put(player.getUUID(), "§a验证成功，您已成为管理员");
                        player.sendSystemMessage(Component.literal("§a验证成功！"));
                        buildAndSendListResponse(player, pendingStatusMessages.remove(player.getUUID()));
                    } else {
                        pendingStatusMessages.put(player.getUUID(), "§c验证码错误或已过期");
                        buildAndSendListResponse(player, pendingStatusMessages.remove(player.getUUID()));
                    }
                    break;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * 构建并发送 LIST 响应给指定玩家 (分页).
     * <p>
     * 页0包含全部元数据 (管理员状态/金币/角色/管理员列表/状态消息), 后续页只携带商品.
     * 超过 LIST_PAGE_SIZE 的商品自动拆分为多页, 客户端收到 more=true 时自动请求下一页.
     */
    public static void buildAndSendListResponse(ServerPlayer player, String statusMsg, int page) {
        List<ShopManager.ShopListing> all = ShopManager.getAllListings();
        int from = Math.max(0, page) * LIST_PAGE_SIZE;
        int to = Math.min(all.size(), from + LIST_PAGE_SIZE);

        ShopPacket response = new ShopPacket(Action.LIST);
        response.index = page;
        response.listings = from < all.size() ? all.subList(from, to) : new ArrayList<>();
        response.more = to < all.size(); // 还有下一页
        if (page <= 0) {
            // 仅首页携带元数据
            boolean isAdmin = SetupManager.isAdmin(player.getName().getString())
                    || player.getServer().getPlayerList().isOp(player.getGameProfile());
            response.verified = isAdmin;
            response.statusMsg = statusMsg != null ? statusMsg : "";
            response.money = MoneyManager.getMoney(player);
            response.role = SetupManager.getRole(player.getName().getString());
            // 构建管理员列表 (用于 OP_MANAGE 标签页显示)
            response.adminList = new ArrayList<>();
            for (var e : SetupManager.getAdmins().entrySet()) {
                MainScreen.AdminInfo ai = new MainScreen.AdminInfo();
                ai.name = e.getKey();
                ai.role = e.getValue();
                response.adminList.add(ai);
            }
        }
        AdminMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), response);
    }

    /** 便捷方法: 默认发送第一页 (兼容旧调用) */
    public static void buildAndSendListResponse(ServerPlayer player, String statusMsg) {
        buildAndSendListResponse(player, statusMsg, 0);
    }

    /**
     * 下架时退还物品给卖家.
     * 若卖家在线 → 直接放入背包或掉落; 若离线 → 放入下架操作者背包
     */
    private static void returnItemToSeller(ServerPlayer remover, ShopManager.ShopListing listing) {
        try {
            ServerPlayer seller = remover.getServer().getPlayerList().getPlayerByName(listing.sellerName);
            CompoundTag tag = TagParser.parseTag(listing.itemNbt);
            ItemStack stack = ItemStack.of(tag);
            stack.setCount(listing.quantity);
            if (seller != null) {
                // 卖家在线: 还给他
                if (!seller.getInventory().add(stack)) {
                    seller.drop(stack, false);
                }
                seller.sendSystemMessage(Component.literal(
                        "§a你的商品 " + listing.itemName + " x" + listing.quantity + " 已退还"));
                if (seller != remover) {
                    remover.sendSystemMessage(Component.literal(
                            "§a已退还 " + listing.sellerName + " 的商品"));
                }
            } else {
                // 卖家离线: 放入下架者背包
                if (!remover.getInventory().add(stack)) {
                    remover.drop(stack, false);
                }
                remover.sendSystemMessage(Component.literal(
                        "§e卖家 " + listing.sellerName + " 不在线，物品已放入你的背包"));
            }
        } catch (Exception e) {
            AdminMod.LOGGER.error("Failed to return item to seller", e);
            remover.sendSystemMessage(Component.literal("§c物品退还失败，请手动处理"));
        }
    }
}