package com.spege.insanetweaks.items.spellblade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.spege.insanetweaks.util.AdaptationUpgradeHelper;
import com.spege.insanetweaks.util.SoManyEnchantmentsCompat;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import com.google.common.collect.Multimap;
import javax.annotation.Nonnull;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.Arrays;

import com.spege.insanetweaks.api.ITweaksPropertyHolder;
import com.spege.insanetweaks.api.AdvPropertyRegistry;

import com.windanesz.ancientspellcraft.item.ItemBattlemageSword;
import electroblob.wizardry.constants.Tier;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;

import com.oblivioussp.spartanweaponry.api.IWeaponPropertyContainer;
import com.oblivioussp.spartanweaponry.api.ToolMaterialEx;
import com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponProperty;
import com.oblivioussp.spartanweaponry.api.weaponproperty.IPropertyCallback;
import com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponPropertyWithCallback;
import com.oblivioussp.spartanweaponry.init.ModelRenderRegistry;

/**
 * fucking piece of shit doest work
 * Abstract bridge: connects ItemBattlemageSword (AncientSpellcraft) with
 * IWeaponPropertyContainer (SpartanWeaponry). Handles weapon properties,
 * kill-based spell synergy and model registration.
 *
 * Armour synergy checks for a full set of SentientWarlockArmorItem or
 * LivingWarlockArmorItem.
 */
