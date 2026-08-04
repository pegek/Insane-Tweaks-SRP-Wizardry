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
import electroblob.wizardry.util.SpellModifiers;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

@SuppressWarnings("null")
public class EventHandlerSkills {

    // Stałe pod optymalizację traitu Archmage (Testowe wartości)
    private static final java.util.UUID ARCHMAGE_MODIFIER_UUID = java.util.UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0123456789ab");
    // Obrażenia Magiczne: 0.07D = +7%
    private static final net.minecraft.entity.ai.attributes.AttributeModifier ARCHMAGE_MODIFIER = new net.minecraft.entity.ai.attributes.AttributeModifier(ARCHMAGE_MODIFIER_UUID, "Archmage Magic Damage Bonus", 0.07D, 1).setSaved(false);

    // Stałe pod traita Bob the Builder
    private static final java.util.UUID BOB_MODIFIER_UUID = java.util.UUID.fromString("b2c3d4e5-f6a7-4b8c-9d0e-123456789abc");
    // Zwiększenie zasięgu o 2 bloki (+2.0D)
    private static final net.minecraft.entity.ai.attributes.AttributeModifier BOB_MODIFIER = new net.minecraft.entity.ai.attributes.AttributeModifier(BOB_MODIFIER_UUID, "Bob the Builder Reach", 2.0D, 0).setSaved(false);

    // Stałe pod traita Angry Farmer
    private static final java.util.UUID ANGRY_FARMER_DMG_UUID = java.util.UUID.fromString("c3d4e5f6-a7b8-4c9d-0e1f-23456789abcd");
    private static final net.minecraft.entity.ai.attributes.AttributeModifier ANGRY_FARMER_DMG = new net.minecraft.entity.ai.attributes.AttributeModifier(ANGRY_FARMER_DMG_UUID, "Angry Farmer Damage", 5.0D, 0).setSaved(false);
    // Stałe pod traita Golden Osmosis Buffed
    private static final java.util.UUID GOLDEN_ARMOR_UUID = java.util.UUID.fromString("e5f6a7b8-c9d0-4e1f-2a34-56789abcdef0");
    private static final java.util.UUID GOLDEN_TOUGHNESS_UUID = java.util.UUID.fromString("f6a7b8c9-d0e1-4f2a-3b45-6789abcdef01");
    private static final java.util.UUID GOLDEN_SPEED_UUID = java.util.UUID.fromString("0a1b2c3d-4e5f-6a7b-8c9d-e0123456789a");
    private static final net.minecraft.entity.ai.attributes.AttributeModifier GOLDEN_SPEED_MOD = new net.minecraft.entity.ai.attributes.AttributeModifier(GOLDEN_SPEED_UUID, "Golden Osmosis Speed", 0.25D, 2).setSaved(false);

    // Removed memory-leaking static maps. Data is stored directly on player NBT
    // securely.

    /**
     * {@code Entity.isInWeb}, resolved once for Spider's Grace.
     *
     * <p>This used to go through {@code ObfuscationReflectionHelper.getPrivateValue} on every
     * tick. That ends up in {@code ReflectionHelper.findField}, which caches nothing at all -
     * {@code getDeclaredField} plus {@code setAccessible(true)} per call, every call. Forge's own
     * javadoc on it says to store the result and not call it repeatedly.
     *
     * <p>It was also passed only the SRG name, which is right in the packaged jar but wrong in
     * {@code runClient}, where the classes carry MCP names - so in dev the lookup threw and the
     * catch block printed a stack trace every tick, on both sides. Both names are tried here.
     */
    private static final java.lang.reflect.Field IN_WEB_FIELD = resolveInWebField();

    /**
     * Set if Spider's Grace ever throws at runtime. It then stops trying, so a broken reflection
     * cannot cost a thrown exception and a log line on every tick for every player - which is what
     * the old code did in dev, where the SRG-only field name never resolved.
     */
    private static boolean spidersGraceFailed = false;

    private static java.lang.reflect.Field resolveInWebField() {
        // MCP name first (dev), SRG second (packaged runtime).
        String[] candidates = { "isInWeb", "field_70134_J" };
        for (String name : candidates) {
            try {
                java.lang.reflect.Field field = net.minecraft.entity.Entity.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // try the other naming
            } catch (Exception e) {
                break;
            }
        }
        com.spege.insanetweaks.InsaneTweaksMod.LOGGER.error(
                "[InsaneTweaks] Could not resolve Entity.isInWeb - Spider's Grace will do nothing.");
        return null;
    }

