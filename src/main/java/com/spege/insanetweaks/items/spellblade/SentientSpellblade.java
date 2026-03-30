package com.spege.insanetweaks.items.spellblade;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.Multimap;
import javax.annotation.Nonnull;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.windanesz.ancientspellcraft.item.ItemBattlemageSword;
import com.windanesz.ancientspellcraft.spell.RunesmithingSpellBase;
import com.windanesz.ancientspellcraft.spell.Runeword;
import com.windanesz.ancientspellcraft.registry.ASSpells;

import electroblob.wizardry.constants.Constants;
import electroblob.wizardry.constants.Tier;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.registry.WizardryAdvancementTriggers;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.WandHelper;
import com.oblivioussp.spartanweaponry.api.WeaponProperties;
import com.existingeevee.swparasites.init.ParasiteSWProperties;

/**
 * Sentient Spellblade  Ethe evolved, higher-tier form of the Living Spellblade.
 * Reached via 1200 kills with the Living Spellblade; synergy potency bonus is capped at 85%.
 * Tooltip is handled by SpellbladeTooltipHandler (client-side event) to avoid duplication.
 */
public class SentientSpellblade extends BridgeSpellblade {

    private boolean _debugLogged = false;

    @SuppressWarnings("null")
    public SentientSpellblade() {
        super("sentient_spellblade", "insanetweaks", Tier.MASTER, 8);
        this.setCreativeTab(CreativeTabs.COMBAT);
        this.setMaxDamage(4000);
        this.swModelPath = "sentient_spellblade";

        // Spartan & Parasite Properties
        this.addBridgeProperty(WeaponProperties.REACH_1)
            .addBridgeProperty(WeaponProperties.SWEEP_DAMAGE_NORMAL)
            .addBridgeProperty(ParasiteSWProperties.BLEEDING_3)
            .addBridgeProperty(ParasiteSWProperties.UNCAPPED)
            .addBridgeProperty(ParasiteSWProperties.HEAVY_1);
    }

