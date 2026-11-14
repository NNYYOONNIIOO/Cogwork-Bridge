package com.cogwork.cogworkbridge.content.decoration;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.monster.EntityShulker;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.List;

public final class SeatBlock extends Block {
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0, 0, 0, 1, .5D, 1);
    private static final AxisAlignedBB PLAYER_COLLISION = new AxisAlignedBB(0, 0, 0, 1, 3D / 16D, 1);
    private static final AxisAlignedBB ENTITY_COLLISION = new AxisAlignedBB(0, 0, 0, 1, 6D / 16D, 1);
    private static final String[] DYES = {"dyeWhite", "dyeOrange", "dyeMagenta", "dyeLightBlue",
            "dyeYellow", "dyeLime", "dyePink", "dyeGray", "dyeLightGray", "dyeCyan",
            "dyePurple", "dyeBlue", "dyeBrown", "dyeGreen", "dyeRed", "dyeBlack"};
    private final int color;

    public SeatBlock(int color) {
        super(Material.CLOTH);
        this.color = color;
        setHardness(.8F);
        setLightOpacity(0);
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return SHAPE; }
    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        addCollisionBoxToList(pos, query, boxes, entity instanceof EntityPlayer ? PLAYER_COLLISION : ENTITY_COLLISION);
    }

    @Override public void onFallenUpon(World world, BlockPos pos, Entity entity, float distance) {
        entity.fall(distance * .5F, 1F);
    }

    @Override public void onLanded(World world, Entity entity) {
        BlockPos pos = new BlockPos(entity.posX, entity.posY - .01D, entity.posZ);
        boolean occupied = !world.getEntitiesWithinAABB(SeatEntity.class, new AxisAlignedBB(pos)).isEmpty();
        if (entity instanceof EntityLiving living && !(entity instanceof EntityShulker) && !occupied
                && !entity.isRiding() && world.getBlockState(pos).getBlock() == this) {
            if (!world.isRemote) {
                SeatEntity seat = new SeatEntity(world);
                seat.setPosition(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
                world.spawnEntity(seat);
                mount(living, seat);
            }
            return;
        }
        if (entity.isSneaking()) super.onLanded(world, entity);
        else if (entity.motionY < 0D) entity.motionY = -entity.motionY * .66D * (entity instanceof EntityLivingBase ? 1D : .8D);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float x, float y, float z) {
        if (player.isSneaking() || player instanceof FakePlayer) return false;
        ItemStack held = player.getHeldItem(hand);
        int dye = dyeColor(held);
        if (dye >= 0 && dye != color) {
            if (!world.isRemote) world.setBlockState(pos, AllBlocks.SEATS[dye].getDefaultState(), 3);
            return true;
        }
        List<SeatEntity> seats = world.getEntitiesWithinAABB(SeatEntity.class, new AxisAlignedBB(pos));
        if (!seats.isEmpty() && !seats.get(0).getPassengers().isEmpty()
                && seats.get(0).getPassengers().get(0) instanceof EntityPlayer) return false;
        if (world.isRemote) return true;
        Entity passenger = player;
        for (EntityLiving living : world.getEntitiesWithinAABB(EntityLiving.class, player.getEntityBoundingBox().grow(10))) {
            if (!(living instanceof EntityShulker) && living.getLeashed() && living.getLeashHolder() == player) {
                passenger = living;
                living.clearLeashed(true, true);
                break;
            }
        }
        SeatEntity seat = seats.isEmpty() ? new SeatEntity(world) : seats.get(0);
        if (seats.isEmpty()) {
            seat.setPosition(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
            world.spawnEntity(seat);
        } else seat.removePassengers();
        mount(passenger, seat);
        return true;
    }

    private static boolean mount(Entity passenger, SeatEntity seat) {
        boolean mounted = passenger.startRiding(seat, true);
        if (mounted && passenger instanceof net.minecraft.entity.passive.EntityTameable tameable) tameable.setSitting(true);
        return mounted;
    }

    public static int dyeColor(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        if (stack.getItem() == Items.DYE) return EnumDyeColor.byDyeDamage(stack.getMetadata()).getMetadata();
        for (int id : OreDictionary.getOreIDs(stack)) {
            String name = OreDictionary.getOreName(id);
            for (int i = 0; i < DYES.length; i++) if (DYES[i].equals(name)) return i;
        }
        return -1;
    }

    public static boolean sitOnContraption(EntityPlayer player, com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity parent, BlockPos local) {
        if (player.world.isRemote || player.isSneaking() || player instanceof FakePlayer) return false;
        net.minecraft.util.math.Vec3d center = parent.localBlockCenterToWorld(local);
        SeatEntity found = null;
        for (SeatEntity seat : player.world.getEntitiesWithinAABB(SeatEntity.class, new AxisAlignedBB(center.x - 1, center.y - 1, center.z - 1, center.x + 1, center.y + 1, center.z + 1)))
            if (seat.belongsTo(parent, local)) { found = seat; break; }
        if (found != null && !found.getPassengers().isEmpty() && found.getPassengers().get(0) instanceof EntityPlayer) return false;
        Entity passenger = player;
        for (EntityLiving living : player.world.getEntitiesWithinAABB(EntityLiving.class, player.getEntityBoundingBox().grow(10))) {
            if (!(living instanceof EntityShulker) && living.getLeashed() && living.getLeashHolder() == player) {
                passenger = living; living.clearLeashed(true, true); break;
            }
        }
        if (found == null) {
            found = new SeatEntity(player.world); found.bind(parent, local); player.world.spawnEntity(found);
        } else found.removePassengers();
        return mount(passenger, found);
    }

    /** Keep occupants mounted when an existing seat is captured into a structure. */
    public static void transferOccupants(com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity parent,
                                         com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraption structure) {
        World world = parent.world;
        if (world.isRemote) return;
        for (var snapshot : structure.blocks().values()) {
            if (!(snapshot.state().getBlock() instanceof SeatBlock)) continue;
            BlockPos pos = snapshot.pos();
            BlockPos local = pos.subtract(structure.anchor());
            for (SeatEntity seat : world.getEntitiesWithinAABB(SeatEntity.class, new AxisAlignedBB(pos).grow(.01D))) {
                if (seat.isStaticSeat() && !seat.getPassengers().isEmpty()) seat.bind(parent, local);
            }
        }
    }
}
