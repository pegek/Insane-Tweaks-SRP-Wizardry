package com.spege.insanetweaks.events;

import java.util.Iterator;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.magic.IMagicParasite;
import com.spege.insanetweaks.entities.magic.MagicParasiteTraits;
import com.spege.insanetweaks.entities.magic.SpectralTags;
import com.spege.insanetweaks.entities.sapling.SaplingDropPenalty;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Magic parasites' drop rules and the spectral summons' lifetime:
 *
 * <ul>
 *   <li>A magic parasite loses each normal (SRP) drop with {@code baseDropPenalty}, then rolls the
 *   bonus table, which is never reduced. LOWEST priority, so SRP's own drops are already in.</li>
 *   <li>A spectral drops nothing and gives no XP, and dissolves when its time is up, whatever
 *   the master switch says (otherwise switching it off would make spectrals permanent).</li>
 * </ul>
 */
public class MagicParasiteHandler {

    public static final ResourceLocation BONUS_TABLE = new ResourceLocation(InsaneTweaksMod.MODID,
            "entities/magic_parasite");
    private static final int SPECTRAL_CHECK_INTERVAL = 10;

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote || entity.ticksExisted % SPECTRAL_CHECK_INTERVAL != 0
                || !(entity instanceof EntityParasiteBase) || !SpectralTags.isSpectral(entity)) {
            return;
        }
        if (SpectralTags.isExpired(entity, entity.world.getTotalWorldTime())) {
            MagicParasiteTraits.dispel(entity);
        } else {
            MagicParasiteTraits.burst(entity.world, entity.posX, entity.posY + entity.height * 0.5D, entity.posZ, 3,
                    entity.width * 0.5D);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrops(LivingDropsEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote) {
            return;
        }
        if (SpectralTags.isSpectral(victim)) {
            event.getDrops().clear();
            return;
        }
        if (!(victim instanceof IMagicParasite) || !MagicParasiteTraits.enabled()) {
            return;
        }
        double penalty = MagicParasiteTraits.cfg().baseDropPenalty;
        if (penalty > 0.0D) {
            Iterator<EntityItem> drops = event.getDrops().iterator();
            while (drops.hasNext()) {
                drops.next();
                if (!SaplingDropPenalty.keepsDrop(victim.world.rand.nextDouble(), penalty)) {
                    drops.remove();
                }
            }
        }
        addBonusLoot(event, victim);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExperience(LivingExperienceDropEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (!victim.world.isRemote && SpectralTags.isSpectral(victim)) {
            event.setDroppedExperience(0);
        }
    }

    private static void addBonusLoot(LivingDropsEvent event, EntityLivingBase victim) {
        if (!(victim.world instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) victim.world;
        LootTable table = world.getLootTableManager().getLootTableFromLocation(BONUS_TABLE);
        LootContext.Builder context = new LootContext.Builder(world).withLootedEntity(victim)
                .withDamageSource(event.getSource());
        Entity killer = event.getSource().getTrueSource();
        if (killer instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) killer;
            context.withPlayer(player).withLuck(player.getLuck());
        }
        for (ItemStack stack : table.generateLootForPools(world.rand, context.build())) {
            if (stack.isEmpty()) {
                continue;
            }
            EntityItem item = new EntityItem(world, victim.posX, victim.posY, victim.posZ, stack);
            item.setDefaultPickupDelay();
            event.getDrops().add(item);
        }
    }
}
