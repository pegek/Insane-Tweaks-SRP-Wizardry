package com.spege.srpwizcore.bbcompat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import baubles.api.BaublesApi;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Brings the Broken Heart trinket ({@code bountifulbaubles:trinketbrokenheart}) back from the
 * dead, and moves it onto the one death hook FirstAid actually honours.
 *
 * <p><b>Why the item is inert.</b> Bountiful Baubles drives it from {@code LivingDamageEvent}.
 * FirstAid's {@code EventHandler.onLivingHurt} is {@code @SubscribeEvent(priority = LOWEST)} and,
 * for every non-fake player on the server side, ends in an unconditional
 * {@code event.setCanceled(true)} on both of its exit paths — verified in the bytecode of
 * {@code firstaid-1.6.22.jar}. A cancelled {@code LivingHurtEvent} makes
 * {@code ForgeHooks.onLivingHurt} return 0, {@code EntityPlayer.damageEntity} returns before it
 * would post the damage event, and so {@code LivingDamageEvent} is never fired for a player at
 * all. The trinket is not weakened by FirstAid, it is unreachable.
 *
 * <p><b>Why the totem hook rather than an earlier damage event.</b> Reimplementing the item on
 * {@code LivingHurtEvent} at HIGHEST would run before FirstAid, but its trigger would be wrong:
 * the original condition is "this hit leaves me under 1 HP", and under FirstAid death is decided
 * by a body-part model, not by total health. That condition would fire on survivable hits and
 * miss lethal ones (a hit that empties the head kills a player who still has plenty of health
 * elsewhere). {@code EntityLivingBase.checkTotemDeathProtection} has no such mismatch: FirstAid
 * calls it from {@code CommonUtils.killPlayer} precisely when it has decided the player dies, and
 * on a {@code true} return it restores every {@code canCauseDeath} part to 1 HP itself. That is
 * the trinket's advertised outcome, implemented by the mod that owns the health model.
 *
 * <p><b>Why the max-health cost is safe here.</b> FirstAid rescales its body parts from the
 * max-health attribute ({@code PlayerDamageModel.runScaleLogic}), so the drain does shrink every
 * part. It cannot backfire: {@code DamageablePart.setMaxHealth} floors a part at 2 max health and
 * only ever clamps {@code currentHealth} <em>down to</em> the new maximum, never below it, so a
 * part just restored to 1 HP survives the rescale and the player cannot be re-killed by the very
 * cost of being saved.
 *
 * <p><b>Deliberate reuse of Bountiful Baubles' modifier UUID.</b> The drain is applied under the
 * mod's own {@code 554f3929-…} UUID, because the mod's {@code onPlayerWake} listener — which is on
 * {@code PlayerWakeUpEvent} and therefore still fires perfectly well — removes exactly that
 * modifier. Reusing it means "sleep to regenerate heart containers" keeps working, and keeps
 * obeying Bountiful Baubles' own {@code regenheartcontainers} config, with no code here at all.
 *
 * <p>Armed only when Bountiful Baubles <em>and</em> FirstAid are both present: without FirstAid the
 * mod's own handler is alive and this would fire on top of it.
 */
public final class BrokenHeartProvider {

    private static final ResourceLocation BROKEN_HEART =
            new ResourceLocation("bountifulbaubles", "trinketbrokenheart");

    /**
     * Bountiful Baubles' own modifier UUID, copied from
     * {@code ItemTrinketBrokenHeart.MODIFIER_UUID}. Not ours to choose — see the class javadoc.
     * If a Bountiful Baubles update ever changes it, sleep will stop refunding the hearts and
     * this constant is the thing to re-check.
     */
    private static final UUID MODIFIER_UUID =
            UUID.fromString("554f3929-4193-4ae5-a4da-4b528a89ca32");

    private static final String MODIFIER_NAME = "Broken Heart MaxHP drain";

    /** Wall-clock, never world time: a player crossing dimensions would go back in time. */
    private static final Map<UUID, Long> LAST_SAVE = new HashMap<UUID, Long>();

    private static Item brokenHeart;

    private BrokenHeartProvider() {
    }

