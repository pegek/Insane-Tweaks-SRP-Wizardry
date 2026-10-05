package com.spege.reskilltweaks.skills;

import com.spege.reskilltweaks.customwizardrystats.SummonDurationStat;

import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.item.IManaStoringItem;
import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Every trait effect that needs Electroblob's Wizardry: Arcane Mastery, the three schools, the
 * spell-potency half of Archmage, and Meditation. Registered from {@code ReskillTweaks.init} only
 * when {@code ebwizardry} is loaded, and those six traits are left out of the skill tree otherwise
 * ({@link SkillsModule}), so nothing is ever bought that this class would then fail to serve.
 *
 * <p>🚨 This has to be its own class, not a guarded method in {@link EventHandlerSkills}.
 * {@code EventBus.register} calls {@code getMethods()}, which resolves the parameter types of every
 * public method - so one {@code onSpellCastPre(SpellCastEvent.Pre)} anywhere in a handler takes the
 * whole handler down with {@code NoClassDefFoundError} on a pack without Wizardry, including all of
 * its vanilla traits. A {@code Loader.isModLoaded} inside the method never gets a turn.
 *
 * <p>Archmage's PotionCore magic-damage attribute deliberately stayed in {@code EventHandlerSkills}:
 * it names no Wizardry type, and without Wizardry the trait is unregistered, so its check answers
 * {@code false} and the attribute is never applied.
 */
@SuppressWarnings("null")
public class WizardrySkillsHandler {

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!com.spege.reskilltweaks.config.ReskillTweaksConfig.modules.enableSkillsModule)
            return;

        if (!(event.getEntityLiving() instanceof EntityPlayer))
            return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote)
            return;

        // The NBT keys keep their insanetweaks_ prefix: they sit in saved player data, and a rename
        // would only reset everyone's idle counter for no gain.
        // MEDITATION - Idle Tracker via volatile NBT
        NBTTagCompound nbt = player.getEntityData();
        double prevX = nbt.getDouble("insanetweaks_meditation_x");
        double prevZ = nbt.getDouble("insanetweaks_meditation_z");
        double dx = player.posX - prevX;
        double dz = player.posZ - prevZ;

        nbt.setDouble("insanetweaks_meditation_x", player.posX);
        nbt.setDouble("insanetweaks_meditation_z", player.posZ);

        boolean isMoving = (dx * dx + dz * dz) > 0.0001;
        int currentIdle = nbt.getInteger("insanetweaks_meditation_ticks");

        if (isMoving) {
            currentIdle = 0;
        } else {
            currentIdle++;
        }
        nbt.setInteger("insanetweaks_meditation_ticks", currentIdle);

        if (player.ticksExisted % 20 == 0) {
            if (currentIdle >= 20 && TraitHandle.MEDITATION.has(player)) {
                double amount = com.spege.reskilltweaks.config.ReskillTweaksConfig
                        .traits.meditationManaPerSecond;

                // With ManaCore installed, item mana is a number nothing spends any more -
                // spells are paid from the player's pool - so topping up armour and the
                // off-hand would leave this trait doing visibly nothing. Fall back to the
                // original behaviour only when the bridge reports it could not deliver, so
                // this mod keeps working on its own exactly as before.
                if (!com.spege.reskilltweaks.util.ManaCoreBridge.addMana(player, amount)) {
                    int itemAmount = (int) Math.round(amount);
                    for (ItemStack stack : player.getArmorInventoryList()) {
                        if (!stack.isEmpty() && stack.getItem() instanceof ItemWizardArmour) {
                            ((IManaStoringItem) stack.getItem()).rechargeMana(stack, itemAmount);
                        }
                    }
                    ItemStack offhand = player.getHeldItemOffhand();
                    if (!offhand.isEmpty() && offhand.getItem() instanceof IManaStoringItem) {
                        ((IManaStoringItem) offhand.getItem()).rechargeMana(offhand, itemAmount);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onSpellCastPre(SpellCastEvent.Pre event) {
        if (!com.spege.reskilltweaks.config.ReskillTweaksConfig.modules.enableSkillsModule)
            return;
        if (!(event.getCaster() instanceof EntityPlayer))
            return;
        EntityPlayer player = (EntityPlayer) event.getCaster();
        if (player.world.isRemote)
            return;

        // Arcane Mastery (10% Cost Reduction)
        if (TraitHandle.ARCANE_MASTERY.has(player)) {
            // Legacy syntax used to be event.getModifiers().set("cost", ...).
            // Keep the note here so future edits remember why we now use the native constant.
            float currentCost = event.getModifiers().get(SpellModifiers.COST);
            event.getModifiers().set(SpellModifiers.COST, Math.max(0.05f, currentCost * 0.90f), false);
        }

        Spell spell = event.getSpell();
        if (spell == null)
            return;

        electroblob.wizardry.constants.SpellType type = spell.getType();

        // School of Conjuration - TESTING
        // Moved from Post to Pre so summon-related modifiers are applied before
        // SpellMinion reads them during minion creation.
        if (TraitHandle.SCHOOL_OF_CONJURATION.has(player)) {
            if (type == electroblob.wizardry.constants.SpellType.MINION
                    || type == electroblob.wizardry.constants.SpellType.CONSTRUCT) {
                SummonDurationStat.applyTestModifier(event);
            }
        }

        // Archmage
        if (TraitHandle.ARCHMAGE.has(player)) {
            if (net.minecraftforge.fml.common.Loader.isModLoaded("potioncore")) {
                event.getModifiers().set(SpellModifiers.POTENCY,
                        event.getModifiers().get(SpellModifiers.POTENCY) * 1.05f, false);
            } else {
                event.getModifiers().set(SpellModifiers.POTENCY,
                        event.getModifiers().get(SpellModifiers.POTENCY) * 1.15f, false);
            }
        }

        // School of Alteration
        if (TraitHandle.SCHOOL_OF_ALTERATION.has(player)) {
            if (type == electroblob.wizardry.constants.SpellType.BUFF
                    || type == electroblob.wizardry.constants.SpellType.ALTERATION) {
                event.getModifiers().set("duration",
                        event.getModifiers().get("duration") * 1.15f, false);
            }
        }

        /*
         * TESTING NOTE:
         * Old School of Conjuration implementation was executed in Post.
         * We are keeping this block commented for reference while testing the new
         * Pre-based implementation, because summon modifiers need to be applied
         * before SpellMinion consumes them during minion creation.
         *
         * // School of Conjuration
         * if (TraitBase.hasTrait(player, "reskillable:magic",
         * "compatskills:school_of_conjuration")) {
         *     if (type == electroblob.wizardry.constants.SpellType.MINION
         *             || type == electroblob.wizardry.constants.SpellType.CONSTRUCT) {
         *         event.getModifiers().set("duration",
         *                 event.getModifiers().get("duration") * 1.20f, false);
         *     }
         * }
         */

        // School of Destruction
        if (TraitHandle.SCHOOL_OF_DESTRUCTION.has(player)) {
            if (type == electroblob.wizardry.constants.SpellType.ATTACK
                    || type == electroblob.wizardry.constants.SpellType.PROJECTILE) {
                event.getModifiers().set(SpellModifiers.POTENCY,
                        event.getModifiers().get(SpellModifiers.POTENCY) * 1.10f, false);
            }
        }
    }
}
