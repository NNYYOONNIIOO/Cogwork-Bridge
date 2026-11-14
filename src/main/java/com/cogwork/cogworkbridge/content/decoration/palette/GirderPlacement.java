package com.cogwork.cogworkbridge.content.decoration.palette;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.BlockFamily;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Extension of a girder along the beam through the selected part. */
public final class GirderPlacement {
    private static final int RANGE = 16;
    private GirderPlacement() { }
    public record Placement(BlockPos pos, EnumFacing direction, IBlockState state) { }

    public static Placement find(World world, BlockPos origin, IBlockState state, EntityPlayer player,
                                 ItemStack held, EnumFacing face, float x, float y, float z) {
        if (player.isSneaking()) return null;
        Block girder = BlockFamily.get("metal_girder");
        if (girder == null || Block.getBlockFromItem(held.getItem()) != girder) return null;
        List<EnumFacing> directions = new ArrayList<>();
        for (EnumFacing direction : EnumFacing.VALUES) if (canExtend(world, origin, state, direction)) directions.add(direction);
        directions.sort(Comparator.comparingDouble(direction -> distance(direction, x, y, z)));
        for (EnumFacing direction : directions) {
            BlockPos next = origin.offset(direction);
            int distance = 0;
            while (world.isBlockLoaded(next) && canExtend(world, next, world.getBlockState(next), direction) && distance < RANGE) {
                next = next.offset(direction);
                distance++;
            }
            if (distance >= RANGE || !world.isBlockLoaded(next) || !world.getWorldBorder().contains(next)
                    || !world.getBlockState(next).getBlock().isReplaceable(world, next)
                    || !player.canPlayerEdit(next, face, held) || !world.isBlockModifiable(player, next)
                    || !world.mayPlace(girder, next, false, face, player)) continue;
            IBlockState placed = girder.getDefaultState().withProperty(GirderBlock.AXIS, direction.getAxis());
            return new Placement(next.toImmutable(), direction, placed);
        }
        return null;
    }

    public static boolean place(World world, BlockPos origin, IBlockState state, EntityPlayer player,
                                EnumHand hand, EnumFacing face, float x, float y, float z) {
        ItemStack held = player.getHeldItem(hand);
        if (player.isSneaking() || Block.getBlockFromItem(held.getItem()) != BlockFamily.get("metal_girder")) return false;
        Placement placement = find(world, origin, state, player, held, face, x, y, z);
        if (placement != null && !world.isRemote && world.setBlockState(placement.pos(), placement.state(), 3)) {
            SoundType sound = placement.state().getBlock().getSoundType(placement.state(), world, placement.pos(), player);
            world.playSound(null, placement.pos(), sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1F) / 2F, sound.getPitch() * .8F);
            if (!player.capabilities.isCreativeMode) held.shrink(1);
        }
        return true;
    }

    private static boolean canExtend(World world, BlockPos pos, IBlockState state, EnumFacing direction) {
        if (state.getBlock() instanceof GirderShaftBlock)
            return direction.getAxis().isHorizontal() && direction.getAxis() != state.getValue(KineticBlock.AXIS);
        if (!(state.getBlock() instanceof GirderBlock girder)) return false;
        IBlockState actual = girder.getActualState(state, world, pos);
        boolean x = actual.getValue(GirderBlock.X), z = actual.getValue(GirderBlock.Z);
        if (!x && !z) return direction.getAxis() == EnumFacing.Axis.Y;
        if (x && z) return true;
        return direction.getAxis() == (x ? EnumFacing.Axis.X : EnumFacing.Axis.Z);
    }

    private static double distance(EnumFacing direction, float x, float y, float z) {
        double dx = x - (.5D + direction.getXOffset() * .5D);
        double dy = y - (.5D + direction.getYOffset() * .5D);
        double dz = z - (.5D + direction.getZOffset() * .5D);
        return dx * dx + dy * dy + dz * dz;
    }
}