    /**
     * Resolves the trinket once, after item registration. Call from
     * {@code FMLInitializationEvent}.
     */
    public static void arm() {
        if (!Loader.isModLoaded("bountifulbaubles") || !Loader.isModLoaded("baubles")) {
            return;
        }
        if (!Loader.isModLoaded("firstaid")) {
            // Without FirstAid the item's own LivingDamageEvent handler is reachable and works.
            SrpWizCore.LOGGER.info("[srpwizcore] bbCompat: FirstAid absent, leaving the Broken "
                    + "Heart to Bountiful Baubles' own handler");
            return;
        }
        final Item item = ForgeRegistries.ITEMS.getValue(BROKEN_HEART);
        if (item == null) {
            SrpWizCore.LOGGER.warn("[srpwizcore] bbCompat: {} not in the item registry, Broken "
                    + "Heart repair disabled", BROKEN_HEART);
            return;
        }
        brokenHeart = item;
        SrpWizCore.LOGGER.info("[srpwizcore] bbCompat: Broken Heart death-save armed "
                + "(cost {} max health, floor {}, cooldown {}s)",
                SrpWizCoreConfig.bbCompat.brokenHeartMaxHealthCost,
                SrpWizCoreConfig.bbCompat.brokenHeartMinMaxHealth,
                SrpWizCoreConfig.bbCompat.brokenHeartCooldownSeconds);
    }

    /**
     * Decides whether the worn Broken Heart eats this death, and charges it if so.
     *
     * <p>Called from {@code MixinEntityLivingBaseBrokenHeart} at the head of
     * {@code checkTotemDeathProtection}. Deliberately does <b>not</b> touch the player's health:
     * on the FirstAid path the caller ({@code CommonUtils.killPlayer}) restores the body parts
     * itself once this reports a save, and writing health here would fight FirstAid's
     * {@code DataManagerWrapper}.
     *
     * @return true when the player survives and the cost has been applied
     */
    public static boolean tryDeathSave(EntityLivingBase entity, DamageSource source) {
        if (brokenHeart == null
                || !SrpWizCoreConfig.bbCompat.enabled
                || !SrpWizCoreConfig.bbCompat.brokenHeartEnabled) {
            return false;
        }
        if (!(entity instanceof EntityPlayer) || entity.world.isRemote) {
            return false;
        }
        // Same carve-out vanilla gives totems: /kill and the void are not survivable.
        if (source != null && source.canHarmInCreative()) {
            return false;
        }
        final EntityPlayer player = (EntityPlayer) entity;
        // Deliberately the (EntityLivingBase, Object) overload: the (EntityPlayer, Item) one is
        // deprecated in BaublesEX and returns a slot index rather than a boolean.
        if (!BaublesApi.isBaubleEquipped(entity, brokenHeart)) {
            return false;
        }

        final long now = System.currentTimeMillis();
        final int cooldownSeconds = SrpWizCoreConfig.bbCompat.brokenHeartCooldownSeconds;
        if (cooldownSeconds > 0) {
            final Long last = LAST_SAVE.get(player.getUniqueID());
            if (last != null && now - last.longValue() < cooldownSeconds * 1000L) {
                return false;
            }
        }

        final double cost = SrpWizCoreConfig.bbCompat.brokenHeartMaxHealthCost;
        final IAttributeInstance maxHealth =
                player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        final AttributeModifier existing = maxHealth.getModifier(MODIFIER_UUID);
        final double alreadyDrained = existing == null ? 0.0D : existing.getAmount();

        // Refuse rather than grind the player down to nothing. This is what makes the trinket
        // run out, and it is checked against the value the save would leave behind.
        if (player.getMaxHealth() - cost < SrpWizCoreConfig.bbCompat.brokenHeartMinMaxHealth) {
            return false;
        }

        if (existing != null) {
            maxHealth.removeModifier(existing);
        }
        if (cost > 0.0D) {
            // Operation 0 = flat addition; the amount is negative, so the modifiers accumulate.
            maxHealth.applyModifier(new AttributeModifier(
                    MODIFIER_UUID, MODIFIER_NAME, alreadyDrained - cost, 0));
        }

        LAST_SAVE.put(player.getUniqueID(), Long.valueOf(now));

        player.world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_IRONGOLEM_HURT, SoundCategory.PLAYERS, 0.7F,
                0.8F + (player.world.rand.nextFloat() - player.world.rand.nextFloat()) * 0.1F);

        if (SrpWizCoreConfig.bbCompat.brokenHeartLogSaves) {
            SrpWizCore.LOGGER.info("[srpwizcore] bbCompat: Broken Heart saved {} from {} "
                    + "(-{} max health, {} left)", player.getName(),
                    source == null ? "unknown" : source.damageType, cost, player.getMaxHealth());
        }
        return true;
    }
}