    // Basic Forge Events
    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;
        if (event.getAttackingPlayer() != null) {
            if (TraitHandle.FAST_LEARNER.has(event.getAttackingPlayer())) {
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
            if (TraitHandle.IRON_STOMACH.has(player)) {
                int newDuration = (int) (event.getDuration() * 0.85F);
                event.setDuration(newDuration);
            }
        }
    }

    // Iron Stomach's saturation bonus lived here (LivingEntityUseItemEvent.Finish) until
    // 2026-08-04. It applied MobEffects.SATURATION for (healAmount * saturationModifier / 2)
    // ticks, and vanilla's saturation potion runs addStats(1, 1.0F) EVERY tick - so a steak
    // handed out +3 hunger and +6 saturation on top of the food itself. Removed as a balance
    // decision, replaced by the food-debuff resistance below.

    // -------------------------------------------------------------------------
    // Iron Stomach - food debuff resistance
    // -------------------------------------------------------------------------

    /** Fraction cut off the debuff duration a piece of food inflicts. 1.0F would be full immunity. */
    private static final float IRON_STOMACH_DEBUFF_REDUCTION = 0.5F;

    /**
     * Per-player snapshot of active effect durations, taken on the last use tick before the food
     * lands. Key: entity id. Value: [player.ticksExisted at capture, Map&lt;Potion, duration&gt;].
     *
     * Bounded by the number of players who have ever eaten while holding the trait - it stores no
     * World or Entity references, only Potion singletons and ints, and each player has at most one
     * entry which the next meal overwrites. The tick stamp makes a stale entry inert rather than
     * wrong: a meal interrupted by an item swap goes through Entity.resetActiveHand(), which fires
     * no event at all, so there is no reliable place to clear it.
     */
    private final java.util.Map<Integer, Object[]> ironStomachSnapshots = new java.util.HashMap<>();