public abstract class BridgeSpellblade extends ItemBattlemageSword
        implements IWeaponPropertyContainer<net.minecraft.item.Item>, ITweaksPropertyHolder {

    protected final List<WeaponProperty> swProperties = new ArrayList<>();
    protected String swModelPath = null;
    protected String bridgeName;
    protected String bridgeModId;
    protected ToolMaterialEx bridgeMaterial = null;
    protected static final int ARCANE_ADAPTATION_LEVEL = 1;
    private static final boolean HAS_POTIONCORE = net.minecraftforge.fml.common.Loader.isModLoaded("potioncore");

    @SuppressWarnings("null")
    public BridgeSpellblade(String name, String modId, Tier tier, int maxUpgrades) {
        super(tier, maxUpgrades);
        this.bridgeName = name;
        this.bridgeModId = modId;
        this.setRegistryName(new ResourceLocation(modId, name));
        this.setUnlocalizedName(name);
    }

    /**
     * Mana capacity, read from config at call time - once a storage upgrade has been socketed.
     *
     * <p>🚨 {@code ItemBattlemageSword.hasManaStorage(stack)} - true only once
     * {@code WandHelper.getUpgradeLevel(stack, WizardryItems.storage_upgrade) > 0} - is Ancient
     * Spellcraft's own design, not an accident: a battlemage sword carries no mana pool at all
     * until the player sockets a storage upgrade into it, unlike this mod's wands. Both
     * {@code LivingSpellblade} and {@code SentientSpellblade} ask that exact question themselves
     * ({@code innateManaAvailable = hasManaStorage(stack) && !isManaEmpty(stack)}) to decide the
     * {@code melee_upgrade} damage bonus, the out-of-mana penalty, and whether Runeword Fury runs.
     * An earlier version of this override answered with a config-driven pool regardless of that
     * gate, which made a fresh blade read as "has mana" for casting while {@code hasManaStorage}
     * was still false - so it took the out-of-mana melee penalty and never ran Runeword Fury
     * despite a full pool. Keep the gate: everything downstream of {@code hasManaStorage} depends
     * on this method agreeing with it.
     *
     * <p>Once the gate is open, the base is resolved from config by registry name and scaled the
     * same way {@link com.spege.insanetweaks.items.wand.BaseCustomWandItem#getMaxDamage(ItemStack)}
     * scales the two wands, rather than reading the Java field default {@code setMaxDamage} used to
     * set in the constructor.
     *
     * <p>We cannot set that base from config in the constructor for the identical reason
     * {@code BaseCustomWandItem} documents: {@code ModItems} is a {@code @Mod.EventBusSubscriber},
     * so its {@code <clinit>} can run before Forge's first {@code ConfigManager.sync}, and a
     * {@code setMaxDamage(config)} there would silently capture the Java field default instead of
     * the value in the file.
     *
     * <p>Scaling factor and rounding: Ancient Spellcraft hardcodes {@code 0.15f} per storage level
     * and rounds ({@code + 0.5f} before truncating) in its own {@code getMaxDamage}. EBW's
     * {@code Constants.STORAGE_INCREASE_PER_LEVEL} is a config-adjustable field, not a compile-time
     * constant, but its shipped default - read out of {@code Settings}'s own initialiser in the EBW
     * jar - is the identical {@code 0.15f}, and {@code BaseCustomWandItem} already scales the two
     * wands by that constant. Using it here too, with the same {@code + 0.5F} rounding, keeps a
     * Spellblade's storage-upgrade progression consistent with the rest of this mod's gear instead
     * of splitting it onto AS's separately-configured number for one weapon pair alone.
     */
    @Override
    public int getMaxDamage(ItemStack stack) {
        if (!ItemBattlemageSword.hasManaStorage(stack)) {
            return super.getMaxDamage(stack);
        }
        int base = this.getBaseManaCapacity();
        if (base <= 0) {
            return super.getMaxDamage(stack);
        }
        int storage = electroblob.wizardry.util.WandHelper.getUpgradeLevel(
                stack, electroblob.wizardry.registry.WizardryItems.storage_upgrade);
        return (int) (base * (1.0F + electroblob.wizardry.constants.Constants.STORAGE_INCREASE_PER_LEVEL * storage) + 0.5F);
    }

    /** Zero means "not one of ours" - fall back to whatever ItemBattlemageSword says. */
    private int getBaseManaCapacity() {
        ResourceLocation reg = this.getRegistryName();
        if (reg != null) {
            if ("living_spellblade".equals(reg.getResourcePath())) {
                return com.spege.insanetweaks.config.ModConfig.gear.spellblades.livingSpellbladeManaCapacity;
            }
            if ("sentient_spellblade".equals(reg.getResourcePath())) {
                return com.spege.insanetweaks.config.ModConfig.gear.spellblades.sentientSpellbladeManaCapacity;
            }
        }
        return 0;
    }

    /**
     * Insurance against a config lowered under a blade that already has more mana spent than the
     * new capacity allows. Mana is stored as {@code capacity - damage} with nothing clamping the
     * result, so a blade charged above a newly lowered ceiling computes to negative mana - but
     * {@code isManaEmpty} (an exact {@code == 0} check) does not recognise that as empty, so the
     * blade would keep its melee bonuses and refuse every spell instead of just being empty.
     * Mirrors {@code BaseCustomWandItem.onUpdate}, which carries the identical clamp for the two
     * wands. Do not remove this.
     */
    @Override
    public void onUpdate(@Nonnull ItemStack stack, @Nonnull World world, @Nonnull Entity entity, int itemSlot,
            boolean isSelected) {
        super.onUpdate(stack, world, entity, itemSlot, isSelected);
        if (this.getMana(stack) < 0) {
            this.setMana(stack, 0);
        }
    }

    // ------------------------------------------------------------------
    // IWeaponPropertyContainer Implementation
    // ------------------------------------------------------------------

    @Override
    @Nonnull
    public net.minecraft.item.Item addWeaponProperty(WeaponProperty prop) {
        if (prop != null && !swProperties.contains(prop)) {
            swProperties.add(prop);
        }
        return this;
    }

    /** Fluent helper Ereturns BridgeSpellblade for chaining during registration. */
    public BridgeSpellblade addBridgeProperty(WeaponProperty prop) {
        addWeaponProperty(prop);
        return this;
    }

    @Override
    public boolean hasWeaponProperty(WeaponProperty prop) {
        return swProperties.contains(prop);
    }

    @Override
    public WeaponProperty getFirstWeaponPropertyWithType(String type) {
        for (WeaponProperty prop : swProperties) {
            if (type.equals(prop.getType())) {
                return prop;
            }
        }
        return null;
    }

    @Override
    public List<WeaponProperty> getAllWeaponPropertiesWithType(String type) {
        List<WeaponProperty> result = new ArrayList<>();
        for (WeaponProperty prop : swProperties) {
            if (type.equals(prop.getType())) {
                result.add(prop);
            }
        }
        return result;
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public List<WeaponProperty> getAllWeaponProperties() {
        return Collections.unmodifiableList(swProperties);
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public ToolMaterialEx getMaterialEx() {
        if (bridgeMaterial == null) {
            try {
                WeaponProperty[] propArray = swProperties.toArray(new WeaponProperty[0]);
                bridgeMaterial = new ToolMaterialEx(
                        bridgeName, "$nothing", bridgeModId, -1, -1, 4000, 22, 1.0f, getBaseAttackDamage(), -1,
                        propArray);
                if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo)
                    System.out.println("[BridgeSpellblade] getMaterialEx built with " + propArray.length
                            + " properties for " + bridgeModId + ":" + bridgeName);
            } catch (Throwable t) {
                if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo)
                    System.out.println("[BridgeSpellblade] getMaterialEx with props failed (" + t.getMessage()
                            + ") -- simple fallback");
                bridgeMaterial = new ToolMaterialEx(
                        bridgeName, "$nothing", bridgeModId, -1, -1, 4000, 22, 1.0f, getBaseAttackDamage(), -1);
            }
        }
        return bridgeMaterial;
    }

    @Override
    public float getDirectAttackDamage() {
        return getBaseAttackDamage();
    }

    public float getBaseAttackDamage() {
        return 20.0f;
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public Multimap<String, net.minecraft.entity.ai.attributes.AttributeModifier> getAttributeModifiers(
            @Nonnull EntityEquipmentSlot slot, @Nonnull ItemStack stack) {
        Multimap<String, net.minecraft.entity.ai.attributes.AttributeModifier> multimap = com.google.common.collect.HashMultimap
                .create();
        multimap.putAll(super.getAttributeModifiers(slot, stack));
        if (slot == EntityEquipmentSlot.MAINHAND) {
            SoManyEnchantmentsCompat.addAttackSpeedModifiers(stack, multimap);

            // Magically Adapted - magic damage modifier for PotionCore
            // Only activates if PotionCore is actually loaded, preventing NPEs on the
            // AttributeMap
            if (HAS_POTIONCORE) {
                ResourceLocation regLoc = this.getRegistryName();
                if (regLoc != null) {
                    String regName = regLoc.toString();
                    double magicDmg = 0;

                    if ("insanetweaks:sentient_spellblade".equals(regName)) {
                        magicDmg = 0.10D; // 10% bonus for Sentient
                    } else if ("insanetweaks:living_spellblade".equals(regName)) {
                        int kills = 0;
                        if (stack.hasTagCompound()) {
                            NBTTagCompound nbt = stack.getTagCompound();
                            if (nbt != null && nbt.hasKey("SentientKills")) {
                                kills = nbt.getInteger("SentientKills");
                            }
                        }
                        int magicBonus = Math.max(1, Math.min(10, (kills * 10) / 900));
                        magicDmg = magicBonus / 100.0D;
                    }

                    magicDmg *= com.spege.insanetweaks.config.ModConfig.gear.spellblades.magicDamageMultiplier;

                    if (magicDmg > 0) {
                        multimap.put(
                                "potioncore.magicDamage",
                                new net.minecraft.entity.ai.attributes.AttributeModifier(
                                        java.util.UUID.fromString("6C2F3E8A-5182-421A-B01B-BCCE9786A000"),
                                        "Magically Adapted", magicDmg, 0));
                    }
                }
            }
        }

        return multimap;
    }

    // ------------------------------------------------------------------
    // Event Callbacks & Modifiers
    // ------------------------------------------------------------------

    @Override
    public boolean hitEntity(@Nonnull ItemStack stack, @Nonnull EntityLivingBase target, @Nonnull EntityLivingBase attacker) {
        boolean result = super.hitEntity(stack, target, attacker);
        ToolMaterialEx mat = getMaterialEx();

        for (WeaponProperty prop : swProperties) {
            if (prop instanceof WeaponPropertyWithCallback) {
                IPropertyCallback cb = ((WeaponPropertyWithCallback) prop).getCallback();
                if (cb != null) {
                    try {
                        cb.onHitEntity(mat, stack, target, attacker, (net.minecraft.entity.Entity) null);
                    } catch (Throwable t) {
                        if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo)
                            System.out.println(
                                    "[BridgeSpellblade] onHit callback error for " + prop + ": " + t.getMessage());
                    }
                }
            }
        }

        return result;
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public SpellModifiers calculateModifiers(@Nonnull ItemStack stack, @Nonnull EntityPlayer player,
            @Nonnull Spell spell) {
        
        int warlockCount = 0;
        int battlemageCount = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack piece = player.inventory.armorInventory.get(i);
            if (!piece.isEmpty()) {
                if (piece.getItem() instanceof com.spege.insanetweaks.items.armor.SentientWarlockArmorItem ||
                    piece.getItem() instanceof com.spege.insanetweaks.items.armor.LivingWarlockArmorItem) {
                    warlockCount++;
                } else if (piece.getItem() instanceof com.spege.insanetweaks.items.armor.SentientBattlemageArmorItem ||
                           piece.getItem() instanceof com.spege.insanetweaks.items.armor.LivingBattlemageArmorItem) {
                    battlemageCount++;
                }
            }
        }
        
        boolean hasSynergy = (warlockCount == 4 || battlemageCount == 4);
        ItemStack[] originalArmor = new ItemStack[4];
        boolean tricked = false;
        
        // Artificial native synergy for Warlock armor
        if (warlockCount == 4) {
            tricked = true;
            for (int i = 0; i < 4; i++) {
                originalArmor[i] = player.inventory.armorInventory.get(i);
                ItemStack dummy = null;
                if (i == 0) dummy = new ItemStack(com.spege.insanetweaks.init.ModItems.LIVING_BATTLEMAGE_BOOTS);
                else if (i == 1) dummy = new ItemStack(com.spege.insanetweaks.init.ModItems.LIVING_BATTLEMAGE_LEGGINGS);
                else if (i == 2) dummy = new ItemStack(com.spege.insanetweaks.init.ModItems.LIVING_BATTLEMAGE_CHESTPLATE);
                else if (i == 3) dummy = new ItemStack(com.spege.insanetweaks.init.ModItems.LIVING_BATTLEMAGE_HELMET);
                
                player.inventory.armorInventory.set(i, dummy);
            }
        }

        SpellModifiers modifiers;
        try {
            modifiers = super.calculateModifiers(stack, player, spell);
        } finally {
            if (tricked) {
                for (int i = 0; i < 4; i++) {
                    player.inventory.armorInventory.set(i, originalArmor[i]);
                }
            }
        }

        int killCount = 0;
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt != null && nbt.hasKey("SentientKills")) {
                killCount = nbt.getInteger("SentientKills");
            }
        }

        int bonusPercent;
        ResourceLocation regLoc = stack.getItem().getRegistryName();
        if (regLoc != null && "insanetweaks:sentient_spellblade".equals(regLoc.toString())) {
            bonusPercent = 70;
        } else {
            bonusPercent = Math.min(70, (killCount * 70) / 900);
        }

        if (bonusPercent > 0) {
            if (hasSynergy) {
                // Config scales the computed bonus rather than replacing it, so a part-evolved
                // Living Spellblade keeps its place on the kill-count curve.
                com.spege.insanetweaks.config.categories.GearCategory.Spellblades cfg =
                        com.spege.insanetweaks.config.ModConfig.gear.spellblades;
                float bonus = (bonusPercent / 100.0f) * (float) cfg.synergyPotencyMultiplier;
                float multiplier = 1.0f + bonus;

                // Nerf runewords: reduce our custom synergy bonus by half specifically for
                // Runewords
                // This prevents game-breaking overlap with their innate 1.20x * 1.20x
                // BATTLEMAGE class bonuses
                if (spell.getRegistryName() != null
                        && "ancientspellcraft".equals(spell.getRegistryName().getResourceDomain())
                        && spell.getRegistryName().getResourcePath().contains("runeword")) {
                    multiplier = 1.0f + (bonus * (float) cfg.runewordSynergyMultiplier);
                }

                modifiers.set(SpellModifiers.POTENCY, multiplier, true);
            }
        }

        return modifiers;
    }

    protected int getDefaultAdaptationLevel() {
        return ARCANE_ADAPTATION_LEVEL;
    }

    public int getArcaneAdaptationLevel(ItemStack stack) {
        return Math.min(AdaptationUpgradeHelper.MAX_ADAPTATION_LEVEL, this.getDefaultAdaptationLevel()
                + AdaptationUpgradeHelper.getAppliedAdaptationUpgradeLevel(stack));
    }

    // ------------------------------------------------------------------
    // Model Registration
    // ------------------------------------------------------------------

    @SideOnly(Side.CLIENT)
    @SuppressWarnings("null")
    public void registerModel() {
        if (FMLCommonHandler.instance().getSide() != Side.CLIENT)
            return;

        ResourceLocation regName = this.getRegistryName();
        if (regName == null)
            return;

        // 1. Always register standard model (inventory icon)
        ModelLoader.setCustomModelResourceLocation(this, 0, new ModelResourceLocation(regName, "inventory"));
        if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo)
            System.out.println("[BridgeSpellblade] Registered standard model for: " + regName);

        // 2. Register Spartan Weaponry model if path is provided
        if (swModelPath != null) {
            try {
                String modId = regName.getResourceDomain();
                ModelRenderRegistry.addItemToRegistry(this, new ResourceLocation(modId, swModelPath));
                if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo)
                    System.out.println("[BridgeSpellblade] Registered Spartan model: " + modId + ":" + swModelPath);
            } catch (Throwable t) {
                if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo)
                    System.out.println(
                            "[BridgeSpellblade] Spartan ModelRenderRegistry skipped or failed: " + t.getMessage());
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return false;
    }

    @Override
    public List<String> getActiveAdvProperties(ItemStack stack) {
        return Arrays.asList(AdvPropertyRegistry.ASHEN_LEGACY);
    }
}
