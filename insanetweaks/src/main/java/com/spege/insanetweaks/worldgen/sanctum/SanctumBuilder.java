package com.spege.insanetweaks.worldgen.sanctum;

import java.util.HashSet;
import java.util.Set;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.anchor.AnchorPool;
import com.spege.insanetweaks.anchor.TileEntityPetrifiedAnchor;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.ArcaneSanctumCategory;
import com.spege.insanetweaks.init.ModBlocks;
import com.spege.insanetweaks.init.ModElements;

import electroblob.wizardry.registry.WizardryBlocks;
import electroblob.wizardry.tileentity.TileEntityImbuementAltar;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Writes the part of a {@link SanctumLayout} inside a clip box. Passes: (1) walls - every room in
 * stone brick, then the ritual chamber's shell in sealed stone over any shared plane; (2) every
 * interior except the stairway's, then carves (doorways, stair headroom) to air; (3) seal, dais,
 * altars, pillars, stairs, props, chests, anchors, lights; (4) webs and rubble; (5) shaft and
 * surface ruin. Materials depend on position only (SanctumNoise), so a structure split over many
 * populate calls is identical to one built at once. Containers get the sanctum's Arcane Lock.
 */
public final class SanctumBuilder {

    public static final ResourceLocation MAIN_LOOT = new ResourceLocation(InsaneTweaksMod.MODID, "chests/arcane_sanctum");
    public static final ResourceLocation MINOR_LOOT = new ResourceLocation(InsaneTweaksMod.MODID,
            "chests/arcane_sanctum_minor");
    public static final ResourceLocation FORGE_LOOT = new ResourceLocation(InsaneTweaksMod.MODID,
            "chests/arcane_sanctum_forge");
    public static final ResourceLocation WORKSHOP_LOOT = new ResourceLocation(InsaneTweaksMod.MODID,
            "chests/arcane_sanctum_workshop");

    private static final int FLAGS = 2;
    private static final Set<String> WARNED = new HashSet<String>();

    private SanctumBuilder() {
    }

    public static void build(World world, SanctumLayout l, SanctumBox clip, long sanctumId) {
        Palette p = new Palette();
        for (SanctumLayout.Room room : l.rooms) {
            if (room.type != SanctumLayout.RoomType.CHAMBER) {
                fillWalls(world, l, room.outer, clip, p);
            }
        }
        fill(world, l.rooms.get(0).outer, clip, p.sealed());
        for (SanctumLayout.Room room : l.rooms) {
            SanctumBox in = room.outer.shrink(1);
            if (in != null && room.type != SanctumLayout.RoomType.DESCENT) {
                fill(world, in, clip, Blocks.AIR.getDefaultState());
            }
        }
        for (SanctumBox carve : l.carves) {
            fill(world, carve, clip, Blocks.AIR.getDefaultState());
        }
        contents(world, l, clip, p, sanctumId);
        for (SanctumLayout.Room room : l.rooms) {
            decorate(world, l, room, clip);
        }
        shaft(world, l, clip, p);
    }

    // ---- passes -----------------------------------------------------------------------------

    private static void fillWalls(World world, SanctumLayout l, SanctumBox box, SanctumBox clip, Palette p) {
        SanctumBox c = box.intersection(clip);
        if (c == null) {
            return;
        }
        for (int x = c.minX; x <= c.maxX; x++) {
            for (int y = c.minY; y <= c.maxY; y++) {
                for (int z = c.minZ; z <= c.maxZ; z++) {
                    world.setBlockState(new BlockPos(x, y, z), p.wall(l.seed, x, y, z), FLAGS);
                }
            }
        }
    }

    private static void fill(World world, SanctumBox box, SanctumBox clip, IBlockState state) {
        SanctumBox c = box.intersection(clip);
        if (c == null) {
            return;
        }
        for (int x = c.minX; x <= c.maxX; x++) {
            for (int y = c.minY; y <= c.maxY; y++) {
                for (int z = c.minZ; z <= c.maxZ; z++) {
                    world.setBlockState(new BlockPos(x, y, z), state, FLAGS);
                }
            }
        }
    }