    /**
     * Capture the effect state one step before the food applies its own.
     *
     * Verified against EntityLivingBase.updateActiveHand() (Forge 1.12.2): ForgeEventFactory
     * .onItemUseTick fires with activeItemStackUseCount BEFORE the decrement, and the very next
     * statement is {@code if (--activeItemStackUseCount <= 0 && !world.isRemote) onItemUseFinish();}
     * So the tick that reports duration == 1 is the last one before ItemFood.onFoodEaten runs, in
     * the same tick. Diffing this snapshot against the state in Finish yields exactly the effects
     * the food itself inflicted - which is what makes this work for any mod's food rather than a
     * hardcoded list of vanilla's.
     */
    @SubscribeEvent
    public void onItemUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;
        if (event.getDuration() > 1)
            return;
        if (!(event.getEntityLiving() instanceof EntityPlayer))
            return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote)
            return;
        if (!(event.getItem().getItem() instanceof ItemFood))
            return;
        if (!TraitHandle.IRON_STOMACH.has(player))
            return;

        java.util.Map<net.minecraft.potion.Potion, Integer> before = new java.util.HashMap<>();
        for (net.minecraft.potion.PotionEffect active : player.getActivePotionEffects()) {
            before.put(active.getPotion(), active.getDuration());
        }
        ironStomachSnapshots.put(player.getEntityId(), new Object[] { player.ticksExisted, before });
    }

    /**
     * Shorten whatever debuff the food just inflicted.
     *
     * Only effects that are new, or whose duration grew past the snapshot, count as "inflicted by
     * this meal" - so a Poison picked up in combat while chewing is left alone, and an extended
     * effect is only trimmed down to what the player already had.
     *
     * FUTURE NOTE - SRP effects are deliberately NOT handled here. Classification uses vanilla's
     * Potion.isBadEffect(), the mandatory constructor flag, which is the only marker that is both
     * server-safe (Forge's isBeneficial() is @SideOnly(Side.CLIENT) - see PotionCleanse) and
     * respected by well-behaved mods. SRParasites sets it wrong: 15 of its 24 harmful effects are
     * constructed with isBadEffect = false (coth, fear, vomit, senses, debar, needler, foster,
     * link, parate, spotted, braining, novision, indeaf, the_sign, thornshade_thorns - verified on
     * SRPPotions.<clinit>, SRParasites 1.10.7), so SRP food debuffs slip through untouched. Fixing
     * that needs the inverse approach: a whitelist of effects worth KEEPING (vanilla's 16
     * setBeneficial() entries minus instant_damage, plus PotionCleanse.BUILT_IN_PROTECTED_EFFECTS)
     * and treat everything else the food applied as hostile. Deferred on purpose - do not "fix" it
     * by adding an SRP effect list here, that would be the third copy of the same list in this repo.
     */
    @SubscribeEvent
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;
        if (!(event.getEntityLiving() instanceof EntityPlayer))
            return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote)
            return;

        Object[] snapshot = ironStomachSnapshots.remove(player.getEntityId());
        if (snapshot == null || (int) snapshot[0] != player.ticksExisted)
            return; // no snapshot, or a stale one left by an interrupted meal

        @SuppressWarnings("unchecked")
        java.util.Map<net.minecraft.potion.Potion, Integer> before =
                (java.util.Map<net.minecraft.potion.Potion, Integer>) snapshot[1];

        // Copy: removePotionEffect/addPotionEffect mutate the active-effect map we are walking.
        for (net.minecraft.potion.PotionEffect active
                : new ArrayList<>(player.getActivePotionEffects())) {
            net.minecraft.potion.Potion potion = active.getPotion();
            if (!potion.isBadEffect())
                continue;

            Integer previous = before.get(potion);
            int floor = (previous == null) ? 0 : previous.intValue();
            int gained = active.getDuration() - floor;
            if (gained <= 0)
                continue; // predates this meal

            int trimmed = floor + Math.max(1, (int) (gained * (1.0F - IRON_STOMACH_DEBUFF_REDUCTION)));
            player.removePotionEffect(potion);
            player.addPotionEffect(new net.minecraft.potion.PotionEffect(potion, trimmed,
                    active.getAmplifier(), active.getIsAmbient(), active.doesShowParticles()));
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
        if (TraitHandle.DOUBLE_LOOT.has(player)) {
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
        if (TraitHandle.ENCHANT_FISHING.has(player)) {
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
            if (event.getHarvester() instanceof net.minecraftforge.common.util.FakePlayer)
                return;
            if (TraitHandle.ASTRAL_PROSPECTOR.has(event.getHarvester())) {
                if (isOreBlock(event.getState())) {
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

    /**
     * Astral Prospector's "is this an ore" test.
     *
     * This used to be regName.getResourcePath().contains("ore"), which is a substring match and
     * therefore matched a pile of things that are not ores. Verified false positives in the DEv 1.2
     * pack: srparasites:goreada/gorefer/goremar/gorepri/gorepur/goresim and srpextra:goredorpa (g-ORE-),
     * srparasites:parasitic_colony_core_slab and iceandfire:dragonforge_core* and our own
     * insanetweaks:sanctuary_core (c-ORE), all 16 quark:colored_flowerpot_* (fl-ORE-wpot), and
     * da:spore_blossom. The SRP gore blocks were the practical problem - they carpet infested biomes.
     *
     * The OreDictionary is the 1.12.2 cross-mod contract for "this is an ore", so it goes first. The
     * name test stays as a fallback for mods that never registered their ore, but as a shape test
     * rather than a bare substring.
     *
     * <p>Three layers, because no single one of them is enough:
     * <ol>
     * <li><b>Explicit ids.</b> The override, for ores that pass neither of the other two. Built-in
     * list in code plus a config list - the built-in cannot be shipped as a config default, because
     * a Forge {@code @Config} file already on disk never picks up new defaults.</li>
     * <li><b>OreDictionary.</b> Semantically right, but only as good as the mod. Verified 2026-08-04:
     * Ancient Spellcraft contains no {@code registerOre} call at all, and neither does Scaling
     * Health, so their ores are invisible here.</li>
     * <li><b>Name shape.</b> {@code _ore} suffix, {@code ore_} prefix, bare {@code ore}, or
     * {@code _ore_} infix. The infix case is what catches {@code ancientspellcraft:crystal_ore_*}
     * (7 blocks) and is safe against every false positive listed above, because all of those have a
     * letter rather than an underscore in front of their "ore": g-ore, c-ore, col-ore-d, sp-ore.
     * Deliberately NOT {@code endsWith("ore")} - that would drag every {@code *_core} block back in.</li>
     * </ol>
     */
    private static boolean isOreBlock(net.minecraft.block.state.IBlockState state) {
        Block block = state.getBlock();
        ResourceLocation regName = block.getRegistryName();
        if (regName == null)
            return false;

        if (isExplicitlyListedOre(regName))
            return true;

        net.minecraft.item.Item blockItem = net.minecraft.item.Item.getItemFromBlock(block);
        if (blockItem != net.minecraft.init.Items.AIR) {
            try {
                // Meta-variant ore blocks (thermalfoundation:ore and friends) are registered per
                // block metadata, so ask with the state's own meta, not damageDropped().
                ItemStack blockStack = new ItemStack(blockItem, 1, block.getMetaFromState(state));
                for (int oreId : net.minecraftforge.oredict.OreDictionary.getOreIDs(blockStack)) {
                    if (net.minecraftforge.oredict.OreDictionary.getOreName(oreId).startsWith("ore")) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // getMetaFromState can throw on blocks with a partial state->meta mapping.
                // Fall through to the name test rather than breaking the harvest.
            }
        }

        String path = regName.getResourcePath().toLowerCase(java.util.Locale.ROOT);
        return path.endsWith("_ore") || path.startsWith("ore_") || path.equals("ore")
                || path.contains("_ore_");
    }

    /**
     * Ores that neither the OreDictionary nor the name shape can recognise. Shipped in code rather
     * than as config defaults - see the note in {@link #isOreBlock}.
     *
     * <p>{@code srparasites:infestedore} is deliberately absent: it fits the same "no underscore"
     * shape but duplicating it hands out more parasite material, which is a balance call for the
     * pack rather than a correctness fix. Add it via the config list if that is wanted.
     */
    private static final String[] BUILT_IN_EXTRA_ORES = { "scalinghealth:crystalore" };

    private static boolean isExplicitlyListedOre(ResourceLocation regName) {
        String id = regName.toString();
        for (String builtIn : BUILT_IN_EXTRA_ORES) {
            if (builtIn.equals(id))
                return true;
        }
        // Read live, and scanned linearly on purpose: this array is normally empty or has a couple
        // of entries, which beats building and invalidating a set behind a config that can change.
        for (String extra : com.spege.insanetweaks.config.ModConfig.traits.astralProspectorExtraOres) {
            if (id.equals(extra))
                return true;
        }
        return false;
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
            if (TraitHandle.SUPREME_ENCHANTER.has(player)) {
                
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
                    boolean hasBob = TraitHandle.BOB_THE_BUILDER.has(player);
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

            // ANGRY FARMER - +5 flat damage with farming tools
            if (player.ticksExisted % 5 == 0) {
                net.minecraft.entity.ai.attributes.IAttributeInstance dmgAttr = player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.ATTACK_DAMAGE);
                
                if (dmgAttr != null) {
                    boolean hasFarmer = TraitHandle.ANGRY_FARMER.has(player);
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
                    
                    if (holdingFarmTool) {
                        if (!hasDmgMod) dmgAttr.applyModifier(ANGRY_FARMER_DMG);
                    } else {
                        if (hasDmgMod) dmgAttr.removeModifier(ANGRY_FARMER_DMG);
                    }
                }
            }

            // GOLDEN OSMOSIS - Passive Buffs for Gold Equipment
            if (player.ticksExisted % 5 == 0) {
                boolean hasGoldenOsmosis = TraitHandle.GOLDEN_OSMOSIS.has(player);

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
                        boolean hasArchmage = TraitHandle.ARCHMAGE.has(player);
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
                if (currentIdle >= 20 && TraitHandle.MEDITATION.has(player)) {
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
        //
        // Deliberately OUTSIDE the !isRemote guard above. Player movement is computed client-side
        // and only then sent to the server, so a server-only version would be fighting the client's
        // own web slowdown and produce rubber-banding. Both sides have to agree.
        //
        // This also has to stay on LivingUpdateEvent and cannot move to Trait.onPlayerTick: that
        // one fires at Phase.END, i.e. AFTER EntityLivingBase.travel() -> Entity.move(), which is
        // where isInWeb is consumed and zeroed (Entity.java:640). LivingUpdateEvent fires on the
        // first line of onUpdate(), before it. By END the flag is already gone.
        //
        // The flag is read BEFORE asking about the trait on purpose. Reading a cached Field is a
        // plain boolean load; the trait lookup is a map walk. Webs are rare, so on the overwhelming
        // majority of ticks this now costs one field read and nothing else.
        if (IN_WEB_FIELD != null && !spidersGraceFailed) {
            try {
                if (IN_WEB_FIELD.getBoolean(player) && TraitHandle.SPIDERS_GRACE.has(player)) {
                    // 1. Wyłączamy drastyczne spowolnienie (-75%) z czystej gry
                    IN_WEB_FIELD.setBoolean(player, false);

                    // 2. Aplikujemy własne, łagodniejsze spowolnienie (-15% speeda)
                    player.motionX *= 0.85D;
                    player.motionZ *= 0.85D;

                    // (Opcjonalnie) Spowalnia też minimalnie opadanie w dół, żeby gracz nie spadał przez pajęczyny jak kamień
                    player.motionY *= 0.85D;
                }
            } catch (Exception e) {
                spidersGraceFailed = true;
                com.spege.insanetweaks.InsaneTweaksMod.LOGGER.error(
                        "[InsaneTweaks] Spider's Grace failed and is disabled for this session.", e);
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
                if (TraitHandle.SAFE_PORT.has(player)) {
                    // Zwiększamy szybkość wyrzutu o 30%
                    pearl.motionX *= 1.3D;
                    pearl.motionY *= 1.3D;
                    pearl.motionZ *= 1.3D;
                }
            }
        }
    }

}
