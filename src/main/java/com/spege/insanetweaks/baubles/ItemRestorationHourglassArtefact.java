package com.spege.insanetweaks.baubles;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.util.SrpOriginSnapshotHelper;

import electroblob.wizardry.item.ItemArtefact;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Artefakt: Hourglass of Restoration (przejęte 1:1 z dawnej Zhonyi)
 *
 * Slot: CHARM (noszony w slocie charm / trzymany w hotbarze gdy Baubles niedostępne).
 *
 * Tryby działania:
 *
 * 1. PASYWNY — Purifying Pulse:
 *    EntityPurifyingWave wywołuje tryRestoreInRange().
 *    Wszystkie zarażone entity SRP w zasięgu fali są przywracane
 *    do oryginalnej postaci z pełnymi atrybutami (dzięki snapshotom).
 *
 * 2. AKTYWNY — Creative Right-Click:
 *    Gracz w trybie Creative celuje w zarażone entity i klika PPM.
 *    Entity zostaje natychmiast przywrócone bez potrzeby fali.
 *
 * System snapshotów:
 *    MixinParasiteEventEntity przechwytuje KAŻDE entity tuż przed
 *    usunięciem przez spawnInsider() i convertEntity(). Pełne NBT
 *    (imię, właściciel, oswojenie, atrybuty) jest zapisywane globalnie.
 *    ZhonyasEventHandler stosuje snapshot do nowo spawnanego SRP entity,
 *    skąd artefakt go odczytuje przy przywracaniu.
 *
 *    Incomplete Form (EntityInhooM/S): mobs bez odpowiednika w SRP — np. modded
 *    entity jak ebwizardry:wizard — trafiają do InhooM lub InhooS.
 *    Snapshot zachowuje pełne ID + NBT, umożliwiając perfekcyjne odtworzenie
 *    dowolnego moda, jeśli snapshot jest dostępny.
 *
 * Efekt po przywróceniu:
 *    performRestore() nakłada na przywrócone entity 30-sekundowy EPEL_E
 *    (ochrona przed ponowną asymilacją/transformacją) + clearCoth.
 */
@SuppressWarnings("null")
public class ItemRestorationHourglassArtefact extends ItemArtefact {

    /** Minimalny promień sprawdzania w zasięgu fali. */
    private static final double RESTORE_RADIUS  = 12.0D;
    /** Zasięg ray-trace dla trybu Creative. */
    private static final double CREATIVE_REACH  = 20.0D;

    public ItemRestorationHourglassArtefact() {
        // CHARM = slot "charm/totem" w EBWizardry (najbliższy do TOTEM).
        // Fallback działa automatycznie: gdy Baubles niedostępne, EBWizardry
        // sprawdza hotbar/offhand gracza dla danego Type.
        super(EnumRarity.RARE, Type.CHARM);
        this.setRegistryName("restoration_hourglass");
        this.setUnlocalizedName("insanetweaks.restoration_hourglass");
    }

    // ─────────────────────────────────────────────────────────────────
    // Pasywny tryb: wywoływany przez EntityPurifyingWave
    // ─────────────────────────────────────────────────────────────────

