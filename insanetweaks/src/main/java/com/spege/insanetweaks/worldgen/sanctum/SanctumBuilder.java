package com.spege.insanetweaks.worldgen.sanctum;

import java.util.HashSet;
import java.util.Set;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.init.ModBlocks;
import com.spege.insanetweaks.init.ModElements;

import electroblob.wizardry.registry.WizardryBlocks;
import electroblob.wizardry.tileentity.TileEntityImbuementAltar;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Writes the part of a {@link SanctumLayout} that lies inside a clip box. Three passes over every
 * room, so doorways cut through neighbours' walls whatever order the chunks arrive in:
 * (1) every outer box becomes wall, (2) every interior and doorway becomes air, (3) contents.
 * Materials come from {@link SanctumNoise}, which depends on position only, so a structure split
 * across several chunks' populate calls is identical to one built at once.
 *
 * <p>The shaft walls and the surface ruin use the surface height of their OWN column: a writer
 * never reads terrain outside the area its populate call may touch.
 */
public final class SanctumBuilder {

    public static final ResourceLocation MAIN_LOOT = new ResourceLocation(InsaneTweaksMod.MODID, "chests/arcane_sanctum");
    public static final ResourceLocation MINOR_LOOT = new ResourceLocation(InsaneTweaksMod.MODID,
            "chests/arcane_sanctum_minor");

    private static final int FLAGS = 2;
    private static final Set<String> WARNED = new HashSet<String>();

    private SanctumBuilder() {
    }

    public static void build(World world, SanctumLayout l, SanctumBox clip) {
        Palette p = new Palette();
        for (SanctumLayout.Room room : l.rooms) {
            fill(world, l, room.outer, clip, p, true);
        }
        for (SanctumLayout.Room room : l.rooms) {
            SanctumBox in = room.outer.shrink(1);
            if (in != null) {
                fill(world, l, in, clip, p, false);
            }
        }
        for (SanctumBox door : l.doorways) {
            fill(world, l, door, clip, p, false);
        }
        contents(world, l, clip, p);
        for (SanctumLayout.Room room : l.rooms) {
            decorate(world, l, room, clip, p);
        }
        shaft(world, l, clip, p);
    }

    // ---- passes -----------------------------------------------------------------------------

    private static void fill(World world, SanctumLayout l, SanctumBox box, SanctumBox clip, Palette p, boolean wall) {
        SanctumBox c = box.intersection(clip);
        if (c == null) {
            return;
        }
        for (int x = c.minX; x <= c.maxX; x++) {
            for (int y = c.minY; y <= c.maxY; y++) {
                for (int z = c.minZ; z <= c.maxZ; z++) {
                    world.setBlockState(new BlockPos(x, y, z), wall ? p.wall(l.seed, x, y, z) : Blocks.AIR.getDefaultState(),
                            FLAGS);
                }
            }
        }
    }