    @Override
    @Nonnull
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        return "\u00a7e" + super.getItemStackDisplayName(stack);
    }

    @Override
    public float getBaseAttackDamage() {
        return 20.0f;
    }

    @Override
    public boolean hitEntity(@Nonnull ItemStack stack, @Nonnull net.minecraft.entity.EntityLivingBase target, @Nonnull net.minecraft.entity.EntityLivingBase attacker) {
        boolean result = super.hitEntity(stack, target, attacker);

        int kills = 0;
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt != null && nbt.hasKey("SentientKills")) {
                kills = nbt.getInteger("SentientKills");
            }
        }

        // Easter Egg: Awakened Viral I at 1800 kills
        if (kills >= 1800) {
            com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponProperty viral = ParasiteSWProperties.VIRAL_1;
            if (viral instanceof com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponPropertyWithCallback) {
                com.oblivioussp.spartanweaponry.api.weaponproperty.IPropertyCallback cb = ((com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponPropertyWithCallback) viral).getCallback();
                if (cb != null) {
                    try {
                        cb.onHitEntity(this.getMaterialEx(), stack, target, attacker, null);
                    } catch (Throwable t) {
                        // ignore
                    }
                }
            }
        }

        return result;
    }

    // ==========================================================
    // SPELL SLOT EXPANSION
    // ==========================================================

    @Override
    @SuppressWarnings("null")
    public int getSpellSlotCount(@Nonnull ItemStack stack) {
        return 6 + WandHelper.getUpgradeLevel(stack, WizardryItems.attunement_upgrade);
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public ItemStack applyUpgrade(net.minecraft.entity.player.EntityPlayer player, @Nonnull ItemStack wand, @Nonnull ItemStack upgrade) {
        if (WandHelper.isWandUpgrade(upgrade.getItem())) {
            Item specialUpgrade = upgrade.getItem();
            int maxUpgrades = this.tier.upgradeLimit - 2;

            if (WandHelper.getTotalUpgrades(wand) < maxUpgrades && WandHelper.getUpgradeLevel(wand, specialUpgrade) < Constants.UPGRADE_STACK_LIMIT) {
                int prevMana = this.getMana(wand);
                WandHelper.applyUpgrade(wand, specialUpgrade);

                if (specialUpgrade == WizardryItems.storage_upgrade) {
                    this.setMana(wand, prevMana);
                } else if (specialUpgrade == WizardryItems.attunement_upgrade) {
                    int newSlotCount = 6 + WandHelper.getUpgradeLevel(wand, WizardryItems.attunement_upgrade);
                    Spell[] spells = WandHelper.getSpells(wand);
                    Spell[] newSpells = new Spell[newSlotCount];
                    for (int i = 0; i < newSpells.length; i++) {
                        newSpells[i] = (i < spells.length && spells[i] != null) ? spells[i] : Spells.none;
                    }
                    WandHelper.setSpells(wand, newSpells);

                    int[] cooldowns = WandHelper.getCooldowns(wand);
                    int[] newCooldowns = new int[newSlotCount];
                    if (cooldowns.length > 0) {
                        System.arraycopy(cooldowns, 0, newCooldowns, 0, Math.min(cooldowns.length, newCooldowns.length));
                    }
                    WandHelper.setCooldowns(wand, newCooldowns);
                }

                upgrade.shrink(1);
                if (player != null) {
                    WizardryAdvancementTriggers.special_upgrade.triggerFor(player);
                    if (WandHelper.getTotalUpgrades(wand) == Tier.MASTER.upgradeLimit) {
                        WizardryAdvancementTriggers.max_out_wand.triggerFor(player);
                    }
                }
            }
        }
        return wand;
    }

    @Override
    @SuppressWarnings("null")
    public boolean onApplyButtonPressed(net.minecraft.entity.player.EntityPlayer player, @Nonnull Slot centre, @Nonnull Slot crystals, @Nonnull Slot upgrade, @Nonnull Slot[] spellBooks) {
        boolean changed = false;
        if (upgrade.getHasStack()) {
            ItemStack original = centre.getStack().copy();
            centre.putStack(this.applyUpgrade(player, centre.getStack(), upgrade.getStack()));
            changed = !ItemStack.areItemStacksEqual(centre.getStack(), original);
        }

        Spell[] spells = WandHelper.getSpells(centre.getStack());
        if (spells.length <= 0) {
            spells = new Spell[6];
        }

        for (int i = 0; i < spells.length; i++) {
            if (!spellBooks[i].getStack().isEmpty()) {
                Spell spell = Spell.byMetadata(spellBooks[i].getStack().getItemDamage());
                if (spell.getTier().level <= this.tier.level && spells[i] != spell && spell.isEnabled() && !(spell instanceof RunesmithingSpellBase)) {
                    spells[i] = spell;
                    changed = true;
                }
            }
        }
        WandHelper.setSpells(centre.getStack(), spells);

        if (ItemBattlemageSword.hasManaStorage(centre.getStack()) && !crystals.getStack().isEmpty() && !this.isManaFull(centre.getStack())) {
            int chargeDepleted = this.getManaCapacity(centre.getStack()) - this.getMana(centre.getStack());
            int manaPerItem = Constants.MANA_PER_CRYSTAL;
            if (crystals.getStack().getItem() == WizardryItems.crystal_shard) manaPerItem = Constants.MANA_PER_SHARD;
            if (crystals.getStack().getItem() == WizardryItems.grand_crystal) manaPerItem = Constants.GRAND_CRYSTAL_MANA;

            if (crystals.getStack().getCount() * manaPerItem < chargeDepleted) {
                this.rechargeMana(centre.getStack(), crystals.getStack().getCount() * manaPerItem);
                crystals.decrStackSize(crystals.getStack().getCount());
            } else {
                this.setMana(centre.getStack(), this.getManaCapacity(centre.getStack()));
                crystals.decrStackSize((int) Math.ceil(((double) chargeDepleted) / manaPerItem));
            }
            changed = true;
        }
        return changed;
    }

    // ==========================================================
    // COMBAT STATS  Emana-conditional damage scaling
    // ==========================================================

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        Multimap<String, AttributeModifier> multimap = super.getAttributeModifiers(slot, stack);

        if (slot == EntityEquipmentSlot.MAINHAND) {
            String damageName = SharedMonsterAttributes.ATTACK_DAMAGE.getName();

            if (!this._debugLogged) {
                this._debugLogged = true;
                if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo) {
                    System.out.println("[SentientSpellblade DBG] super.getAttributeModifiers keys: " + multimap.keySet());
                    Collection<AttributeModifier> dmgEntries = multimap.get(damageName);
                    System.out.println("[SentientSpellblade DBG] ATTACK_DAMAGE entries: " + dmgEntries.size());
                }
            }

            Collection<AttributeModifier> currentMods = multimap.removeAll(damageName);

            if (currentMods != null && !currentMods.isEmpty()) {
                AttributeModifier mainMod = null;
                for (AttributeModifier mod : currentMods) {
                    if (mod.getID().equals(Item.ATTACK_DAMAGE_MODIFIER)) {
                        mainMod = mod;
                        break;
                    }
                }
                if (mainMod == null) {
                    mainMod = currentMods.iterator().next();
                }

                if (mainMod != null) {
                    double customAttackDamage = 20.0d;
                    double finalDamage = customAttackDamage;

                    int level = WandHelper.getUpgradeLevel(stack, WizardryItems.melee_upgrade);
                    boolean innateManaAvailable = ItemBattlemageSword.hasManaStorage(stack) && !this.isManaEmpty(stack);
                    boolean anyManaAvailable = stack.hasTagCompound()
                        && stack.getTagCompound().hasKey("mana_available")
                        && stack.getTagCompound().getBoolean("mana_available");

                    if (level > 0 && (innateManaAvailable || anyManaAvailable)) {
                        finalDamage = customAttackDamage + level;
                    } else if (!innateManaAvailable && !anyManaAvailable && stack.hasTagCompound()) {
                        finalDamage = customAttackDamage * 0.5d;
                    }

                    if (anyManaAvailable && stack.hasTagCompound()) {
                        Spell[] spells = WandHelper.getSpells(stack);
                        for (Spell spell : spells) {
                            if (spell instanceof Runeword && ((Runeword) spell).isAffectingAttributes()) {
                                java.util.Map<Runeword, NBTTagCompound> dataMap = ItemBattlemageSword.getTemporaryRunewordData(stack);
                                if (spell == ASSpells.runeword_fury && dataMap != null && dataMap.containsKey(ASSpells.runeword_fury)) {
                                    NBTTagCompound runewordData = dataMap.get(ASSpells.runeword_fury);
                                    if (runewordData != null) {
                                        int charges = runewordData.getInteger("charges");
                                        float modifier = 1.0f + ASSpells.runeword_fury.getProperty("dmg_percent_increase_per_hit").floatValue() * charges;
                                        finalDamage = finalDamage * modifier;
                                    }
                                }
                            }
                        }
                    }

                    multimap.put(damageName, new AttributeModifier(mainMod.getID(), mainMod.getName(), finalDamage, mainMod.getOperation()));
                    for (AttributeModifier mod : currentMods) {
                        if (!mod.getID().equals(mainMod.getID())) {
                            multimap.put(damageName, mod);
                        }
                    }
                }
            }
        }
        return multimap;
    }

    // Tooltip is handled entirely by SpellbladeTooltipHandler to avoid duplication.
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @javax.annotation.Nullable World world, @Nonnull List<String> tooltip, @Nonnull net.minecraft.client.util.ITooltipFlag flag) {
        // Intentionally empty  Eall custom tooltip lines are injected by SpellbladeTooltipHandler.
    }
}