    /**
     * Przywraca wszystkie zarażone SRP entity w zasięgu fali puryfikacji.
     * Wywoływany z EntityPurifyingWave.onUpdate() gdy gracz ma CHARM aktywny.
     *
     * Nie używa cooldownu per-entity — performRestore() wykonuje srpEntity.setDead(),
     * więc entity natychmiast przestaje istnieć po przywróceniu i nie może ponownie
     * trafić do pętli w tej samej fali.
     *
     * UWAGA: Gdy owner (rzucający) wyjdzie poza zasięg chunku lub rozłączy się
     * podczas aktywnej fali, this.getOwner() zwróci null i przywrócenie zostanie
     * pominięte. Jest to znane ograniczenie projektowe.
     */
    public static void tryRestoreInRange(World world, double ox, double oy, double oz,
            double radius, @Nullable EntityLivingBase owner) {
        if (world.isRemote) return;
        if (!(owner instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) owner;
        if (!ItemArtefact.isArtefactActive(player, ModItems.RESTORATION_HOURGLASS)) return;

        double r = Math.max(radius, RESTORE_RADIUS);
        AxisAlignedBB area = new AxisAlignedBB(ox - r, oy - 5, oz - r, ox + r, oy + 5, oz + r);

        for (EntityLivingBase entity : world.getEntitiesWithinAABB(EntityLivingBase.class, area)) {
            if (!entity.isEntityAlive()) continue;
            if (!SrpOriginSnapshotHelper.isSrpConvertedEntity(entity)) continue;
            doRestore(entity, world, player);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Aktywny tryb: Right-Click (Ray-trace)
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, @Nonnull EntityPlayer player,
            @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        // Ray-trace wzdłuż wektora wzroku
        Vec3d eyes = player.getPositionEyes(1.0F);
        Vec3d look = player.getLookVec();
        Vec3d end   = eyes.addVector(
            look.x * CREATIVE_REACH, look.y * CREATIVE_REACH, look.z * CREATIVE_REACH);

        AxisAlignedBB searchBox = player.getEntityBoundingBox()
            .grow(CREATIVE_REACH)
            .expand(look.x * CREATIVE_REACH, look.y * CREATIVE_REACH, look.z * CREATIVE_REACH);

        EntityLivingBase target = null;
        double closest = Double.MAX_VALUE;

        for (EntityLivingBase candidate : world.getEntitiesWithinAABB(EntityLivingBase.class, searchBox)) {
            if (candidate == player || !candidate.isEntityAlive()) continue;
            if (!SrpOriginSnapshotHelper.isSrpConvertedEntity(candidate)) continue;

            RayTraceResult rtr = candidate.getEntityBoundingBox().grow(0.3)
                .calculateIntercept(eyes, end);
            if (rtr == null) continue;

            double dist = eyes.distanceTo(rtr.hitVec);
            if (dist < closest) {
                closest = dist;
                target  = candidate;
            }
        }

        if (target == null) {
            player.sendMessage(new TextComponentString(
                TextFormatting.GRAY + "[Restoration] No infected entity in sight."));
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        Entity restored = doRestore(target, world, player);
        if (restored != null) {
            player.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "[Restoration] Restored: " + restored.getName()));
            
            // Nakładamy 6-godzinny cooldown (432 000 ticków) jeśli gracz nie jest na creative.
            if (!player.isCreative()) {
                player.getCooldownTracker().setCooldown(this, 432000);
            }
            
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        player.sendMessage(new TextComponentString(
            TextFormatting.RED + "[Restoration] No origin data — cannot restore."));
        return new ActionResult<>(EnumActionResult.FAIL, stack);
    }

    // ─────────────────────────────────────────────────────────────────
    // Wewnętrzna logika przywracania
    // ─────────────────────────────────────────────────────────────────

    @Nullable
    private static Entity doRestore(EntityLivingBase srpEntity, World world,
            @Nullable EntityPlayer instigator) {
        String vanillaId = SrpOriginSnapshotHelper.resolveVanillaId(srpEntity);
        if (vanillaId == null) {
            InsaneTweaksMod.LOGGER.debug(
                "[IT][Restoration] Brak ID dla {} ({})",
                srpEntity.getClass().getSimpleName(),
                SrpOriginSnapshotHelper.isIncompleteForm(srpEntity) ? "IncompleteForm — brak snapshotu" : "brak mapowania");
            return null;
        }

        Entity restored = SrpOriginSnapshotHelper.performRestore(srpEntity, world, vanillaId);
        if (restored == null) return null;

        // Efekty wizualne po stronie serwera
        double ex = srpEntity.posX;
        double ey = srpEntity.posY + srpEntity.height * 0.5D;
        double ez = srpEntity.posZ;

        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SPELL_INSTANT,
                ex, ey, ez, 25,
                srpEntity.width * 0.4D, srpEntity.height * 0.3D, srpEntity.width * 0.4D, 0.02D);
            ws.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                ex, ey, ez, 12,
                srpEntity.width * 0.5D, srpEntity.height * 0.4D, srpEntity.width * 0.5D, 0.03D);
            ws.spawnParticle(EnumParticleTypes.HEART,
                ex, ey + srpEntity.height * 0.5D, ez,
                5, 0.3D, 0.2D, 0.3D, 0.05D);
        }

        world.playSound(null, ex, ey, ez,
            SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE, SoundCategory.NEUTRAL, 1.0F, 1.2F);

        InsaneTweaksMod.LOGGER.info(
            "[IT][Restoration] Restored '{}' → '{}' at {},{},{}",
            srpEntity.getClass().getSimpleName(), vanillaId,
            (int) ex, (int) srpEntity.posY, (int) ez);

        return restored;
    }

    // ─────────────────────────────────────────────────────────────────
    // Tooltip
    // ─────────────────────────────────────────────────────────────────

    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + "A moment of the past, bottled and returned.");
        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + "Entity Restoration");
        tooltip.add(TextFormatting.GRAY + "When using Purifying Pulse spell,");
        tooltip.add(TextFormatting.GRAY + "restores infected mobs to their original form,");
        tooltip.add(TextFormatting.GRAY + "preserving name, taming & owner data.");
        tooltip.add(TextFormatting.GRAY + "Incomplete Forms (modded/unknown mobs)");
        tooltip.add(TextFormatting.GRAY + "are fully restored if snapshot available.");
        tooltip.add("");
        tooltip.add(TextFormatting.YELLOW + "Restored entities gain §630s §7re-infection immunity.");
        tooltip.add("");
        tooltip.add(TextFormatting.GOLD + "" + TextFormatting.ITALIC
            + "Right-click: instant restore. [6 h cooldown]");
    }

    @Override
    @Nonnull
    public net.minecraftforge.common.IRarity getForgeRarity(@Nonnull ItemStack stack) {
        return EnumRarity.RARE;
    }
}
