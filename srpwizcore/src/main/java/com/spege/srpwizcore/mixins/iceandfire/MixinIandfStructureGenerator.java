package com.spege.srpwizcore.mixins.iceandfire;

import java.util.Random;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.github.alexthe666.iceandfire.IceAndFireConfig;
import com.github.alexthe666.iceandfire.event.StructureGenerator;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.util.IandfLastPosStore;
import com.spege.srpwizcore.util.IandfWorldgenOverrides;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

/**
 * Per-dimension control over Ice&amp;Fire worldgen.
 *
 * <p>Ice&amp;Fire funnels every structure it generates through one method —
 * {@code StructureGenerator.generate(Random, int, int, World, IChunkGenerator, IChunkProvider)} —
 * which reads its on/off and chance settings straight out of the public fields of
 * {@code IceAndFireConfig$WorldGenConfig}. Those settings are global, so the mod can only offer
 * three coarse dimension gates. Redirecting each of those field reads and answering with a
 * per-dimension value turns every structure into an independently dimension-scoped feature,
 * without touching a single line of Ice&amp;Fire's placement logic.
 *
 * <p><b>Fall-through is the contract.</b> Every handler asks
 * {@link IandfWorldgenOverrides} for this dimension, and hands back Ice&amp;Fire's own field value
 * whenever the config has nothing to say — which is also what happens for every dimension when the
 * config is empty or the master switch is off. No dimension id appears anywhere in this class.
 *
 * <p><b>Ores are a special case.</b> Ice&amp;Fire has no chance field for them (confirmed by javap
 * on {@code WorldGenConfig}: {@code generateCopperOre} and friends are plain booleans), and the
 * boolean is read once per chunk right before the ore's placement loop. So a configured number is
 * applied as a per-chunk veto: {@code 0=true:6} lets the pass run in roughly one chunk out of six.
 *
 * <p><b>Spacing.</b> See {@link IandfLastPosStore} — every read and write of the ten shared
 * anti-clustering positions is redirected to a per-dimension ConcurrentHashMap, otherwise a
 * structure placed in one dimension suppresses the same structure in another (and the map guards
 * against corruption under this pack's off-thread worldgen).
 *
 * <p><b>Known limitation.</b> For dragon roosts and dens, Ice&amp;Fire consults the per-biome
 * chance maps ({@code Generate Dragon Roosts/Dens Biome Name Chance} in {@code iceandfire.cfg})
 * <i>before</i> reading the global chance field. In a biome that has such an entry the per-biome
 * number wins and our chance override never comes into play. The enable/disable override still
 * applies, because it is checked earlier. Documented in the config comments as well.
 *
 * <p>Target verified with {@code javap -p -c} on {@code Ice and Fire-2.2.9.jar} (2026-07-24):
 * every redirected field read was attributed to its exact offset inside {@code generate}.
 * Ice&amp;Fire is a mod class, so all selectors are literal jar names with {@code remap = false};
 * the handler bodies use MCP names, which reobf maps.
 */
@Mixin(value = com.github.alexthe666.iceandfire.event.StructureGenerator.class, remap = false)
public abstract class MixinIandfStructureGenerator {

    private static final String GENERATE = "generate(Ljava/util/Random;IILnet/minecraft/world/World;"
            + "Lnet/minecraft/world/gen/IChunkGenerator;Lnet/minecraft/world/chunk/IChunkProvider;)V";

    private static final String CFG = "Lcom/github/alexthe666/iceandfire/IceAndFireConfig$WorldGenConfig;";

    // ---------------------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------------------

    private static boolean srpwizcore$enabled(boolean original, String key, World world) {
        Boolean override = IandfWorldgenOverrides.enabledFor(key, world.provider.getDimension());
        return override == null ? original : override.booleanValue();
    }

    private static int srpwizcore$chance(int original, String key, World world) {
        Integer override = IandfWorldgenOverrides.chanceFor(key, world.provider.getDimension());
        if (override == null) {
            return original;
        }
        int value = override.intValue();
        // Ice&Fire feeds this straight into Random.nextInt, which throws on values below 1.
        return value < 1 ? original : value;
    }