    private static void contents(World world, SanctumLayout l, SanctumBox clip, Palette p, long sanctumId) {
        ArcaneSanctumCategory cfg = ModConfig.arcaneSanctum;
        for (int[] f : l.sealFrame) {
            setIn(world, clip, f[0], f[1], f[2], p.chiseled());
        }
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
        fill(world, l.dais, clip, p.chiseled());
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
        for (int[] pillar : l.pillars) {
            for (int y = l.floorY2; y <= l.floorY2 + 7; y++) {
                setIn(world, clip, pillar[0], y, pillar[1], p.sealed());
            }
        }
        IBlockState stair = Blocks.STONE_BRICK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,
                facing(l.stairFacing));
        for (int[] s : l.stairs) {
            setIn(world, clip, s[0], s[1], s[2], stair);
        }
        for (int[] prop : l.props) {
            if (!clip.contains(prop[0], prop[1], prop[2])) {
                continue;
            }
            BlockPos pos = new BlockPos(prop[0], prop[1], prop[2]);
            world.setBlockState(pos, withFacing(p.prop(prop[3]), facing(prop[4])), FLAGS);
            if (prop[3] == SanctumLayout.PROP_EBW_SHELF) {
                TileEntity te = world.getTileEntity(pos);
                if (te instanceof IInventory) {
                    SanctumBooks.fill((IInventory) te, l.seed, prop[0], prop[1], prop[2], cfg.bookshelfBookRolls,
                            cfg.bookshelfBookChance);
                }
                if (te != null) {
                    SanctumLocks.lock(te, sanctumId);
                }
            }
        }
        for (int[] c : l.chests) {
            if (!clip.contains(c[0], c[1], c[2])) {
                continue;
            }
            BlockPos pos = new BlockPos(c[0], c[1], c[2]);
            world.setBlockState(pos, Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, facing(c[3])), FLAGS);
            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof TileEntityChest)) {
                continue;
            }
            TileEntityChest chest = (TileEntityChest) te;
            chest.setLootTable(table(c[4]), SanctumNoise.seedAt(l.seed, c[0], c[1], c[2]));
            if (c[4] == SanctumLayout.CHEST_MAIN && SanctumNoise.at(l.seed, c[0], c[1], c[2], 70) < cfg.mainChestBaubleChance) {
                ItemStack bauble = SanctumBaubles.pick(SanctumNoise.at(l.seed, c[0], c[1], c[2], 71));
                if (!bauble.isEmpty()) {
                    int slot = (int) (SanctumNoise.at(l.seed, c[0], c[1], c[2], 72) * chest.getSizeInventory());
                    chest.setInventorySlotContents(Math.min(slot, chest.getSizeInventory() - 1), bauble);
                }
            }
            SanctumLocks.lock(chest, sanctumId);
        }
        AnchorPool roomPool = AnchorPool.parse(cfg.roomAnchorPool);
        for (int[] a : l.anchors) {
            if (!clip.contains(a[0], a[1], a[2])) {
                continue;
            }
            BlockPos pos = new BlockPos(a[0], a[1], a[2]);
            world.setBlockState(pos, ModBlocks.PETRIFIED_ANCHOR.getDefaultState(), FLAGS);
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityPetrifiedAnchor) {
                TileEntityPetrifiedAnchor anchor = (TileEntityPetrifiedAnchor) te;
                anchor.setSanctum(sanctumId);
                if (a[3] == SanctumLayout.POOL_ROOM) {
                    String id = roomPool.pick(SanctumNoise.at(l.seed, a[0], a[1], a[2], 80));
                    if (id != null) {
                        anchor.pinAtGeneration(id);
                    }
                }
            }
        }
        for (int[] light : l.lights) {
            setIn(world, clip, light[0], light[1], light[2], Blocks.GLOWSTONE.getDefaultState());
        }
    }

    /** Webs on top-layer edges everywhere; rubble on the floor of crypts and collapsed rooms. */
    private static void decorate(World world, SanctumLayout l, SanctumLayout.Room room, SanctumBox clip) {
        SanctumBox in = room.outer.shrink(1);
        if (in == null || room.type == SanctumLayout.RoomType.DESCENT) {
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
                    } else if (y == in.minY && room.type == SanctumLayout.RoomType.CRYPT && n < 0.12D) {
                        world.setBlockState(pos, n < 0.05D ? Blocks.BONE_BLOCK.getDefaultState()
                                : Blocks.MOSSY_COBBLESTONE.getDefaultState(), FLAGS);
                    } else if (y == in.minY && room.type == SanctumLayout.RoomType.COLLAPSED && n < 0.3D) {
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
        for (int y = l.floorY; y < l.wardY; y++) {
            setIn(world, clip, l.ladderSupport[0], y, l.ladderSupport[2],
                    p.wall(l.seed, l.ladderSupport[0], y, l.ladderSupport[2]));
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = sx + dx;
                int z = sz + dz;
                if (!clip.contains(x, l.wardY, z)) {
                    continue;
                }
                int surface = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)).getY() - 1;
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
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

    private static ResourceLocation table(int kind) {
        switch (kind) {
            case SanctumLayout.CHEST_MAIN:
                return MAIN_LOOT;
            case SanctumLayout.CHEST_FORGE:
                return FORGE_LOOT;
            case SanctumLayout.CHEST_WORKSHOP:
                return WORKSHOP_LOOT;
            default:
                return MINOR_LOOT;
        }
    }

    private static boolean isDust(SanctumLayout l, int[] altar) {
        for (int[] d : l.dustAltars) {
            if (d[0] == altar[0] && d[1] == altar[1] && d[2] == altar[2]) {
                return true;
            }
        }
        return false;
    }

    /** Doorway approaches stay clear of rubble and webs. */
    private static boolean inDoorLine(SanctumLayout l, int x, int z) {
        for (SanctumBox d : l.carves) {
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

    private static void setIn(World world, SanctumBox clip, int x, int y, int z, IBlockState state) {
        if (clip.contains(x, y, z)) {
            set(world, x, y, z, state);
        }
    }

    /** Sets a horizontal "facing" property if the block has one (EBW shelf, ASC stations, anvil...). */
    @SuppressWarnings("unchecked")
    private static IBlockState withFacing(IBlockState state, EnumFacing facing) {
        for (IProperty<?> prop : state.getPropertyKeys()) {
            if ("facing".equals(prop.getName()) && prop.getValueClass() == EnumFacing.class
                    && prop.getAllowedValues().contains(facing)) {
                return state.withProperty((IProperty<EnumFacing>) prop, facing);
            }
        }
        return state;
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

        IBlockState prop(int kind) {
            switch (kind) {
                case SanctumLayout.PROP_EBW_SHELF:
                    return WizardryBlocks.oak_bookshelf.getDefaultState();
                case SanctumLayout.PROP_ENCHANTING_TABLE:
                    return Blocks.ENCHANTING_TABLE.getDefaultState();
                case SanctumLayout.PROP_ARCANE_WORKBENCH:
                    return WizardryBlocks.arcane_workbench.getDefaultState();
                case SanctumLayout.PROP_ARCANE_ANVIL:
                    return lookup("ancientspellcraft:arcane_anvil");
                case SanctumLayout.PROP_ANVIL:
                    return Blocks.ANVIL.getDefaultState();
                case SanctumLayout.PROP_FURNACE:
                    return Blocks.FURNACE.getDefaultState();
                case SanctumLayout.PROP_SCRIBING_DESK:
                    return lookup("ancientspellcraft:scribing_desk");
                case SanctumLayout.PROP_SAGE_DESK:
                    return lookup("ancientspellcraft:sage_desk");
                case SanctumLayout.PROP_SAGE_LECTERN:
                    return lookup("ancientspellcraft:sage_lectern");
                default:
                    return Blocks.CRAFTING_TABLE.getDefaultState();
            }
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
