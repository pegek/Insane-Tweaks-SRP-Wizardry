package com.spege.insanetweaks.worldgen.sanctum;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;

/**
 * The sanctum advancements (own tab, {@code assets/insanetweaks/advancements/sanctum/}) carry only an
 * {@code impossible} criterion, so code grants them. Manacore rewards {@code sanctum/cleansed} with
 * maximum mana through its own config.
 */
public final class SanctumAdvancements {

    public static final String FOUND = "sanctum/found";
    public static final String CLEANSED = "sanctum/cleansed";

    private SanctumAdvancements() {
    }

    public static boolean has(EntityPlayerMP player, String path) {
        Advancement advancement = lookup(player, path);
        return advancement == null || player.getAdvancements().getProgress(advancement).isDone();
    }

    public static void grant(EntityPlayerMP player, String path) {
        Advancement advancement = lookup(player, path);
        if (advancement == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getProgress(advancement);
        if (progress.isDone()) {
            return;
        }
        for (String criterion : progress.getRemaningCriteria()) {
            player.getAdvancements().grantCriterion(advancement, criterion);
        }
    }

    private static Advancement lookup(EntityPlayerMP player, String path) {
        MinecraftServer server = player.getServer();
        return server == null ? null
                : server.getAdvancementManager().getAdvancement(new ResourceLocation(InsaneTweaksMod.MODID, path));
    }
}
