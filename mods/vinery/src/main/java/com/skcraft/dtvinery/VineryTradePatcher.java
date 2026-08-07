package com.skcraft.dtvinery;

import com.dtteam.dynamictrees.DynamicTrees;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.satisfy.vinery.core.entity.WanderingWinemakerEntity;

import java.lang.reflect.Field;

final class VineryTradePatcher {
    private static final ResourceLocation APPLE_SAPLING = ResourceLocation.fromNamespaceAndPath("vinery", "apple_tree_sapling");
    private static final ResourceLocation DARK_CHERRY_SAPLING = ResourceLocation.fromNamespaceAndPath("vinery", "dark_cherry_sapling");
    private static final ResourceLocation APPLE_OAK_SEED = DynamicTrees.location("apple_oak_seed");
    private static final ResourceLocation DARK_CHERRY_SEED = ResourceLocation.fromNamespaceAndPath("dtvinery", "dark_cherry_seed");

    private VineryTradePatcher() {
    }

    static void patchTrades() {
        VillagerTrades.ItemListing[] listings = WanderingWinemakerEntity.TRADES.get(1);
        if (listings == null) {
            return;
        }

        Item appleSeed = BuiltInRegistries.ITEM.get(APPLE_OAK_SEED);
        Item darkCherrySeed = BuiltInRegistries.ITEM.get(DARK_CHERRY_SEED);
        if (appleSeed == null || appleSeed == Items.AIR || darkCherrySeed == null || darkCherrySeed == Items.AIR) {
            return;
        }

        for (int i = 0; i < listings.length; i++) {
            VillagerTrades.ItemListing listing = listings[i];
            if (!(listing instanceof VillagerTrades.ItemsForEmeralds trade)) {
                continue;
            }

            ItemStack sold = readSoldStack(trade);
            if (sold.isEmpty()) {
                continue;
            }

            ResourceLocation soldId = BuiltInRegistries.ITEM.getKey(sold.getItem());
            if (APPLE_SAPLING.equals(soldId)) {
                listings[i] = copyTrade(trade, appleSeed);
            } else if (DARK_CHERRY_SAPLING.equals(soldId)) {
                listings[i] = copyTrade(trade, darkCherrySeed);
            }
        }
    }

    private static ItemStack readSoldStack(VillagerTrades.ItemsForEmeralds trade) {
        try {
            Field field = VillagerTrades.ItemsForEmeralds.class.getDeclaredField("itemStack");
            field.setAccessible(true);
            return ((ItemStack) field.get(trade)).copy();
        } catch (ReflectiveOperationException error) {
            return ItemStack.EMPTY;
        }
    }

    private static VillagerTrades.ItemsForEmeralds copyTrade(VillagerTrades.ItemsForEmeralds trade, Item replacement) {
        ItemStack sold = readSoldStack(trade);
        return new VillagerTrades.ItemsForEmeralds(
                new ItemStack(replacement, Math.max(1, sold.getCount())),
                readIntField(trade, "emeraldCost"),
                Math.max(1, sold.getCount()),
                readIntField(trade, "maxUses"),
                readIntField(trade, "villagerXp"),
                readFloatField(trade, "priceMultiplier")
        );
    }

    private static int readIntField(VillagerTrades.ItemsForEmeralds trade, String name) {
        try {
            Field field = VillagerTrades.ItemsForEmeralds.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(trade);
        } catch (ReflectiveOperationException error) {
            return 0;
        }
    }

    private static float readFloatField(VillagerTrades.ItemsForEmeralds trade, String name) {
        try {
            Field field = VillagerTrades.ItemsForEmeralds.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getFloat(trade);
        } catch (ReflectiveOperationException error) {
            return 0.0F;
        }
    }
}
