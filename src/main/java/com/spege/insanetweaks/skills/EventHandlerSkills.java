package com.spege.insanetweaks.skills;

import com.spege.insanetweaks.customwizardrystats.SummonDurationStat;
import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.enchanting.EnchantmentLevelSetEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import codersafterdark.reskillable.api.event.UnlockUnlockableEvent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.item.IManaStoringItem;
import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.spell.Spell;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

@SuppressWarnings("null")
public class EventHandlerSkills {

    // Stałe pod optymalizację traitu Archmage (Testowe wartości)
    private static final java.util.UUID ARCHMAGE_MODIFIER_UUID = java.util.UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0123456789ab");
    // Obrażenia Magiczne: 0.10D = +10% (zmienione z domyślnego +5%)
    private static final net.minecraft.entity.ai.attributes.AttributeModifier ARCHMAGE_MODIFIER = new net.minecraft.entity.ai.attributes.AttributeModifier(ARCHMAGE_MODIFIER_UUID, "Archmage Magic Damage Bonus", 0.10D, 1).setSaved(false);

    // Stałe pod traita Bob the Builder
    private static final java.util.UUID BOB_MODIFIER_UUID = java.util.UUID.fromString("b2c3d4e5-f6a7-4b8c-9d0e-123456789abc");
    // Zwiększenie zasięgu o 2 bloki (+2.0D)
    private static final net.minecraft.entity.ai.attributes.AttributeModifier BOB_MODIFIER = new net.minecraft.entity.ai.attributes.AttributeModifier(BOB_MODIFIER_UUID, "Bob the Builder Reach", 2.0D, 0).setSaved(false);

    // Stałe pod traita Angry Farmer
    private static final java.util.UUID ANGRY_FARMER_DMG_UUID = java.util.UUID.fromString("c3d4e5f6-a7b8-4c9d-0e1f-23456789abcd");
    private static final net.minecraft.entity.ai.attributes.AttributeModifier ANGRY_FARMER_DMG = new net.minecraft.entity.ai.attributes.AttributeModifier(ANGRY_FARMER_DMG_UUID, "Angry Farmer Damage", 5.0D, 0).setSaved(false);
    private static final java.util.UUID ANGRY_FARMER_SPEED_UUID = java.util.UUID.fromString("d4e5f6a7-b8c9-4d0e-1f2a-3456789abcde");
    private static final net.minecraft.entity.ai.attributes.AttributeModifier ANGRY_FARMER_SPEED = new net.minecraft.entity.ai.attributes.AttributeModifier(ANGRY_FARMER_SPEED_UUID, "Angry Farmer Attack Speed", 0.1D, 2).setSaved(false);

    // Stałe pod traita Golden Osmosis Buffed
    private static final java.util.UUID GOLDEN_ARMOR_UUID = java.util.UUID.fromString("e5f6a7b8-c9d0-4e1f-2a34-56789abcdef0");
    private static final java.util.UUID GOLDEN_TOUGHNESS_UUID = java.util.UUID.fromString("f6a7b8c9-d0e1-4f2a-3b45-6789abcdef01");
    private static final java.util.UUID GOLDEN_SPEED_UUID = java.util.UUID.fromString("0a1b2c3d-4e5f-6a7b-8c9d-e0123456789a");
    private static final net.minecraft.entity.ai.attributes.AttributeModifier GOLDEN_SPEED_MOD = new net.minecraft.entity.ai.attributes.AttributeModifier(GOLDEN_SPEED_UUID, "Golden Osmosis Speed", 0.25D, 2).setSaved(false);

    // Removed memory-leaking static maps. Data is stored directly on player NBT
    // securely.

