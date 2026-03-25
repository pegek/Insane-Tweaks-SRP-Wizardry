package com.spege.insanetweaks.events;

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
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import electroblob.wizardry.item.IManaStoringItem;

public class AegisEventHandler {

    private static final ResourceLocation BLAZING_MIGHT_LOC = new ResourceLocation("enigmaticlegacy", "blazing_might");
    private static final ResourceLocation IMMALEABLE_LOC = new ResourceLocation("srparasites", "immaleable");
    private static final ResourceLocation CORROSIVE_LOC = new ResourceLocation("srparasites", "corrosive");
    private static final ResourceLocation CORROSION_LOC = new ResourceLocation("srparasites", "corrosion");

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        DamageSource source = event.getSource();

        if (source.isDamageAbsolute() || source.canHarmInCreative()) return;

        ItemStack activeStack = player.getActiveItemStack();
        if (activeStack.isEmpty()) return;

        Item shieldItem = activeStack.getItem();
        if (shieldItem == ModItems.PARASITE_AEGIS) {
            if (player.isHandActive() && shieldItem.isShield(activeStack, player)) {
                
                Vec3d damageVec = source.getDamageLocation();

                if (damageVec != null) {
                    Vec3d viewVec = player.getLook(1.0f);
                    Vec3d posVec = player.getPositionVector();
                    if (viewVec != null && posVec != null) {
                        Vec3d toDamage = damageVec.subtract(posVec).normalize();
                        if (toDamage != null) {
                            toDamage = new Vec3d(toDamage.x, 0.0, toDamage.z);
                            if (toDamage.dotProduct(viewVec) < 0.0) {
                                return; // Not frontal
                            }
                        }
                    }
                }

                // Block everything that is not unblockable
                if (!source.isUnblockable()) {
                    Entity trueSource = source.getTrueSource();

                        // Retribution Logic
                        if (trueSource instanceof EntityLivingBase && !source.isProjectile()) {
                            EntityLivingBase attacker = (EntityLivingBase) trueSource;

                            if (attacker.getDistance(player) <= 5.0D) {
                                // Fast Fire NBT (80 ticks = 4 seconds)
                                attacker.getEntityData().setInteger("AegisBurn", 80);

                                Potion immaleable = ForgeRegistries.POTIONS.getValue(IMMALEABLE_LOC);
                                if (immaleable != null) {
                                    attacker.addPotionEffect(new PotionEffect(immaleable, 80, 0));
                                }

                                Potion corrosion = ForgeRegistries.POTIONS.getValue(CORROSIVE_LOC);
                                if (corrosion == null) corrosion = ForgeRegistries.POTIONS.getValue(CORROSION_LOC);
                                if (corrosion != null) {
                                    attacker.addPotionEffect(new PotionEffect(corrosion, 80, 0));
                                }
                            }
                        }

                        // Apply Blazing Might
                        Potion blazingMight = ForgeRegistries.POTIONS.getValue(BLAZING_MIGHT_LOC);
                        if (blazingMight != null && !player.world.isRemote) {
                            PotionEffect currentEffect = player.getActivePotionEffect(blazingMight);
                            int amplifier = currentEffect != null ? Math.min(currentEffect.getAmplifier() + 1, 4) : 0;
                            player.addPotionEffect(new PotionEffect(blazingMight, 300, amplifier, false, true));
                        }

                        // Mana Drain
                        if (shieldItem instanceof IManaStoringItem) {
                            IManaStoringItem manaItem = (IManaStoringItem) shieldItem;
                            int manaDrain = Math.max(1, (int) event.getAmount());
                            int newMana = Math.max(0, manaItem.getMana(activeStack) - manaDrain);
                            manaItem.setMana(activeStack, newMana);
                        }
                }
            }
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();

        // --- Aegis Player Logic ---
        if (entity instanceof EntityPlayer && !entity.world.isRemote) {
            EntityPlayer player = (EntityPlayer) entity;
            ItemStack main = player.getHeldItemMainhand();
            ItemStack off = player.getHeldItemOffhand();

            boolean hasAegis = (main.getItem() == ModItems.PARASITE_AEGIS) || (off.getItem() == ModItems.PARASITE_AEGIS);

            if (hasAegis) {
                // Anti-Axe Guard
                Item aegis = ModItems.PARASITE_AEGIS;
                if (aegis != null && player.getCooldownTracker().hasCooldown(aegis)) {
                    player.getCooldownTracker().removeCooldown(aegis);
                }

                // Extinguish feature (Every 1.5 seconds)
                if (player.ticksExisted % 30 == 0 && player.isBurning()) {
                    player.extinguish();
                }
            }
        }

        // --- Fast Fire System (For enemies hit by shield) ---
        if (!entity.world.isRemote) {
            NBTTagCompound data = entity.getEntityData();
            if (data.hasKey("AegisBurn")) {
                int burnTicks = data.getInteger("AegisBurn");

                if (burnTicks > 0) {
                    burnTicks--;
                    data.setInteger("AegisBurn", burnTicks);

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
                }
            }
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) return;

        ItemStack main = player.getHeldItemMainhand();
        ItemStack off = player.getHeldItemOffhand();

        boolean hasAegisEquipped = (main.getItem() == ModItems.PARASITE_AEGIS) || (off.getItem() == ModItems.PARASITE_AEGIS);

        if (hasAegisEquipped) {
            // Remove blazing might upon any health loss
            Potion blazingMight = ForgeRegistries.POTIONS.getValue(BLAZING_MIGHT_LOC);
            if (blazingMight != null && player.isPotionActive(blazingMight)) {
                player.removePotionEffect(blazingMight);
            }

            // Backstab Verification
            ItemStack activeStack = player.getActiveItemStack();
            boolean isAegisRaised = player.isHandActive() && !activeStack.isEmpty() && activeStack.getItem() == ModItems.PARASITE_AEGIS;

            DamageSource source = event.getSource();
            Vec3d damageLoc = source.getDamageLocation();
            if (event.getAmount() > 0 && damageLoc != null && isAegisRaised) {
                Vec3d viewVec = player.getLook(1.0f);
                Vec3d posVec = player.getPositionVector();
                if (viewVec != null && posVec != null) {
                    Vec3d toDamage = damageLoc.subtract(posVec).normalize();
                    if (toDamage != null) {
                        toDamage = new Vec3d(toDamage.x, 0.0, toDamage.z);

                        // Hits from behind deal +50% DMG
                        if (toDamage.dotProduct(viewVec) < 0.0) {
                            event.setAmount(event.getAmount() * 1.5f);
                        }
                    }
                }
            }
        }
    }
}