    /**
     * Ore fields are boolean-only in Ice&Fire, so a configured number becomes a per-chunk veto:
     * the whole placement pass runs in roughly 1 chunk out of N. Randomness comes from the
     * {@code Random} the chunk populator handed to {@code generate}, so the result is
     * deterministic for a given seed and chunk.
     */
    private static boolean srpwizcore$ore(boolean original, String key, World world, Random rand) {
        int dim = world.provider.getDimension();
        Boolean enabled = IandfWorldgenOverrides.enabledFor(key, dim);
        if (enabled == null) {
            return original;
        }
        if (!enabled.booleanValue()) {
            return false;
        }
        Integer divisor = IandfWorldgenOverrides.chanceFor(key, dim);
        if (divisor == null || divisor.intValue() <= 1) {
            return true;
        }
        return rand.nextInt(divisor.intValue()) == 0;
    }

    // ---------------------------------------------------------------------------------------
    // Enable flags
    // ---------------------------------------------------------------------------------------

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateGorgonTemple:Z"))
    private boolean srpwizcore$gorgonTempleEnabled(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$enabled(cfg.generateGorgonTemple, IandfWorldgenOverrides.GORGON_TEMPLE, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateMausoleums:Z"))
    private boolean srpwizcore$mausoleumsEnabled(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$enabled(cfg.generateMausoleums, IandfWorldgenOverrides.MAUSOLEUMS, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateSirenIslands:Z"))
    private boolean srpwizcore$sirenIslandsEnabled(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$enabled(cfg.generateSirenIslands, IandfWorldgenOverrides.SIREN_ISLANDS, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateCyclopsCaves:Z"))
    private boolean srpwizcore$cyclopsCavesEnabled(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$enabled(cfg.generateCyclopsCaves, IandfWorldgenOverrides.CYCLOPS_CAVES, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateWanderingCyclops:Z"))
    private boolean srpwizcore$wanderingCyclopsEnabled(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$enabled(cfg.generateWanderingCyclops, IandfWorldgenOverrides.WANDERING_CYCLOPS, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generatePixieVillages:Z"))
    private boolean srpwizcore$pixieVillagesEnabled(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$enabled(cfg.generatePixieVillages, IandfWorldgenOverrides.PIXIE_VILLAGES, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateHydraCaves:Z"))
    private boolean srpwizcore$hydraCavesEnabled(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$enabled(cfg.generateHydraCaves, IandfWorldgenOverrides.HYDRA_CAVES, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateDragonRoosts:Z"))
    private boolean srpwizcore$dragonRoostsEnabled(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$enabled(cfg.generateDragonRoosts, IandfWorldgenOverrides.DRAGON_ROOSTS, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateDragonDens:Z"))
    private boolean srpwizcore$dragonDensEnabled(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$enabled(cfg.generateDragonDens, IandfWorldgenOverrides.DRAGON_DENS, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateDragonSkeletons:Z"))
    private boolean srpwizcore$dragonSkeletonsEnabled(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$enabled(cfg.generateDragonSkeletons, IandfWorldgenOverrides.DRAGON_SKELETONS, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateMyrmexColonies:Z"))
    private boolean srpwizcore$myrmexColoniesEnabled(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$enabled(cfg.generateMyrmexColonies, IandfWorldgenOverrides.MYRMEX_COLONIES, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateSnowVillages:Z"))
    private boolean srpwizcore$snowVillagesEnabled(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$enabled(cfg.generateSnowVillages, IandfWorldgenOverrides.SNOW_VILLAGES, world);
    }

    // ---------------------------------------------------------------------------------------
    // Chance fields ("1 in N")
    // ---------------------------------------------------------------------------------------

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateGorgonChance:I"))
    private int srpwizcore$gorgonChance(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$chance(cfg.generateGorgonChance, IandfWorldgenOverrides.GORGON_TEMPLE, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateMausoleumChance:I"))
    private int srpwizcore$mausoleumChance(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$chance(cfg.generateMausoleumChance, IandfWorldgenOverrides.MAUSOLEUMS, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateSirenChance:I"))
    private int srpwizcore$sirenChance(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$chance(cfg.generateSirenChance, IandfWorldgenOverrides.SIREN_ISLANDS, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateCyclopsChance:I"))
    private int srpwizcore$cyclopsChance(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$chance(cfg.generateCyclopsChance, IandfWorldgenOverrides.CYCLOPS_CAVES, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateWanderingCyclopsChance:I"))
    private int srpwizcore$wanderingCyclopsChance(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$chance(cfg.generateWanderingCyclopsChance,
                IandfWorldgenOverrides.WANDERING_CYCLOPS, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generatePixieChance:I"))
    private int srpwizcore$pixieChance(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$chance(cfg.generatePixieChance, IandfWorldgenOverrides.PIXIE_VILLAGES, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateHydrasChance:I"))
    private int srpwizcore$hydrasChance(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$chance(cfg.generateHydrasChance, IandfWorldgenOverrides.HYDRA_CAVES, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateDragonRoostChance:I"))
    private int srpwizcore$dragonRoostChance(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$chance(cfg.generateDragonRoostChance, IandfWorldgenOverrides.DRAGON_ROOSTS, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateDragonDenChance:I"))
    private int srpwizcore$dragonDenChance(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$chance(cfg.generateDragonDenChance, IandfWorldgenOverrides.DRAGON_DENS, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateDragonSkeletonChance:I"))
    private int srpwizcore$dragonSkeletonChance(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$chance(cfg.generateDragonSkeletonChance,
                IandfWorldgenOverrides.DRAGON_SKELETONS, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "myrmexColonyGenChance:I"))
    private int srpwizcore$myrmexColonyChance(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$chance(cfg.myrmexColonyGenChance, IandfWorldgenOverrides.MYRMEX_COLONIES, world);
    }

    // ---------------------------------------------------------------------------------------
    // Ores (boolean-only in Ice&Fire — configured number is our per-chunk veto divisor)
    // ---------------------------------------------------------------------------------------

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateCopperOre:Z"))
    private boolean srpwizcore$copperOre(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$ore(cfg.generateCopperOre, IandfWorldgenOverrides.ORE_COPPER, world, rand);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateSilverOre:Z"))
    private boolean srpwizcore$silverOre(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$ore(cfg.generateSilverOre, IandfWorldgenOverrides.ORE_SILVER, world, rand);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateAmethystOre:Z"))
    private boolean srpwizcore$amethystOre(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$ore(cfg.generateAmethystOre, IandfWorldgenOverrides.ORE_AMETHYST, world, rand);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateRubyOre:Z"))
    private boolean srpwizcore$rubyOre(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$ore(cfg.generateRubyOre, IandfWorldgenOverrides.ORE_RUBY, world, rand);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = CFG + "generateSapphireOre:Z"))
    private boolean srpwizcore$sapphireOre(IceAndFireConfig.WorldGenConfig cfg,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$ore(cfg.generateSapphireOre, IandfWorldgenOverrides.ORE_SAPPHIRE, world, rand);
    }

    // ---------------------------------------------------------------------------------------
    // Per-dimension anti-clustering positions (plan Task 5, PRIMARY approach).
    //
    // Each last* field is read (twice) and written (once) inside generate to enforce Ice&Fire's
    // minimum spacing between structures of the same kind. Those fields belong to the single
    // registered StructureGenerator instance, so they are shared by every dimension: once worldgen
    // runs in two dimensions the stored position from one bleeds into the distance check of the
    // other. We redirect every read and write of all ten fields to IandfLastPosStore, keyed by
    // dimension. The backing map is a ConcurrentHashMap, so concurrent worldgen across dimensions
    // (this pack runs chunk gen off the server thread) neither leaks positions between dimensions
    // nor corrupts the map. Ice&Fire's distance arithmetic is untouched — only the storage moves.
    //
    // When the fix is off, each handler falls straight through to the real field (via @Shadow),
    // so behaviour is byte-for-byte native Ice&Fire, including its own within-dimension race.
    //
    // Redirect-handler shapes (sponge-mixin 0.8.7):
    //   GETFIELD -> (owner, <generate args...>) : BlockPos
    //   PUTFIELD -> (owner, BlockPos value, <generate args...>) : void
    // The trailing generate args are captured so the handler can read world.provider.getDimension().
    // ---------------------------------------------------------------------------------------

    private static final String SG = "Lcom/github/alexthe666/iceandfire/event/StructureGenerator;";
    private static final String BP = ":Lnet/minecraft/util/math/BlockPos;";

    @Shadow private BlockPos lastMausoleum;
    @Shadow private BlockPos lastDragonRoost;
    @Shadow private BlockPos lastDragonCave;
    @Shadow private BlockPos lastCyclopsCave;
    @Shadow private BlockPos lastMyrmexHive;
    @Shadow private BlockPos lastSnowVillage;
    @Shadow private BlockPos lastPixieVillage;
    @Shadow private BlockPos lastHydraCave;
    @Shadow private BlockPos lastSirenIsland;
    @Shadow private BlockPos lastGorgonTemple;

    private static boolean srpwizcore$perDimSpacing() {
        return SrpWizCoreConfig.iandfWorldgen.enableIandfWorldgenControl
                && SrpWizCoreConfig.iandfWorldgen.fixCrossDimStructureSpacing;
    }

    /** Shared read path: native field value when off, per-dimension stored value when on. */
    private static BlockPos srpwizcore$readLast(BlockPos nativeValue, String key, World world) {
        if (!srpwizcore$perDimSpacing()) {
            return nativeValue;
        }
        return IandfLastPosStore.get(world.provider.getDimension(), key);
    }

    // ---- lastMausoleum ----
    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = SG + "lastMausoleum" + BP))
    private BlockPos srpwizcore$getLastMausoleum(StructureGenerator owner,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$readLast(this.lastMausoleum, IandfLastPosStore.MAUSOLEUM, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = SG + "lastMausoleum" + BP))
    private void srpwizcore$putLastMausoleum(StructureGenerator owner, BlockPos value,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (srpwizcore$perDimSpacing()) {
            IandfLastPosStore.put(world.provider.getDimension(), IandfLastPosStore.MAUSOLEUM, value);
        } else {
            this.lastMausoleum = value;
        }
    }

    // ---- lastDragonRoost ----
    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = SG + "lastDragonRoost" + BP))
    private BlockPos srpwizcore$getLastDragonRoost(StructureGenerator owner,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$readLast(this.lastDragonRoost, IandfLastPosStore.DRAGON_ROOST, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = SG + "lastDragonRoost" + BP))
    private void srpwizcore$putLastDragonRoost(StructureGenerator owner, BlockPos value,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (srpwizcore$perDimSpacing()) {
            IandfLastPosStore.put(world.provider.getDimension(), IandfLastPosStore.DRAGON_ROOST, value);
        } else {
            this.lastDragonRoost = value;
        }
    }

    // ---- lastDragonCave ----
    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = SG + "lastDragonCave" + BP))
    private BlockPos srpwizcore$getLastDragonCave(StructureGenerator owner,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$readLast(this.lastDragonCave, IandfLastPosStore.DRAGON_CAVE, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = SG + "lastDragonCave" + BP))
    private void srpwizcore$putLastDragonCave(StructureGenerator owner, BlockPos value,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (srpwizcore$perDimSpacing()) {
            IandfLastPosStore.put(world.provider.getDimension(), IandfLastPosStore.DRAGON_CAVE, value);
        } else {
            this.lastDragonCave = value;
        }
    }

    // ---- lastCyclopsCave ----
    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = SG + "lastCyclopsCave" + BP))
    private BlockPos srpwizcore$getLastCyclopsCave(StructureGenerator owner,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$readLast(this.lastCyclopsCave, IandfLastPosStore.CYCLOPS_CAVE, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = SG + "lastCyclopsCave" + BP))
    private void srpwizcore$putLastCyclopsCave(StructureGenerator owner, BlockPos value,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (srpwizcore$perDimSpacing()) {
            IandfLastPosStore.put(world.provider.getDimension(), IandfLastPosStore.CYCLOPS_CAVE, value);
        } else {
            this.lastCyclopsCave = value;
        }
    }

    // ---- lastMyrmexHive ----
    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = SG + "lastMyrmexHive" + BP))
    private BlockPos srpwizcore$getLastMyrmexHive(StructureGenerator owner,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$readLast(this.lastMyrmexHive, IandfLastPosStore.MYRMEX_HIVE, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = SG + "lastMyrmexHive" + BP))
    private void srpwizcore$putLastMyrmexHive(StructureGenerator owner, BlockPos value,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (srpwizcore$perDimSpacing()) {
            IandfLastPosStore.put(world.provider.getDimension(), IandfLastPosStore.MYRMEX_HIVE, value);
        } else {
            this.lastMyrmexHive = value;
        }
    }

    // ---- lastSnowVillage ----
    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = SG + "lastSnowVillage" + BP))
    private BlockPos srpwizcore$getLastSnowVillage(StructureGenerator owner,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$readLast(this.lastSnowVillage, IandfLastPosStore.SNOW_VILLAGE, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = SG + "lastSnowVillage" + BP))
    private void srpwizcore$putLastSnowVillage(StructureGenerator owner, BlockPos value,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (srpwizcore$perDimSpacing()) {
            IandfLastPosStore.put(world.provider.getDimension(), IandfLastPosStore.SNOW_VILLAGE, value);
        } else {
            this.lastSnowVillage = value;
        }
    }

    // ---- lastPixieVillage ----
    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = SG + "lastPixieVillage" + BP))
    private BlockPos srpwizcore$getLastPixieVillage(StructureGenerator owner,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$readLast(this.lastPixieVillage, IandfLastPosStore.PIXIE_VILLAGE, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = SG + "lastPixieVillage" + BP))
    private void srpwizcore$putLastPixieVillage(StructureGenerator owner, BlockPos value,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (srpwizcore$perDimSpacing()) {
            IandfLastPosStore.put(world.provider.getDimension(), IandfLastPosStore.PIXIE_VILLAGE, value);
        } else {
            this.lastPixieVillage = value;
        }
    }

    // ---- lastHydraCave ----
    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = SG + "lastHydraCave" + BP))
    private BlockPos srpwizcore$getLastHydraCave(StructureGenerator owner,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$readLast(this.lastHydraCave, IandfLastPosStore.HYDRA_CAVE, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = SG + "lastHydraCave" + BP))
    private void srpwizcore$putLastHydraCave(StructureGenerator owner, BlockPos value,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (srpwizcore$perDimSpacing()) {
            IandfLastPosStore.put(world.provider.getDimension(), IandfLastPosStore.HYDRA_CAVE, value);
        } else {
            this.lastHydraCave = value;
        }
    }

    // ---- lastSirenIsland ----
    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = SG + "lastSirenIsland" + BP))
    private BlockPos srpwizcore$getLastSirenIsland(StructureGenerator owner,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$readLast(this.lastSirenIsland, IandfLastPosStore.SIREN_ISLAND, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = SG + "lastSirenIsland" + BP))
    private void srpwizcore$putLastSirenIsland(StructureGenerator owner, BlockPos value,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (srpwizcore$perDimSpacing()) {
            IandfLastPosStore.put(world.provider.getDimension(), IandfLastPosStore.SIREN_ISLAND, value);
        } else {
            this.lastSirenIsland = value;
        }
    }

    // ---- lastGorgonTemple ----
    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = SG + "lastGorgonTemple" + BP))
    private BlockPos srpwizcore$getLastGorgonTemple(StructureGenerator owner,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        return srpwizcore$readLast(this.lastGorgonTemple, IandfLastPosStore.GORGON_TEMPLE, world);
    }

    @Redirect(method = GENERATE, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = SG + "lastGorgonTemple" + BP))
    private void srpwizcore$putLastGorgonTemple(StructureGenerator owner, BlockPos value,
            Random rand, int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (srpwizcore$perDimSpacing()) {
            IandfLastPosStore.put(world.provider.getDimension(), IandfLastPosStore.GORGON_TEMPLE, value);
        } else {
            this.lastGorgonTemple = value;
        }
    }
}