    private static void contents(World world, SanctumLayout l, SanctumBox clip, Palette p) {
        // Dais, then altars on it.
        SanctumBox dais = l.dais.intersection(clip);
        if (dais != null) {
            for (int x = dais.minX; x <= dais.maxX; x++) {
                for (int z = dais.minZ; z <= dais.maxZ; z++) {
                    set(world, x, dais.minY, z, p.chiseled());
                }
            }
        }
        for (int i = 0; i < l.altars.size(); i++) {
            int[] a = l.altars.get(i);
            if (!clip.contains(a[0], a[1], a[2])) {
                continue;
            }
            BlockPos pos = new BlockPos(a[0], a[1], a[2]);
            world.setBlockState(pos, WizardryBlocks.imbuement_altar.getDefaultState(), FLAGS);
            ItemStack stack = i == 4 ? crystal() : isDust(l, a) ? abominationDust() : ItemStack.EMPTY;
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityImbuementAltar && !stack.isEmpty()) {
                ((TileEntityImbuementAltar) te).setStack(stack);
            }
        }
        // Pillars span the chamber interior height.
        for (int[] pillar : l.pillars) {
            for (int y = l.floorY; y <= l.floorY + 6; y++) {
                if (clip.contains(pillar[0], y, pillar[1])) {
                    set(world, pillar[0], y, pillar[1], p.sealed());
                }
            }
        }
        // Seal plug with the unseal button at its centre.
        SanctumBox seal = l.seal.intersection(clip);
        if (seal != null) {
            for (int x = seal.minX; x <= seal.maxX; x++) {
                for (int y = seal.minY; y <= seal.maxY; y++) {
                    for (int z = seal.minZ; z <= seal.maxZ; z++) {
                        boolean centre = x == l.button[0] && y == l.button[1] && z == l.button[2];
                        set(world, x, y, z, centre ? p.button() : p.sealed());
                    }
                }
            }
        }
        for (int[] a : l.anchors) {
            if (clip.contains(a[0], a[1], a[2])) {
                set(world, a[0], a[1], a[2], ModBlocks.PETRIFIED_ANCHOR.getDefaultState());
            }
        }
        for (int[] c : l.chests) {
            if (!clip.contains(c[0], c[1], c[2])) {
                continue;
            }
            BlockPos pos = new BlockPos(c[0], c[1], c[2]);
            world.setBlockState(pos, Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, facing(c[3])), FLAGS);
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityChest) {
                ((TileEntityChest) te).setLootTable(c[4] == 1 ? MAIN_LOOT : MINOR_LOOT,
                        SanctumNoise.seedAt(l.seed, c[0], c[1], c[2]));
            }
        }
        for (int[] light : l.lights) {
            if (clip.contains(light[0], light[1], light[2])) {
                set(world, light[0], light[1], light[2], Blocks.GLOWSTONE.getDefaultState());
            }
        }
    }

    /** Room flavour: webs everywhere, then the side-room type's furniture or rubble. */
    private static void decorate(World world, SanctumLayout l, SanctumLayout.Room room, SanctumBox clip, Palette p) {
        SanctumBox in = room.outer.shrink(1);
        if (in == null || room.type == SanctumLayout.RoomType.CONNECTOR) {
            return;
        }
        SanctumBox c = in.intersection(clip);
        if (c == null) {
            return;
        }
        for (int x = c.minX; x <= c.maxX; x++) {
            for (int z = c.minZ; z <= c.maxZ; z++) {
                boolean edge = x == in.minX || x == in.maxX || z == in.minZ || z == in.maxZ;
                for (int y = c.minY; y <= c.maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.isAirBlock(pos) || inDoorLine(l, x, z)) {
                        continue;
                    }
                    double n = SanctumNoise.at(l.seed, x, y, z, 7);
                    if (y == in.maxY && edge && n < 0.18D) {
                        world.setBlockState(pos, Blocks.WEB.getDefaultState(), FLAGS);
                        continue;
                    }
                    if (y != in.minY) {
                        if (room.type == SanctumLayout.RoomType.LIBRARY && edge && y <= in.minY + 2) {
                            world.setBlockState(pos, n < 0.2D ? p.ebwShelf() : Blocks.BOOKSHELF.getDefaultState(), FLAGS);
                        }
                        continue;
                    }
                    if (room.type == SanctumLayout.RoomType.CRYPT && n < 0.12D) {
                        world.setBlockState(pos, n < 0.05D ? Blocks.BONE_BLOCK.getDefaultState()
                                : Blocks.MOSSY_COBBLESTONE.getDefaultState(), FLAGS);
                    } else if (room.type == SanctumLayout.RoomType.COLLAPSED && n < 0.3D) {
                        world.setBlockState(pos, n < 0.15D ? Blocks.GRAVEL.getDefaultState()
                                : Blocks.COBBLESTONE.getDefaultState(), FLAGS);
                    }
                }
            }
        }
    }

    /** Shaft walls, ladders, ward and the surface ruin, each from its own column's surface. */
    private static void shaft(World world, SanctumLayout l, SanctumBox clip, Palette p) {
        int sx = l.shaftX;
        int sz = l.shaftZ;
        IBlockState ladder = ModBlocks.ARCANE_LADDER.getDefaultState().withProperty(BlockLadder.FACING,
                facing(l.ladderFacing));
        // Support column inside the vestibule, under the shaft's ladder wall.
        for (int y = l.floorY; y < l.wardY; y++) {
            if (clip.contains(l.ladderSupport[0], y, l.ladderSupport[2])) {
                set(world, l.ladderSupport[0], y, l.ladderSupport[2], p.wall(l.seed, l.ladderSupport[0], y,
                        l.ladderSupport[2]));
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = sx + dx;
                int z = sz + dz;
                if (!clip.contains(x, l.wardY, z)) {
                    continue;
                }
                int surface = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)).getY() - 1;
                boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (ring) {
                    ruinCell(world, l, x, z, surface, p);
                } else if (dx == 0 && dz == 0) {
                    for (int y = l.floorY; y <= Math.max(surface, l.wardY + 1); y++) {
                        set(world, x, y, z, y == l.wardY ? ModBlocks.ARCANE_WARD.getDefaultState() : ladder);
                    }
                } else {
                    for (int y = l.wardY; y <= Math.max(surface, l.wardY); y++) {
                        set(world, x, y, z, p.wall(l.seed, x, y, z));
                    }
                }
            }
        }
    }

    private static void ruinCell(World world, SanctumLayout l, int x, int z, int surface, Palette p) {
        double n = SanctumNoise.at(l.seed, x, 0, z, 11);
        if (n < 0.3D) {
            int height = n < 0.12D ? 2 : 1;
            for (int h = 1; h <= height; h++) {
                set(world, x, surface + h, z, p.ruin(l.seed, x, surface + h, z));
            }
        } else if (n < 0.42D) {
            set(world, x, surface, z, Blocks.MOSSY_COBBLESTONE.getDefaultState());
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static boolean isDust(SanctumLayout l, int[] altar) {
        for (int[] d : l.dustAltars) {
            if (d[0] == altar[0] && d[1] == altar[1] && d[2] == altar[2]) {
                return true;
            }
        }
        return false;
    }

    /** The straight line through every doorway stays clear of rubble and shelves. */
    private static boolean inDoorLine(SanctumLayout l, int x, int z) {
        for (SanctumBox d : l.doorways) {
            if (x >= d.minX - 1 && x <= d.maxX + 1 && z >= d.minZ - 1 && z <= d.maxZ + 1) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack crystal() {
        Item item = Item.getByNameOrId("ebwizardry:magic_crystal");
        return item == null ? ItemStack.EMPTY : new ItemStack(item, 1, 0);
    }

    private static ItemStack abominationDust() {
        Item item = Item.getByNameOrId("ebwizardry:spectral_dust");
        if (item == null || !ModElements.EXTENDED) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, 1, ModElements.ABOMINATION.ordinal());
    }

    private static void set(World world, int x, int y, int z, IBlockState state) {
        world.setBlockState(new BlockPos(x, y, z), state, FLAGS);
    }

    private static EnumFacing facing(int index) {
        switch (index) {
            case 0:
                return EnumFacing.NORTH;
            case 1:
                return EnumFacing.EAST;
            case 2:
                return EnumFacing.SOUTH;
            default:
                return EnumFacing.WEST;
        }
    }

    /** Block choices, with id lookups for ASC/EBW blocks and a stone-brick fallback. */
    private static final class Palette {
        private final IBlockState plain = Blocks.STONEBRICK.getDefaultState();
        private final IBlockState mossy = plain.withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.MOSSY);
        private final IBlockState cracked = plain.withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED);
        private final IBlockState chiseled = plain.withProperty(BlockStoneBrick.VARIANT,
                BlockStoneBrick.EnumType.CHISELED);
        private final IBlockState sealed = lookup("ancientspellcraft:sealed_stone");
        private final IBlockState button = lookup("ancientspellcraft:unseal_button");
        private final IBlockState ebwShelf = WizardryBlocks.oak_bookshelf.getDefaultState();

        IBlockState wall(long seed, int x, int y, int z) {
            double n = SanctumNoise.at(seed, x, y, z, 3);
            return n < 0.6D ? this.plain : n < 0.85D ? this.mossy : this.cracked;
        }

        IBlockState ruin(long seed, int x, int y, int z) {
            return SanctumNoise.at(seed, x, y, z, 5) < 0.5D ? this.mossy : this.cracked;
        }

        IBlockState chiseled() {
            return this.chiseled;
        }

        IBlockState sealed() {
            return this.sealed;
        }

        IBlockState button() {
            return this.button;
        }

        IBlockState ebwShelf() {
            return this.ebwShelf;
        }

        private IBlockState lookup(String id) {
            Block block = Block.getBlockFromName(id);
            if (block == null || block == Blocks.AIR) {
                if (WARNED.add(id)) {
                    InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Sunken Arcane Sanctum: block '{}' is missing; "
                            + "using chiseled stone bricks instead.", id);
                }
                return this.chiseled;
            }
            return block.getDefaultState();
        }
    }
}
