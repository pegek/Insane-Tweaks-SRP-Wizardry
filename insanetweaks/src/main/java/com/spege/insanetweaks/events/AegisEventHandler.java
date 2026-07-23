package com.spege.insanetweaks.events;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.spege.insanetweaks.init.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import electroblob.wizardry.item.IManaStoringItem;

public class AegisEventHandler {

    private static final ResourceLocation IMMALEABLE_LOC = new ResourceLocation("srparasites", "immaleable");
    private static final ResourceLocation CORROSIVE_LOC = new ResourceLocation("srparasites", "corrosive");
    private static final ResourceLocation CORROSION_LOC = new ResourceLocation("srparasites", "corrosion");
    private static final int AEGIS_BURN_DURATION_TICKS = 80;
    private static final long AEGIS_BURN_CLEANUP_INTERVAL = 20L;

    private final Map<Integer, Long> activeAegisBurns = new HashMap<>();
    private long lastBurnCleanupTime = -AEGIS_BURN_CLEANUP_INTERVAL;

    private void startAegisBurn(EntityLivingBase entity) {
        activeAegisBurns.put(entity.getEntityId(), entity.world.getTotalWorldTime() + AEGIS_BURN_DURATION_TICKS);
    }

    private void cleanupExpiredAegisBurns(long worldTime) {
        if (worldTime - lastBurnCleanupTime < AEGIS_BURN_CLEANUP_INTERVAL) {
            return;
        }

        lastBurnCleanupTime = worldTime;

        Iterator<Map.Entry<Integer, Long>> iterator = activeAegisBurns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            if (entry.getValue() <= worldTime) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        
        // Protection safeguarding mana, potion effects and fire from executing on phantom entities in the client environment
        if (player.world.isRemote) return;
        
        DamageSource source = event.getSource();

        if (source.isDamageAbsolute() || source.canHarmInCreative()) return;

        ItemStack activeStack = player.getActiveItemStack();
        if (activeStack.isEmpty()) return;

        Item shieldItem = activeStack.getItem();
        if (shieldItem == ModItems.LIVING_AEGIS || shieldItem == ModItems.SENTIENT_AEGIS) {
            if (player.isHandActive() && shieldItem.isShield(activeStack, player)) {
                
                Vec3d damageVec = source.getDamageLocation();

                // Track whether this attack was actually blockable from the front.
                // Attacks with no position (magic, fire, environmental) are NOT counted
                // as "damage blocked" since the shield cannot directionally intercept them.
                boolean isDirectionallyBlocked = false;

                if (damageVec != null) {
                    Vec3d viewVec = player.getLook(1.0f);
                    Vec3d posVec = player.getPositionVector();
                    if (viewVec != null && posVec != null) {
                        Vec3d toDamage = damageVec.subtract(posVec).normalize();
                        if (toDamage != null) {
                            toDamage = new Vec3d(toDamage.x, 0.0, toDamage.z);
                            if (toDamage.dotProduct(viewVec) < 0.0) {
                                return; // Attack from behind  Enot blocked
                            }
                            // Attack is frontal and has a known position -> truly blocked
                            isDirectionallyBlocked = true;
                        }
                    }
                }

                // Block everything that is not unblockable
                if (!source.isUnblockable()) {
                    Entity trueSource = source.getTrueSource();

                        // Retribution Logic: Ignite and debuff attacker
                        if (trueSource instanceof EntityLivingBase) {
                            EntityLivingBase attacker = (EntityLivingBase) trueSource;
                            boolean isProjectile = source.isProjectile();
                            boolean isCloseMelee = !isProjectile && attacker.getDistance(player) <= 5.0D;

                            // Projectiles trigger always, melee only if close
                            if (isProjectile || isCloseMelee) {
                                boolean hasEasterEgg = false;
                                if (activeStack.hasTagCompound()) {
                                    NBTTagCompound tag = activeStack.getTagCompound();
                                    if (tag != null && tag.getFloat("AegisDamageBlocked") >= 10000.0f) {
                                        hasEasterEgg = true;
                                    }
                                }

                                if (hasEasterEgg) {
                                    // Massive Fire Cache (80 ticks = 4 seconds)
                                    startAegisBurn(attacker);
                                } else {
                                    // Normal Fire (4 seconds)
                                    attacker.setFire(4);
                                }

                                Potion immaleable = ForgeRegistries.POTIONS.getValue(IMMALEABLE_LOC);
                                if (immaleable != null) {
                                    attacker.addPotionEffect(new PotionEffect(immaleable, 80, 0));
                                }

                                if (shieldItem == ModItems.SENTIENT_AEGIS) {
                                    Potion corrosion = ForgeRegistries.POTIONS.getValue(CORROSIVE_LOC);
                                    if (corrosion == null) corrosion = ForgeRegistries.POTIONS.getValue(CORROSION_LOC);
                                    if (corrosion != null) {
                                        attacker.addPotionEffect(new PotionEffect(corrosion, 80, 0));
                                    }
                                }
                            }
                        }


                        // Mana Drain
                        if (shieldItem instanceof IManaStoringItem) {
                            IManaStoringItem manaItem = (IManaStoringItem) shieldItem;
                            int manaDrain = Math.max(1, (int) event.getAmount());
                            int newMana = Math.max(0, manaItem.getMana(activeStack) - manaDrain);
                            manaItem.setMana(activeStack, newMana);
                        }

                        // Progress Tracking: only count truly blocked frontal positioned attacks
                        if (isDirectionallyBlocked) {
                            if (!activeStack.hasTagCompound()) activeStack.setTagCompound(new NBTTagCompound());
                            NBTTagCompound nbt = activeStack.getTagCompound();
                            if (nbt != null) {
                                float currentBlocked = nbt.getFloat("AegisDamageBlocked");
                                if (currentBlocked < 10000.0f) {
                                    float blocked = currentBlocked + event.getAmount();
                                    nbt.setFloat("AegisDamageBlocked", Math.min(10000.0f, blocked));

                                    // Evolution Trigger: 1500 Damage Blocked -> Living Aegis evolves into Sentient Aegis
                                    if (shieldItem == ModItems.LIVING_AEGIS && blocked >= 1500.0f) {
                                        ItemStack newShield = new ItemStack(ModItems.SENTIENT_AEGIS);
                                        newShield.setTagCompound(nbt.copy());
                                        player.setHeldItem(player.getActiveHand(), newShield);

                                        player.world.playSound(null, player.posX, player.posY, player.posZ,
                                            net.minecraft.init.SoundEvents.ENTITY_WITHER_SPAWN,
                                            net.minecraft.util.SoundCategory.PLAYERS, 1.0f, 1.0f);

                                        if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo) {
                                            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                                                "\u00a7c\u00a7l[!] \u00a7cYour shield pulses as it absorbs enough essence!\n" +
                                                "It has evolved into the Sentient Aegis!"));
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onLivingUpdate(LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();

        // --- Aegis Player Logic ---
        if (entity instanceof EntityPlayer && !entity.world.isRemote) {
            EntityPlayer player = (EntityPlayer) entity;
            ItemStack main = player.getHeldItemMainhand();
            ItemStack off = player.getHeldItemOffhand();

            boolean hasAegis = (main.getItem() == ModItems.LIVING_AEGIS || main.getItem() == ModItems.SENTIENT_AEGIS) || 
                             (off.getItem() == ModItems.LIVING_AEGIS || off.getItem() == ModItems.SENTIENT_AEGIS);

            if (hasAegis) {
                // Anti-Axe Guard
                Item livingAegis = ModItems.LIVING_AEGIS;
                Item sentientAegis = ModItems.SENTIENT_AEGIS;
                if (player.getCooldownTracker().hasCooldown(livingAegis)) player.getCooldownTracker().removeCooldown(livingAegis);
                if (player.getCooldownTracker().hasCooldown(sentientAegis)) player.getCooldownTracker().removeCooldown(sentientAegis);

                // Extinguish feature (Every 1.5 seconds)
                if (player.ticksExisted % 30 == 0 && player.isBurning()) {
                    player.extinguish();
                }
            }
        }

        // --- Fast Fire System (For enemies hit by shield) ---
        if (!entity.world.isRemote) {
            long worldTime = entity.world.getTotalWorldTime();
            cleanupExpiredAegisBurns(worldTime);

            int entityId = entity.getEntityId();
            Long burnExpiry = activeAegisBurns.get(entityId);
            if (burnExpiry != null) {
                int burnTicks = (int) (burnExpiry - worldTime);

                if (burnTicks > 0) {
                    // Damage every 10 ticks (0.5 seconds)
                    if (entity.ticksExisted % 10 == 0) {
                        entity.hurtResistantTime = 0; // Break i-frames
                        DamageSource fireSource = DamageSource.IN_FIRE;
                        if (fireSource != null) entity.attackEntityFrom(fireSource, 1.0F);
                    }

                    // Maintain visual fire
                    if (!entity.isBurning()) {
                        entity.setFire(2);
                    }
                } else {
                    activeAegisBurns.remove(entityId);
                }
            }
        }
    }

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntityLiving().world.isRemote) return;
        activeAegisBurns.remove(event.getEntityLiving().getEntityId());
    }

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) return;

        ItemStack main = player.getHeldItemMainhand();
        ItemStack off = player.getHeldItemOffhand();

        boolean hasAegisEquipped = (main.getItem() == ModItems.LIVING_AEGIS || main.getItem() == ModItems.SENTIENT_AEGIS) || 
                                   (off.getItem() == ModItems.LIVING_AEGIS || off.getItem() == ModItems.SENTIENT_AEGIS);

        if (hasAegisEquipped) {

            // Backstab Verification
            ItemStack activeStack = player.getActiveItemStack();
            boolean isAegisRaised = player.isHandActive() && !activeStack.isEmpty() && 
                                  (activeStack.getItem() == ModItems.LIVING_AEGIS || activeStack.getItem() == ModItems.SENTIENT_AEGIS);

            DamageSource source = event.getSource();
            Vec3d damageLoc = source.getDamageLocation();
            if (event.getAmount() > 0 && damageLoc != null && isAegisRaised) {
                Vec3d viewVec = player.getLook(1.0f);
                Vec3d posVec = player.getPositionVector();
                if (viewVec != null && posVec != null) {
                    Vec3d toDamage = damageLoc.subtract(posVec).normalize();
                    if (toDamage != null) {
                        toDamage = new Vec3d(toDamage.x, 0.0, toDamage.z);

                        // Hits from behind deal +30% DMG
                        if (toDamage.dotProduct(viewVec) < 0.0) {
                            event.setAmount(event.getAmount() * 1.3f);
                        }
                    }
                }
            }
        }
    }
}
