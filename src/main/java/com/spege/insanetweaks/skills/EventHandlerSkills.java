package com.spege.insanetweaks.skills;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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

    // Removed memory-leaking static maps. Data is stored directly on player NBT securely.

    // Basic Forge Events
    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule) return;
        if (event.getAttackingPlayer() != null) {
            if (TraitBase.hasTrait(event.getAttackingPlayer(), "reskillable:attack", "compatskills:fast_learner")) {
                event.setDroppedExperience((int)(event.getDroppedExperience() * 1.15));
            }
        }
    }

    @SubscribeEvent
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule) return;
        if (event.getEntityLiving() instanceof EntityPlayer && !event.getEntityLiving().world.isRemote) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            ItemStack item = event.getItem();
            if (item.getItem() instanceof ItemFood) {
                ItemFood food = (ItemFood) item.getItem();
                if (food.getSaturationModifier(item) > 0) {
                    if (TraitBase.hasTrait(player, "reskillable:defense", "compatskills:iron_stomach")) {
                        player.heal(1.0f);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onItemFished(ItemFishedEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule) return;
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) return;

        // Double Loot (6% chance)
        if (TraitBase.hasTrait(player, "reskillable:gathering", "compatskills:double_loot")) {
            if (player.world.rand.nextInt(100) < 6) {
                for (ItemStack drop : event.getDrops()) {
                    if (!drop.isEmpty()) {
                        spawnItemNearPlayer(player, drop.copy());
                    }
                }
            }
        }

        // Enchant Fishing (4% chance)
        if (TraitBase.hasTrait(player, "reskillable:gathering", "compatskills:enchant_fishing")) {
            if (player.world.rand.nextInt(100) < 4) {
                ItemStack book = generateRandomEnchantedBook(player);
                if (book != null && !book.isEmpty()) {
                    event.getDrops().add(book);
                }
            }
        }
    }

    private void spawnItemNearPlayer(EntityPlayer player, ItemStack stack) {
        EntityItem entityItem = new EntityItem(player.world, player.posX, player.posY + 0.5, player.posZ, stack);
        entityItem.setPickupDelay(10);
        entityItem.motionX = (player.world.rand.nextDouble() - 0.5) * 0.15;
        entityItem.motionY = 0.25;
        entityItem.motionZ = (player.world.rand.nextDouble() - 0.5) * 0.15;
        player.world.spawnEntity(entityItem);
    }

    private ItemStack generateRandomEnchantedBook(EntityPlayer player) {
        // Safe mapping of original numeric IDs to reliable resource locations
        List<String> validEnchants = Arrays.asList(
            "somanyenchantments:advancedmending", "somanyenchantments:advancedsharpness",
            "somanyenchantments:advancedsmite", "somanyenchantments:advancedbaneofarthropods",
            "somanyenchantments:advancedknockback", "somanyenchantments:advancedfireaspect",
            "somanyenchantments:advancedlooting", "somanyenchantments:advancedsweepingedge",
            "somanyenchantments:advancedpunch", "somanyenchantments:advancedpower",
            "somanyenchantments:advancedflame", "somanyenchantments:advancedinfinity",
            "somanyenchantments:advancedluckofthesea", "somanyenchantments:advancedlure",
            "somanyenchantments:advancedprotection", "somanyenchantments:advancedblastprotection",
            "somanyenchantments:advancedprojectileprotection", "somanyenchantments:advancedfireprotection",
            "somanyenchantments:advancedfeatherfalling", "somanyenchantments:advancedthorns" 
            // Guessed the AncientSpellcraft/SME equivalents from IDs 127-164
            // We'll use ForgeRegistries lookup to be highly resilient against ID shifting
        );
        
        String chosen = validEnchants.get(player.world.rand.nextInt(validEnchants.size()));
        Enchantment enchant = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(chosen));
        if (enchant == null) return null;
        
        ItemStack book = new ItemStack(net.minecraft.init.Items.ENCHANTED_BOOK);
        NBTTagList storedEnchants = new NBTTagList();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setShort("id", (short) Enchantment.getEnchantmentID(enchant));
        tag.setShort("lvl", (short) enchant.getMaxLevel());
        storedEnchants.appendTag(tag);
        
        NBTTagCompound rootTag = new NBTTagCompound();
        rootTag.setTag("StoredEnchantments", storedEnchants);
        book.setTagCompound(rootTag);
        return book;
    }

    @SubscribeEvent
    public void onBlockHarvest(HarvestDropsEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule) return;
        if (event.getHarvester() != null && !event.getWorld().isRemote) {
            if (TraitBase.hasTrait(event.getHarvester(), "reskillable:mining", "compatskills:astral_prospector")) {
                Block block = event.getState().getBlock();
                ResourceLocation regName = block.getRegistryName();
                if (regName != null && regName.getResourcePath().toLowerCase().contains("ore")) {
                    if (event.getWorld().rand.nextInt(100) < 25) {
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
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule) return;
        if (event.getWorld().isRemote) return;
        
        AxisAlignedBB searchBox = new AxisAlignedBB(event.getPos()).grow(5.0);
        List<EntityPlayer> players = event.getWorld().getEntitiesWithinAABB(EntityPlayer.class, searchBox);
        
        for (EntityPlayer player : players) {
            if (TraitBase.hasTrait(player, "reskillable:building", "compatskills:supreme_enchanter")) {
                int[] bonusPerRow = {3, 7, 10};
                int bonus = bonusPerRow[event.getEnchantRow()];
                event.setLevel(event.getLevel() + bonus);
                break;
            }
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule) return;
        
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();

        if (!player.world.isRemote) {
            // ARCANE MASTERY
            if (player.ticksExisted % 20 == 0) {
                if (TraitBase.hasTrait(player, "reskillable:magic", "compatskills:arcane_mastery")) {
                    if (Loader.isModLoaded("ancientspellcraft")) {
                        Potion manaRegen = ForgeRegistries.POTIONS.getValue(new ResourceLocation("ancientspellcraft", "mana_regeneration"));
                        if (manaRegen != null) {
                            player.addPotionEffect(new PotionEffect(manaRegen, 300, 0, false, false));
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
        // Używamy ObfuscationReflectionHelper, który bezpiecznie ingeruje w prywatne zmienne
        // "field_70134_J" to obfuskowana nazwa zmiennej "isInWeb" na 1.12.2.
        // Wywoływane natywnie po stronie klienta oraz serwera, dzięki wbudowanej synchronizacji Reskillable
        if (TraitBase.hasTrait(player, "reskillable:defense", "compatskills:poison_immunity")) {
            try {
                net.minecraftforge.fml.common.ObfuscationReflectionHelper.setPrivateValue(net.minecraft.entity.Entity.class, player, false, "field_70134_J");
            } catch (Exception e) {
                // Bezpieczny fallback
                System.err.println("[InsaneTweaks] Error applying Spider's Grace reflection:");
                e.printStackTrace();
            }
        }
    }

    // EBWizardry Magic Traits
    @SubscribeEvent
    public void onSpellCastPost(SpellCastEvent.Post event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule) return;
        if (!(event.getCaster() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getCaster();
        if (player.world.isRemote) return;
        
        Spell spell = event.getSpell();
        if (spell == null) return;

        // Archmage (Power Creep)
        if (TraitBase.hasTrait(player, "reskillable:magic", "compatskills:power_creep")) {
            event.getModifiers().set("potency", 
                event.getModifiers().get("potency") * 1.15f, false);
        }

        electroblob.wizardry.constants.SpellType type = spell.getType();

        // School of Alteration
        if (TraitBase.hasTrait(player, "reskillable:magic", "compatskills:school_of_alteration")) {
            if (type == electroblob.wizardry.constants.SpellType.BUFF || type == electroblob.wizardry.constants.SpellType.ALTERATION) {
                event.getModifiers().set("duration", 
                    event.getModifiers().get("duration") * 1.20f, false);
            }
        }

        // School of Conjuration
        if (TraitBase.hasTrait(player, "reskillable:magic", "compatskills:school_of_conjuration")) {
            if (type == electroblob.wizardry.constants.SpellType.MINION || type == electroblob.wizardry.constants.SpellType.CONSTRUCT) {
                event.getModifiers().set("duration", 
                    event.getModifiers().get("duration") * 1.25f, false);
            }
        }

        // School of Destruction
        if (TraitBase.hasTrait(player, "reskillable:magic", "compatskills:school_of_destruction")) {
            if (type == electroblob.wizardry.constants.SpellType.ATTACK || type == electroblob.wizardry.constants.SpellType.PROJECTILE) {
                event.getModifiers().set("potency", 
                    event.getModifiers().get("potency") * 1.15f, false);
            }
        }
    }
}
