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

import com.windanesz.ancientspellcraft.tileentity.TileRune;

import electroblob.wizardry.registry.WizardryBlocks;
import electroblob.wizardry.tileentity.TileEntityImbuementAltar;
import electroblob.wizardry.tileentity.TileEntityTimer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockEndPortalFrame;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockSilverfish;
import net.minecraft.block.BlockSkull;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.BlockTripWire;
import net.minecraft.block.BlockTripWireHook;
import net.minecraft.block.BlockVine;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Writes the part of a {@link SanctumLayout} inside a clip box. Passes: (1) walls - every room in
 * stone brick (a few silverfish blocks among them), then the ritual chamber's shell in sealed
 * stone over any shared plane; (2) every interior except the stairway's, then carves (doorways,
 * stair headroom) to air; (3) seal, dais, altars, pillars, stairs, props, traps, chests, anchors,
 * lights, decor; (4) webs, vines and rubble; (5) shaft and surface ruin. Materials depend on position only (SanctumNoise), so a structure split over many
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
        SanctumWorldData data = SanctumWorldData.get(world);
        if (SanctumHull.shouldBuild(data.box(sanctumId) != null, data.isCleared(sanctumId))) {
            hull(world, l, clip);
        }
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

    /** The arcane-wall hull: the surface of every room's outer box grown by one, where it is hull. */
    private static void hull(World world, SanctumLayout l, SanctumBox clip) {
        IBlockState wall = ModBlocks.ARCANE_WALL.getDefaultState();
        for (SanctumLayout.Room room : l.rooms) {
            SanctumBox o = room.outer;
            SanctumBox g = new SanctumBox(o.minX - 1, o.minY - 1, o.minZ - 1, o.maxX + 1, o.maxY + 1, o.maxZ + 1);
            SanctumBox c = g.intersection(clip);
            if (c == null) {
                continue;
            }
            for (int x = c.minX; x <= c.maxX; x++) {
                for (int y = c.minY; y <= c.maxY; y++) {
                    for (int z = c.minZ; z <= c.maxZ; z++) {
                        boolean surface = x == g.minX || x == g.maxX || y == g.minY || y == g.maxY || z == g.minZ
                                || z == g.maxZ;
                        if (surface && l.isHull(x, y, z)) {
                            world.setBlockState(new BlockPos(x, y, z), wall, FLAGS);
                        }
                    }
                }
            }
        }
    }

    private static void fillWalls(World world, SanctumLayout l, SanctumBox box, SanctumBox clip, Palette p) {
        SanctumBox c = box.intersection(clip);
        if (c == null) {
            return;
        }
        for (int x = c.minX; x <= c.maxX; x++) {
            for (int y = c.minY; y <= c.maxY; y++) {
                for (int z = c.minZ; z <= c.maxZ; z++) {
                    IBlockState wall = p.wall(l.seed, x, y, z);
                    if (SanctumNoise.at(l.seed, x, y, z, 9) < 0.015D) {
                        wall = p.infested(wall);
                    }
                    world.setBlockState(new BlockPos(x, y, z), wall, FLAGS);
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
            if (prop[3] == SanctumLayout.PROP_SHULKER) {
                potionBox(world, l, pos, sanctumId);
                continue;
            }
            world.setBlockState(pos, withFacing(p.prop(prop[3]), facing(prop[4])), FLAGS);
            if (prop[3] == SanctumLayout.PROP_EBW_SHELF) {
                TileEntity te = world.getTileEntity(pos);
                if (te instanceof IInventory) {
                    SanctumBooks.fill((IInventory) te, l.seed, prop[0], prop[1], prop[2], cfg.bookshelfBookRolls,
                            cfg.bookshelfBookChance);
                    SanctumBooks.fillExtras((IInventory) te, l.seed, prop[0], prop[1], prop[2], cfg.bookshelfExtraRolls,
                            cfg.bookshelfTreasureChance, cfg.bookshelfInsaneBookChance);
                }
                if (te != null) {
                    SanctumLocks.lock(te, sanctumId);
                }
            }
        }
        for (int[] d : l.trapDispensers) {
            if (!clip.contains(d[0], d[1], d[2])) {
                continue;
            }
            BlockPos pos = new BlockPos(d[0], d[1], d[2]);
            world.setBlockState(pos, Blocks.DISPENSER.getDefaultState().withProperty(BlockDispenser.FACING,
                    facing(d[3])), FLAGS);
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof IInventory) {
                SanctumTraps.load((IInventory) te, l.seed, d[0], d[1], d[2]);
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
        for (int[] light : l.magicLights) {
            if (!clip.contains(light[0], light[1], light[2])) {
                continue;
            }
            BlockPos pos = new BlockPos(light[0], light[1], light[2]);
            world.setBlockState(pos, WizardryBlocks.magic_light.getDefaultState(), FLAGS);
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityTimer) {
                ((TileEntityTimer) te).setLifetime(-1);
            }
        }
        for (int[] spot : l.glowSpots) {
            if (SanctumNoise.at(l.seed, spot[0], spot[1], spot[2], 12) < 0.15D) {
                setIn(world, clip, spot[0], spot[1], spot[2], Blocks.GLOWSTONE.getDefaultState());
            }
        }
        for (int[] d : l.decor) {
            decor(world, l, clip, d, sanctumId);
        }
        ritual(world, l, clip);
        int[] lectern = l.codexLectern;
        if (lectern != null && clip.contains(lectern[0], lectern[1], lectern[2])) {
            // The codex lectern stays empty until the guide book exists (spec 15.3).
            world.setBlockState(new BlockPos(lectern[0], lectern[1], lectern[2]),
                    withFacing(p.prop(SanctumLayout.PROP_SAGE_LECTERN), facing(lectern[3])), FLAGS);
        }
    }

    /**
     * The ritual room: ASC runes laid without their centrepiece, and an unlocked chest holding the
     * hint book. A rune block or item missing from ASC skips that rune.
     */
    private static void ritual(World world, SanctumLayout l, SanctumBox clip) {
        if (l.ritualKind < 0) {
            return;
        }
        Block runeBlock = Block.getBlockFromName("ancientspellcraft:placed_rune");
        EnumFacing face = facing(l.hintChest[3]);
        for (int[] r : l.runes) {
            if (runeBlock == null || runeBlock == Blocks.AIR || !clip.contains(r[0], r[1], r[2])) {
                continue;
            }
            Item rune = Item.getByNameOrId(SanctumRituals.rune(r[3], (char) r[4]));
            if (rune == null) {
                continue;
            }
            BlockPos pos = new BlockPos(r[0], r[1], r[2]);
            world.setBlockState(pos, runeBlock.getDefaultState(), FLAGS);
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileRune) {
                ((TileRune) te).setRune(rune);
                ((TileRune) te).setDirection(face);
            }
        }
        int[] c = l.hintChest;
        if (clip.contains(c[0], c[1], c[2])) {
            BlockPos pos = new BlockPos(c[0], c[1], c[2]);
            world.setBlockState(pos, Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, face), FLAGS);
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityChest) {
                ((TileEntityChest) te).setInventorySlotContents(13, SanctumRitualBook.create(l.ritualKind));
            }
        }
    }

    /** A purple shulker box of potions, under the sanctum's lock. */
    private static void potionBox(World world, SanctumLayout l, BlockPos pos, long sanctumId) {
        world.setBlockState(pos, Blocks.PURPLE_SHULKER_BOX.getDefaultState(), FLAGS);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof IInventory) {
            SanctumPotions.fill((IInventory) te, l.seed, pos.getX(), pos.getY(), pos.getZ());
        }
        if (te != null) {
            SanctumLocks.lock(te, sanctumId);
        }
    }

    private static void decor(World world, SanctumLayout l, SanctumBox clip, int[] d, long sanctumId) {
        BlockPos pos = new BlockPos(d[0], d[1], d[2]);
        EnumFacing face = facing(d[4]);
        if (d[3] == SanctumLayout.DECOR_BED) {
            EnumDyeColor colour = EnumDyeColor.byMetadata((int) (SanctumNoise.at(l.seed, d[0], d[1], d[2], 120) * 16));
            int[] head = SanctumLayout.bedHead(d);
            bedPart(world, clip, pos, face, BlockBed.EnumPartType.FOOT, colour);
            bedPart(world, clip, new BlockPos(head[0], head[1], head[2]), face, BlockBed.EnumPartType.HEAD, colour);
            return;
        }
        if (d[3] == SanctumLayout.DECOR_PAINTING) {
            // The painting tests surfaces for art up to two blocks out: stay well inside this clip,
            // so it never reads a chunk outside the populated area. The cell is the air in front of
            // the wall, which is what EntityHanging's hanging position means.
            if (clip.contains(d[0] - 3, d[1], d[2] - 3) && clip.contains(d[0] + 3, d[1], d[2] + 3)) {
                EntityPainting painting = new EntityPainting(world, pos, face);
                if (painting.onValidSurface()) {
                    world.spawnEntity(painting);
                }
            }
            return;
        }
        if (!clip.contains(d[0], d[1], d[2])) {
            return;
        }
        switch (d[3]) {
            case SanctumLayout.DECOR_ARMOR_STAND:
                armorStand(world, l, d, face);
                break;
            case SanctumLayout.DECOR_SHULKER:
                potionBox(world, l, pos, sanctumId);
                break;
            case SanctumLayout.DECOR_ENDER_CHEST:
                world.setBlockState(pos, withFacing(Blocks.ENDER_CHEST.getDefaultState(), face), FLAGS);
                break;
            case SanctumLayout.DECOR_PORTAL_FRAME:
                world.setBlockState(pos, Blocks.END_PORTAL_FRAME.getDefaultState()
                        .withProperty(BlockEndPortalFrame.FACING, face)
                        .withProperty(BlockEndPortalFrame.EYE, Boolean.valueOf(d[5] == 1)), FLAGS);
                break;
            case SanctumLayout.DECOR_SKULL:
                world.setBlockState(pos, Blocks.SKULL.getDefaultState().withProperty(BlockSkull.FACING, EnumFacing.UP),
                        FLAGS);
                TileEntity te = world.getTileEntity(pos);
                if (te instanceof TileEntitySkull) {
                    ((TileEntitySkull) te).setType(d[5] == 1 ? 1 : 0);
                    ((TileEntitySkull) te).setSkullRotation((int) (SanctumNoise.at(l.seed, d[0], d[1], d[2], 121) * 16));
                }
                break;
            default:
                break;
        }
    }

    private static void bedPart(World world, SanctumBox clip, BlockPos pos, EnumFacing face, BlockBed.EnumPartType part,
            EnumDyeColor colour) {
        if (!clip.contains(pos.getX(), pos.getY(), pos.getZ())) {
            return;
        }
        world.setBlockState(pos, Blocks.BED.getDefaultState().withProperty(BlockBed.FACING, face)
                .withProperty(BlockBed.PART, part), FLAGS);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityBed) {
            ((TileEntityBed) te).setColor(colour);
        }
    }

    private static final Item[][] ARMOUR = {
            { Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS },
            { Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS },
            { Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS } };
    private static final EntityEquipmentSlot[] ARMOUR_SLOTS = { EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST,
            EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET };

    /** An armour stand wearing one or two pieces of iron, chain or gold, slots picked by position. */
    private static void armorStand(World world, SanctumLayout l, int[] d, EnumFacing face) {
        EntityArmorStand stand = new EntityArmorStand(world);
        stand.setLocationAndAngles(d[0] + 0.5D, d[1], d[2] + 0.5D, face.getHorizontalAngle(), 0.0F);
        int pieces = SanctumNoise.at(l.seed, d[0], d[1], d[2], 110) < 0.5D ? 1 : 2;
        int first = Math.min(3, (int) (SanctumNoise.at(l.seed, d[0], d[1], d[2], 111) * 4));
        int step = 1 + Math.min(2, (int) (SanctumNoise.at(l.seed, d[0], d[1], d[2], 112) * 3));
        for (int i = 0; i < pieces; i++) {
            int slot = (first + i * step) & 3;
            int material = Math.min(2, (int) (SanctumNoise.at(l.seed, d[0], d[1], d[2], 113 + i) * 3));
            stand.setItemStackToSlot(ARMOUR_SLOTS[slot], new ItemStack(ARMOUR[material][slot]));
        }
        world.spawnEntity(stand);
    }

    /**
     * Webs on top-layer edges everywhere; vines on the walls between floor and ceiling; rubble on
     * the floor of crypts and collapsed rooms.
     */
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
                    } else if (edge && y > in.minY && y < in.maxY && SanctumNoise.at(l.seed, x, y, z, 13) < 0.06D
                            && !anchorColumn(l, x, z)) {
                        EnumFacing wall = x == in.minX ? EnumFacing.WEST : x == in.maxX ? EnumFacing.EAST
                                : z == in.minZ ? EnumFacing.NORTH : EnumFacing.SOUTH;
                        world.setBlockState(pos, Blocks.VINE.getDefaultState()
                                .withProperty(BlockVine.getPropertyFor(wall), Boolean.TRUE), FLAGS);
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

    /** Vines never grow over an anchor: they would fill the space its statue needs. */
    private static boolean anchorColumn(SanctumLayout l, int x, int z) {
        for (int[] a : l.anchors) {
            if (a[0] == x && a[2] == z) {
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

        /** The silverfish block that looks like the given wall brick. */
        IBlockState infested(IBlockState wall) {
            BlockSilverfish.EnumType type = wall == this.mossy ? BlockSilverfish.EnumType.MOSSY_STONEBRICK
                    : wall == this.cracked ? BlockSilverfish.EnumType.CRACKED_STONEBRICK
                            : BlockSilverfish.EnumType.STONEBRICK;
            return Blocks.MONSTER_EGG.getDefaultState().withProperty(BlockSilverfish.VARIANT, type);
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
                case SanctumLayout.PROP_SAGE_LECTERN:
                    return lookup("ancientspellcraft:sage_lectern");
                case SanctumLayout.PROP_SPHERE_COGNIZANCE:
                    return lookup("ancientspellcraft:sphere_cognizance");
                case SanctumLayout.PROP_TRANSPORT_STONE:
                    return WizardryBlocks.transportation_stone.getDefaultState();
                case SanctumLayout.PROP_GILDED_WOOD:
                    return WizardryBlocks.gilded_wood.getDefaultState();
                case SanctumLayout.PROP_BOOKSHELF:
                    return Blocks.BOOKSHELF.getDefaultState();
                case SanctumLayout.PROP_TRIP_HOOK:
                    return Blocks.TRIPWIRE_HOOK.getDefaultState().withProperty(BlockTripWireHook.ATTACHED, Boolean.TRUE);
                case SanctumLayout.PROP_TRIPWIRE:
                    return Blocks.TRIPWIRE.getDefaultState().withProperty(BlockTripWire.ATTACHED, Boolean.TRUE);
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
