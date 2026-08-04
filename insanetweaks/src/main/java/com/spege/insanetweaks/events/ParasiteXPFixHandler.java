package com.spege.insanetweaks.events;

import com.dhanantry.scapeandrunparasites.init.SRPPotions;
import com.dhanantry.scapeandrunparasites.util.config.SRPConfigSystems;
import com.dhanantry.scapeandrunparasites.world.SRPSaveData;
import com.spege.insanetweaks.skills.TraitBase;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Implements the "Assimilated Warfare" trait mechanic.
 *
 * SRParasites suppresses vanilla XP drops under two conditions, so for those kills
 * LivingExperienceDropEvent never fires and the player receives no XP at all. This
 * handler detects exactly those two conditions and spawns a small replacement orb.
 *
 * It also drains one evolution point per parasite kill from a configured phase onward.
 *
 * HISTORY (2026-08-04): this used to record the death, wait TICK_DELAY = 2 ticks and
 * cancel itself if LivingExperienceDropEvent fired in the meantime. That could never
 * work. SRP's EntityParasiteBase.onDeathUpdateOG() is a copy of vanilla's
 * onDeathUpdate(): it increments deathTime and only reaches
 * ForgeEventFactory.getExperienceDrop() at deathTime == 20. So the natural XP event
 * arrives 20 ticks after LivingDeathEvent - 18 ticks after the fallback orb had
 * already been handed out. The trait was paying +4 XP on every kill, on top of the
 * XP SRP dropped normally. The suppression conditions are all knowable at death time,
 * so the wait (and the pending map that leaked World references) is gone.
 */
public class ParasiteXPFixHandler {

    private static final int PARASITE_XP_GRANT = 4;
    private static final int ASSIMILATED_WARFARE_EVOLUTION_STAGE = 5;
    private static final int ASSIMILATED_WARFARE_EVOLUTION_DRAIN = 1;

    /**
     * SRPSaveData.get(World, int) only reads this int on the CLIENT branch, where it is
     * forwarded to createData(); server-side it is ignored entirely. 37 is what SRP itself
     * passes at its own call sites, so we match it rather than invent a value.
     */
    private static final int SRP_SAVEDATA_HINT = 37;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onParasiteDeath(LivingDeathEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule) {
            return;
        }

        EntityLivingBase entity = event.getEntityLiving();
        if (entity == null || entity.world.isRemote) {
            return;
        }

        ResourceLocation id = EntityList.getKey(entity);
        if (id == null || !"srparasites".equals(id.getResourceDomain())) {
            return;
        }

        Entity trueSource = event.getSource().getTrueSource();
        if (!(trueSource instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer killer = (EntityPlayer) trueSource;
        if (!TraitBase.hasTrait(killer, "reskillable:attack", "compatskills:assimilated_warfare")) {
            return;
        }

        this.tryDrainEvolutionPoints(entity.world);

        // SRP is about to drop XP by itself in 20 ticks - adding an orb here would double it.
        if (!this.isSrpXpSuppressed(entity)) {
            return;
        }

        int xpToGive = this.getFallbackXpForParasite(entity, id);
        if (xpToGive <= 0) {
            return;
        }

        entity.world.spawnEntity(new EntityXPOrb(entity.world, entity.posX, entity.posY, entity.posZ, xpToGive));
    }

    /**
     * Mirrors the two XP cut-offs inside EntityParasiteBase.onDeathUpdateOG(). Returns true
     * only when SRP will refuse to drop XP for this parasite, i.e. when the trait has
     * something to replace.
     */
    private boolean isSrpXpSuppressed(EntityLivingBase entity) {
        World world = entity.world;

        // Both vanilla and SRP gate the whole XP block on this gamerule. If the pack turned
        // mob loot off, the trait has no business handing XP out either.
        if (!world.getGameRules().getBoolean("doMobLoot")) {
            return false;
        }

        // Suppressor 1: dislocation code 18 ("Parasite when killed, will not drop loot, xp")
        // applies DEBAR_E, and SRP skips the XP block outright while it is active.
        if (entity.isPotionActive(SRPPotions.DEBAR_E)) {
            return true;
        }

        // Suppressor 2: evolution phase at or past SRP's own cut-off. Read SRP's config rather
        // than hardcoding a phase - the pack owns that number ("Phase Parasites Without XP").
        if (SRPConfigSystems.useEvolution) {
            SRPSaveData saveData = SRPSaveData.get(world, SRP_SAVEDATA_HINT);
            if (saveData != null
                    && saveData.getEvolutionPhase(world.provider.getDimension()) >= SRPConfigSystems.evolutionPArasitesWithoutXP) {
                return true;
            }
        }

        return false;
    }

    private int getFallbackXpForParasite(EntityLivingBase entity, ResourceLocation id) {
        if (entity == null || id == null) {
            return 0;
        }

        if ("buglin".equals(id.getResourcePath())) {
            return 0;
        }

        String className = entity.getClass().getName();
        if (className.contains(".entity.monster.inborn.")
                || className.contains(".entity.monster.deterrent.")) {
            return 0;
        }

        return PARASITE_XP_GRANT;
    }

    private void tryDrainEvolutionPoints(World world) {
        if (world == null || world.isRemote) {
            return;
        }

        SRPSaveData saveData = SRPSaveData.get(world, SRP_SAVEDATA_HINT);
        if (saveData == null) {
            return;
        }

        int dimension = world.provider.getDimension();
        if (saveData.getEvolutionPhase(dimension) < ASSIMILATED_WARFARE_EVOLUTION_STAGE) {
            return;
        }

        // Mirror SRP's own parasite-death subtraction style: reduce the point pool
        // without forcibly dropping the current phase below its floor.
        // srcID 9 = PENALTY_OR_LOSS in SRP 1.10.7's point-source debug log.
        saveData.setTotalKills(dimension, -ASSIMILATED_WARFARE_EVOLUTION_DRAIN, true, world, false, 9);
    }
}