    // Basic Forge Events
    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;
        if (event.getAttackingPlayer() != null) {
            if (TraitBase.hasTrait(event.getAttackingPlayer(), "reskillable:attack", "compatskills:fast_learner")) {
                event.setDroppedExperience((int) (event.getDroppedExperience() * 1.15));
            }
        }
    }

    @SubscribeEvent
    public void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;
        if (!(event.getEntityLiving() instanceof EntityPlayer))
            return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();

        ItemStack item = event.getItem();
        if (item.getItem() instanceof ItemFood) {
            // Przyspieszenie jedzenia o 15%
            if (TraitBase.hasTrait(player, "reskillable:defense", "compatskills:iron_stomach")) {
                int newDuration = (int) (event.getDuration() * 0.85F);
                event.setDuration(newDuration);
            }
        }
    }

    @SubscribeEvent
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;
        if (!(event.getEntityLiving() instanceof EntityPlayer))
            return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote)
            return; // Efekty nakładamy tylko na serwerze

        ItemStack item = event.getItem();
        if (item.getItem() instanceof ItemFood) {
            ItemFood food = (ItemFood) item.getItem();
            if (food.getSaturationModifier(item) > 0) {
                if (TraitBase.hasTrait(player, "reskillable:defense", "compatskills:iron_stomach")) {
                    float healAmount = food.getHealAmount(item);
                    float saturationMod = food.getSaturationModifier(item);
                    
                    // Obliczamy skalę bonusu (ucięta o połowę dla balansu). 
                    // Np. dla steka (Heal: 8, SatMod: 0.8) wyjdzie ~3 ticki potężnego efektu Saturation.
                    int durationTicks = Math.max(1, (int) ((healAmount * saturationMod) / 2.0f));
                    
                    // Nakładamy natywny efekt nasycenia, który wywoła efekt szybkiego leczenia z Vanilli
                    player.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.SATURATION, durationTicks, 0, false, false));
                }
            }
        }
    }

    @SubscribeEvent
    public void onItemFished(ItemFishedEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote)
            return;

        // Double Loot (6% chance)
        if (TraitBase.hasTrait(player, "reskillable:gathering", "compatskills:double_loot")) {
            if (player.world.rand.nextInt(100) < 6) {
                List<ItemStack> additionalDrops = new ArrayList<>();
                for (ItemStack drop : event.getDrops()) {
                    if (!drop.isEmpty()) {
                        additionalDrops.add(drop.copy());
                    }
                }
                event.getDrops().addAll(additionalDrops);
            }
        }

        // Enchant Fishing (0.5% chance)
        if (TraitBase.hasTrait(player, "reskillable:gathering", "compatskills:enchant_fishing")) {
            if (player.world.rand.nextFloat() < 0.005f) {
                ItemStack book = generateRandomEnchantedBook(player);
                if (book != null && !book.isEmpty()) {
                    net.minecraft.entity.projectile.EntityFishHook hook = event.getHookEntity();
                    double spawnX = hook != null ? hook.posX : player.posX;
                    double spawnY = hook != null ? hook.posY : player.posY + 0.5;
                    double spawnZ = hook != null ? hook.posZ : player.posZ;
                    
                    EntityItem entityItem = new EntityItem(player.world, spawnX, spawnY, spawnZ, book);
                    entityItem.setPickupDelay(0);
                    
                    if (hook != null) {
                        double d0 = player.posX - spawnX;
                        double d1 = player.posY - spawnY;
                        double d2 = player.posZ - spawnZ;
                        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                        
                        entityItem.motionX = d0 * 0.1D;
                        entityItem.motionY = d1 * 0.1D + Math.sqrt(d3) * 0.08D;
                        entityItem.motionZ = d2 * 0.1D;
                    } else {
                        entityItem.motionX = (player.world.rand.nextDouble() - 0.5) * 0.15;
                        entityItem.motionY = 0.25;
                        entityItem.motionZ = (player.world.rand.nextDouble() - 0.5) * 0.15;
                    }
                    
                    player.world.spawnEntity(entityItem);
                }
            }
        }
    }

    private ItemStack generateRandomEnchantedBook(EntityPlayer player) {
        List<String> validEnchants;

        if (net.minecraftforge.fml.common.Loader.isModLoaded("somanyenchantments")) {
            validEnchants = Arrays.asList(
                "somanyenchantments:advancedbaneofarthropods", "somanyenchantments:advancedblastprotection",
                "somanyenchantments:advancedefficiency", "somanyenchantments:advancedfeatherfalling",
                "somanyenchantments:advancedfireaspect", "somanyenchantments:advancedfireprotection",
                "somanyenchantments:advancedflame", "somanyenchantments:advancedknockback",
                "somanyenchantments:advancedlooting", "somanyenchantments:advancedluckofthesea",
                "somanyenchantments:advancedlure", "somanyenchantments:advancedmending",
                "somanyenchantments:advancedpower", "somanyenchantments:advancedprojectileprotection",
                "somanyenchantments:advancedprotection", "somanyenchantments:advancedpunch",
                "somanyenchantments:advancedsharpness", "somanyenchantments:advancedsmite",
                "somanyenchantments:advancedthorns",
                "somanyenchantments:supremebaneofarthropods", "somanyenchantments:supremefireaspect",
                "somanyenchantments:supremeflame", "somanyenchantments:supremeprotection",
                "somanyenchantments:supremesharpness", "somanyenchantments:supremesmite",
                "somanyenchantments:ancientswordmastery", "somanyenchantments:ancientsealedcurses",
                "somanyenchantments:pandorascurse", "minecraft:mending", "minecraft:frost_walker"
            );
        } else {
            validEnchants = Arrays.asList(
                "minecraft:mending", "minecraft:sharpness", "minecraft:looting", 
                "minecraft:fortune", "minecraft:protection", "minecraft:power"
            );
        }

        String chosenId = validEnchants.get(player.world.rand.nextInt(validEnchants.size()));
        Enchantment enchant = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(chosenId));
        
        if (enchant == null) return ItemStack.EMPTY;

        // Native 1.12.2 method for enchanted books (safely handles NBT)
        ItemStack book = new ItemStack(net.minecraft.init.Items.ENCHANTED_BOOK);
        net.minecraft.enchantment.EnchantmentData enchantData = new net.minecraft.enchantment.EnchantmentData(enchant, 1);
        net.minecraft.item.ItemEnchantedBook.addEnchantment(book, enchantData);

        return book;
    }

    @SubscribeEvent
    public void onBlockHarvest(HarvestDropsEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;
        if (event.getHarvester() != null && !event.getWorld().isRemote) {
            if (TraitBase.hasTrait(event.getHarvester(), "reskillable:mining", "compatskills:astral_prospector")) {
                Block block = event.getState().getBlock();
                ResourceLocation regName = block.getRegistryName();
                if (regName != null && regName.getResourcePath().toLowerCase().contains("ore")) {
                    if (event.getWorld().rand.nextInt(100) < 10) {
                        List<ItemStack> additionalDrops = new ArrayList<>();
                        for (ItemStack drop : event.getDrops()) {
                            additionalDrops.add(drop.copy());
                        }
                        event.getDrops().addAll(additionalDrops);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onEnchantmentLevelSet(EnchantmentLevelSetEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;
        if (event.getWorld().isRemote)
            return;

        ItemStack itemInTable = event.getItem();
        // Bezpieczeństwo - unikamy zaklinania przedmiotów, które się do tego nie nadają
        if (itemInTable.isEmpty() || itemInTable.getItem().getItemEnchantability(itemInTable) <= 0) 
            return;

        AxisAlignedBB searchBox = new AxisAlignedBB(event.getPos()).grow(5.0);
        List<EntityPlayer> players = event.getWorld().getEntitiesWithinAABB(EntityPlayer.class, searchBox);

        for (EntityPlayer player : players) {
            if (TraitBase.hasTrait(player, "reskillable:building", "compatskills:supreme_enchanter")) {
                
                // Wirtualne Enchantability (+10). Wzór Vanilli to średnio +1 mocy za +2 enchantability.
                int virtualEnchantabilityBonus = 10; 
                int simulatedPower = virtualEnchantabilityBonus / 2;

                // Aplikujemy bonus wynikający wyłącznie z wirtualnej podatności przedmiotu
                event.setLevel(event.getLevel() + simulatedPower);
                break;
            }
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;

        if (!(event.getEntityLiving() instanceof EntityPlayer))
            return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();

        if (!player.world.isRemote) {
            // ARCANE MASTERY - przeniesiono do onSpellCastPre

            // BOB THE BUILDER - +2 Block Reach Distance
            if (player.ticksExisted % 5 == 0) {
                net.minecraft.entity.ai.attributes.IAttributeInstance reachAttr = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE);
                if (reachAttr != null) {
                    boolean hasBob = TraitBase.hasTrait(player, "reskillable:building", "compatskills:bob_the_builder");
                    boolean holdingBlock = false;
                    
                    if (hasBob) {
                        ItemStack mainhand = player.getHeldItemMainhand();
                        if (!mainhand.isEmpty() && mainhand.getItem() instanceof net.minecraft.item.ItemBlock) {
                            holdingBlock = true;
                        }
                    }
                    
                    boolean hasModifier = reachAttr.hasModifier(BOB_MODIFIER);
                    
                    if (holdingBlock && !hasModifier) {
                        reachAttr.applyModifier(BOB_MODIFIER);
                    } else if (!holdingBlock && hasModifier) {
                        reachAttr.removeModifier(BOB_MODIFIER);
                    }
                }
            }

            // ANGRY FARMER - +5 Flat Dmg, +10% Attack Speed with farming tools
            if (player.ticksExisted % 5 == 0) {
                net.minecraft.entity.ai.attributes.IAttributeInstance dmgAttr = player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.ATTACK_DAMAGE);
                net.minecraft.entity.ai.attributes.IAttributeInstance speedAttr = player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.ATTACK_SPEED);
                
                if (dmgAttr != null && speedAttr != null) {
                    boolean hasFarmer = TraitBase.hasTrait(player, "reskillable:farming", "compatskills:angry_farmer");
                    boolean holdingFarmTool = false;
                    
                    if (hasFarmer) {
                        ItemStack mainhand = player.getHeldItemMainhand();
                        if (!mainhand.isEmpty()) {
                            net.minecraft.item.Item item = mainhand.getItem();
                            if (item instanceof net.minecraft.item.ItemHoe || item instanceof net.minecraft.item.ItemShears) {
                                holdingFarmTool = true;
                            } else {
                                ResourceLocation regName = item.getRegistryName();
                                if (regName != null) {
                                    String name = regName.getResourcePath().toLowerCase();
                                    if (name.contains("hoe") || name.contains("shears") || name.contains("scythe")) {
                                        holdingFarmTool = true;
                                    }
                                }
                            }
                        }
                    }
                    
                    boolean hasDmgMod = dmgAttr.hasModifier(ANGRY_FARMER_DMG);
                    boolean hasSpeedMod = speedAttr.hasModifier(ANGRY_FARMER_SPEED);
                    
                    if (holdingFarmTool) {
                        if (!hasDmgMod) dmgAttr.applyModifier(ANGRY_FARMER_DMG);
                        if (!hasSpeedMod) speedAttr.applyModifier(ANGRY_FARMER_SPEED);
                    } else {
                        if (hasDmgMod) dmgAttr.removeModifier(ANGRY_FARMER_DMG);
                        if (hasSpeedMod) speedAttr.removeModifier(ANGRY_FARMER_SPEED);
                    }
                }
            }

            // GOLDEN OSMOSIS - Passive Buffs for Gold Equipment
            if (player.ticksExisted % 5 == 0) {
                boolean hasGoldenOsmosis = TraitBase.hasTrait(player, "reskillable:magic", "reskillable:golden_osmosis");

                // 1. Attack Speed Buff for Golden Tools/Weapons (+25%)
                net.minecraft.entity.ai.attributes.IAttributeInstance speedAttr = player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.ATTACK_SPEED);
                if (speedAttr != null) {
                    boolean holdingGoldenTool = false;
                    if (hasGoldenOsmosis) {
                        if (isGoldenWeaponOrTool(player.getHeldItemMainhand())) {
                            holdingGoldenTool = true;
                        }
                    }
                    
                    boolean hasSpeedMod = speedAttr.hasModifier(GOLDEN_SPEED_MOD);
                    if (holdingGoldenTool && !hasSpeedMod) {
                        speedAttr.applyModifier(GOLDEN_SPEED_MOD);
                    } else if (!holdingGoldenTool && hasSpeedMod) {
                        speedAttr.removeModifier(GOLDEN_SPEED_MOD);
                    }
                }

                // 2. Armor and Toughness Buffs (+1 Armor, +0.5 Toughness per golden armor piece)
                net.minecraft.entity.ai.attributes.IAttributeInstance armorAttr = player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.ARMOR);
                net.minecraft.entity.ai.attributes.IAttributeInstance toughnessAttr = player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.ARMOR_TOUGHNESS);
                
                if (armorAttr != null && toughnessAttr != null) {
                    int goldenArmorPieces = 0;
                    if (hasGoldenOsmosis) {
                        for (ItemStack armorStack : player.getArmorInventoryList()) {
                            if (isGoldenArmor(armorStack)) {
                                goldenArmorPieces++;
                            }
                        }
                    }

                    net.minecraft.entity.ai.attributes.AttributeModifier existingArmorMod = armorAttr.getModifier(GOLDEN_ARMOR_UUID);
                    net.minecraft.entity.ai.attributes.AttributeModifier existingToughnessMod = toughnessAttr.getModifier(GOLDEN_TOUGHNESS_UUID);
                    
                    double expectedArmorBonus = goldenArmorPieces * 1.0D;
                    double expectedToughnessBonus = goldenArmorPieces * 0.5D;

                    if (existingArmorMod != null && existingArmorMod.getAmount() != expectedArmorBonus) {
                        armorAttr.removeModifier(existingArmorMod);
                        existingArmorMod = null;
                    }
                    if (existingToughnessMod != null && existingToughnessMod.getAmount() != expectedToughnessBonus) {
                        toughnessAttr.removeModifier(existingToughnessMod);
                        existingToughnessMod = null;
                    }

                    if (goldenArmorPieces > 0) {
                        if (existingArmorMod == null) {
                            armorAttr.applyModifier(new net.minecraft.entity.ai.attributes.AttributeModifier(GOLDEN_ARMOR_UUID, "Golden Osmosis Armor", expectedArmorBonus, 0).setSaved(false));
                        }
                        if (existingToughnessMod == null) {
                            toughnessAttr.applyModifier(new net.minecraft.entity.ai.attributes.AttributeModifier(GOLDEN_TOUGHNESS_UUID, "Golden Osmosis Toughness", expectedToughnessBonus, 0).setSaved(false));
                        }
                    } else {
                        if (existingArmorMod != null) armorAttr.removeModifier(existingArmorMod);
                        if (existingToughnessMod != null) toughnessAttr.removeModifier(existingToughnessMod);
                    }
                }
            }

            // ARCHMAGE - PotionCore magicDamage Attribute (Optymalizacja, Timer: 20t)
            if (player.ticksExisted % 20 == 0) {
                if (net.minecraftforge.fml.common.Loader.isModLoaded("potioncore")) {
                    net.minecraft.entity.ai.attributes.IAttributeInstance magicDamageAttr = player.getAttributeMap().getAttributeInstanceByName("potioncore.magicDamage");
                    if (magicDamageAttr != null) {
                        boolean hasArchmage = TraitBase.hasTrait(player, "reskillable:magic", "compatskills:archmage");
                        boolean hasModifier = magicDamageAttr.hasModifier(ARCHMAGE_MODIFIER);
                        
                        if (hasArchmage && !hasModifier) {
                            magicDamageAttr.applyModifier(ARCHMAGE_MODIFIER);
                        } else if (!hasArchmage && hasModifier) {
                            magicDamageAttr.removeModifier(ARCHMAGE_MODIFIER);
                        }
                    }
                }
            }

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
                if (currentIdle >= 20 && TraitBase.hasTrait(player, "reskillable:agility", "compatskills:meditation")) {
                    for (ItemStack stack : player.getArmorInventoryList()) {
                        if (!stack.isEmpty() && stack.getItem() instanceof ItemWizardArmour) {
                            ((IManaStoringItem) stack.getItem()).rechargeMana(stack, 2);
                        }
                    }
                    ItemStack offhand = player.getHeldItemOffhand();
                    if (!offhand.isEmpty() && offhand.getItem() instanceof IManaStoringItem) {
                        ((IManaStoringItem) offhand.getItem()).rechargeMana(offhand, 2);
                    }
                }
            }
        }

        // SPIDER'S GRACE - execution
        if (TraitBase.hasTrait(player, "reskillable:defense", "compatskills:spiders_grace")) {
            try {
                // 1. Sprawdzamy, czy gracz wdepnął w pajęczynę
                boolean inWeb = net.minecraftforge.fml.common.ObfuscationReflectionHelper
                        .getPrivateValue(net.minecraft.entity.Entity.class, player, "field_70134_J");
                
                if (inWeb) {
                    // 2. Wyłączamy drastyczne spowolnienie (-75%) z czystej gry
                    net.minecraftforge.fml.common.ObfuscationReflectionHelper
                            .setPrivateValue(net.minecraft.entity.Entity.class, player, false, "field_70134_J");
                    
                    // 3. Aplikujemy własne, łagodniejsze spowolnienie (-15% speeda)
                    player.motionX *= 0.85D;
                    player.motionZ *= 0.85D;
                    
                    // (Opcjonalnie) Spowalnia też minimalnie opadanie w dół, żeby gracz nie spadał przez pajęczyny jak kamień
                    player.motionY *= 0.85D; 
                }
            } catch (Exception e) {
                System.err.println("[InsaneTweaks] Error applying Spider's Grace reflection:");
                e.printStackTrace();
            }
        }
    }

    // EBWizardry Magic Traits
    @SubscribeEvent
    public void onSpellCastPre(SpellCastEvent.Pre event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;
        if (!(event.getCaster() instanceof EntityPlayer))
            return;
        EntityPlayer player = (EntityPlayer) event.getCaster();
        if (player.world.isRemote)
            return;

        // Arcane Mastery (10% Cost Reduction)
        if (TraitBase.hasTrait(player, "reskillable:magic", "compatskills:arcane_mastery")) {
            float currentCost = event.getModifiers().get("cost");
            event.getModifiers().set("cost", Math.max(0.05f, currentCost * 0.90f), false);
        }

        Spell spell = event.getSpell();
        if (spell == null)
            return;

        electroblob.wizardry.constants.SpellType type = spell.getType();

        // School of Conjuration - TESTING
        // Moved from Post to Pre so summon-related modifiers are applied before
        // SpellMinion reads them during minion creation.
        if (TraitBase.hasTrait(player, "reskillable:magic", "compatskills:school_of_conjuration")) {
            if (type == electroblob.wizardry.constants.SpellType.MINION
                    || type == electroblob.wizardry.constants.SpellType.CONSTRUCT) {
                SummonDurationStat.applyTestModifier(event);
            }
        }
    }

    @SubscribeEvent
    public void onSpellCastPost(SpellCastEvent.Post event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;
        if (!(event.getCaster() instanceof EntityPlayer))
            return;
        EntityPlayer player = (EntityPlayer) event.getCaster();
        if (player.world.isRemote)
            return;

        Spell spell = event.getSpell();
        if (spell == null)
            return;

        // Archmage
        if (TraitBase.hasTrait(player, "reskillable:magic", "compatskills:archmage")) {
            if (net.minecraftforge.fml.common.Loader.isModLoaded("potioncore")) {
                event.getModifiers().set("potency",
                        event.getModifiers().get("potency") * 1.05f, false);
            } else {
                event.getModifiers().set("potency",
                        event.getModifiers().get("potency") * 1.15f, false);
            }
        }

        electroblob.wizardry.constants.SpellType type = spell.getType();

        // School of Alteration
        if (TraitBase.hasTrait(player, "reskillable:magic", "compatskills:school_of_alteration")) {
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
        if (TraitBase.hasTrait(player, "reskillable:magic", "compatskills:school_of_destruction")) {
            if (type == electroblob.wizardry.constants.SpellType.ATTACK
                    || type == electroblob.wizardry.constants.SpellType.PROJECTILE) {
                event.getModifiers().set("potency",
                        event.getModifiers().get("potency") * 1.10f, false);
            }
        }
    }

    private static boolean isGoldenArmor(ItemStack stack) {
        if (stack.isEmpty()) return false;
        net.minecraft.item.Item item = stack.getItem();
        if (item instanceof net.minecraft.item.ItemArmor) {
            if (((net.minecraft.item.ItemArmor) item).getArmorMaterial() == net.minecraft.item.ItemArmor.ArmorMaterial.GOLD) return true;
        }
        net.minecraft.util.ResourceLocation reg = item.getRegistryName();
        if (reg != null && reg.getResourcePath().toLowerCase().contains("gold") && item instanceof net.minecraft.item.ItemArmor) return true;
        return false;
    }

    private static boolean isGoldenWeaponOrTool(ItemStack stack) {
        if (stack.isEmpty()) return false;
        net.minecraft.item.Item item = stack.getItem();
        
        boolean isGold = false;
        if (item instanceof net.minecraft.item.ItemTool && "GOLD".equals(((net.minecraft.item.ItemTool) item).getToolMaterialName())) isGold = true;
        if (item instanceof net.minecraft.item.ItemSword && "GOLD".equals(((net.minecraft.item.ItemSword) item).getToolMaterialName())) isGold = true;
        if (item instanceof net.minecraft.item.ItemHoe && "GOLD".equals(((net.minecraft.item.ItemHoe) item).getMaterialName())) isGold = true;
        
        net.minecraft.util.ResourceLocation reg = item.getRegistryName();
        if (reg != null && reg.getResourcePath().toLowerCase().contains("gold")) {
            if (!(item instanceof net.minecraft.item.ItemArmor)) isGold = true;
        }
        
        if (!isGold && item.isRepairable()) {
            try {
                if (item.getIsRepairable(stack, new ItemStack(net.minecraft.init.Items.GOLD_INGOT)) && !(item instanceof net.minecraft.item.ItemArmor)) {
                    isGold = true;
                }
            } catch (Exception e) {}
        }
        
        return isGold;
    }

    @SubscribeEvent
    public void onEnderPearlJoinWorld(net.minecraftforge.event.entity.EntityJoinWorldEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;

        // Jeśli wchodzącym na serwer bytem jest Ender Perła
        if (event.getEntity() instanceof net.minecraft.entity.item.EntityEnderPearl) {
            net.minecraft.entity.item.EntityEnderPearl pearl = (net.minecraft.entity.item.EntityEnderPearl) event.getEntity();
            net.minecraft.entity.EntityLivingBase thrower = pearl.getThrower();
            
            // Jeśli wyrzucił ją gracz posiadający Safe Port
            if (thrower instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) thrower;
                if (TraitBase.hasTrait(player, "reskillable:magic", "reskillable:safe_port")) {
                    // Zwiększamy szybkość wyrzutu o 30%
                    pearl.motionX *= 1.3D;
                    pearl.motionY *= 1.3D;
                    pearl.motionZ *= 1.3D;
                }
            }
        }
    }

    @SubscribeEvent
    public void onSkillUnlocked(UnlockUnlockableEvent.Post event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;

        if (event.getUnlockable() != null) {
            net.minecraft.util.ResourceLocation regName = event.getUnlockable().getRegistryName();
            if (regName != null && regName.toString().equals("reskillable:golden_osmosis")) {
                event.getEntityPlayer().sendMessage(new TextComponentString(
                    TextFormatting.GOLD + "[InsaneTweaks]: " + TextFormatting.RESET + "You're so awesome your golden osmosis needs a buff! Golden Armor receives +1 Armor, +0.5 Toughness per piece, and golden tools/weapons receive +25% Attack Speed."
                ));
            } else if (regName != null && regName.toString().equals("reskillable:safe_port")) {
                event.getEntityPlayer().sendMessage(new TextComponentString(
                    TextFormatting.GOLD + "[InsaneTweaks]: " + TextFormatting.RESET + "You're so awesome your safe port needs a buff! Ender Pearl throw velocity and range are increased by 30%!"
                ));
            }
        }
    }
}
