package com.spege.insanetweaks.skills;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.enchanting.EnchantmentLevelSetEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.item.IManaStoringItem;
import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.spell.Spell;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class EventHandlerSkills {

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
            // ARCANE MASTERY
            if (player.ticksExisted % 20 == 0) {
                if (TraitBase.hasTrait(player, "reskillable:magic", "compatskills:arcane_mastery")) {
                    if (Loader.isModLoaded("ancientspellcraft")) {
                        Potion manaRegen = ForgeRegistries.POTIONS
                                .getValue(new ResourceLocation("ancientspellcraft", "mana_regeneration"));
                        if (manaRegen != null) {
                            player.addPotionEffect(new PotionEffect(manaRegen, 300, 0, false, false));
                        }
                    }
                }
            }

            // ARCHMAGE - PotionCore magicDamage Attribute (+5%)
            if (net.minecraftforge.fml.common.Loader.isModLoaded("potioncore")) {
                net.minecraft.entity.ai.attributes.IAttributeInstance magicDamageAttr = player.getAttributeMap().getAttributeInstanceByName("potioncore.magicDamage");
                if (magicDamageAttr != null) {
                    // Unikalne UUID dla tego konkretnego bonusu ze skilla
                    java.util.UUID archmageModifierUUID = java.util.UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0123456789ab");
                    // Operacja 1 oznacza dodanie procentowe (0.05D = +5%)
                    net.minecraft.entity.ai.attributes.AttributeModifier archmageModifier = new net.minecraft.entity.ai.attributes.AttributeModifier(archmageModifierUUID, "Archmage Magic Damage Bonus", 0.05D, 1).setSaved(false);
                    
                    boolean hasArchmage = TraitBase.hasTrait(player, "reskillable:magic", "compatskills:archmage");
                    boolean hasModifier = magicDamageAttr.hasModifier(archmageModifier);
                    
                    if (hasArchmage && !hasModifier) {
                        magicDamageAttr.applyModifier(archmageModifier);
                    } else if (!hasArchmage && hasModifier) {
                        magicDamageAttr.removeModifier(archmageModifier);
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
                        event.getModifiers().get("potency") * 1.10f, false);
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
                        event.getModifiers().get("duration") * 1.20f, false);
            }
        }

        // School of Conjuration
        if (TraitBase.hasTrait(player, "reskillable:magic", "compatskills:school_of_conjuration")) {
            if (type == electroblob.wizardry.constants.SpellType.MINION
                    || type == electroblob.wizardry.constants.SpellType.CONSTRUCT) {
                event.getModifiers().set("duration",
                        event.getModifiers().get("duration") * 1.25f, false);
            }
        }

        // School of Destruction
        if (TraitBase.hasTrait(player, "reskillable:magic", "compatskills:school_of_destruction")) {
            if (type == electroblob.wizardry.constants.SpellType.ATTACK
                    || type == electroblob.wizardry.constants.SpellType.PROJECTILE) {
                event.getModifiers().set("potency",
                        event.getModifiers().get("potency") * 1.15f, false);
            }
        }
    }
}
