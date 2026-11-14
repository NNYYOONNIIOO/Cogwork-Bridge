package com.cogwork.cogworkbridge.content.kinetics.encased;

import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Applies a casing while keeping the kinetic tile's saved state. */
public final class Encasing {

    private Encasing() {
    }

    public static boolean tryApply(World world, BlockPos pos, IBlockState state,
                                   EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        Item item = held.getItem();
        boolean andesite = item == Item.getItemFromBlock(AllBlocks.ANDESITE_CASING);
        boolean brass = item == Item.getItemFromBlock(AllBlocks.BRASS_CASING);
        if (held.isEmpty() || (!andesite && !brass)) {
            return false;
        }
        Block block = state.getBlock();
        KineticBlock replacement;
        if (block instanceof EncasedShaftBlock || block instanceof EncasedCogwheelBlock) {
            return false;
        } else if (block instanceof ShaftBlock) {
            replacement = andesite ? AllBlocks.SHAFT_ENCASED_ANDESITE : AllBlocks.SHAFT_ENCASED_BRASS;
        } else if (block instanceof CogwheelBlock cog) {
            replacement = cog.isLarge()
                    ? (andesite ? AllBlocks.LARGE_COGWHEEL_ENCASED_ANDESITE : AllBlocks.LARGE_COGWHEEL_ENCASED_BRASS)
                    : (andesite ? AllBlocks.COGWHEEL_ENCASED_ANDESITE : AllBlocks.COGWHEEL_ENCASED_BRASS);
        } else {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        return replace(world, pos, state, replacement, true);
    }

    public static boolean tryRemove(World world, BlockPos pos, IBlockState state) {
        KineticBlock replacement;
        if (state.getBlock() instanceof EncasedCogwheelBlock cog) {
            replacement = cog.isLarge() ? AllBlocks.LARGE_COGWHEEL : AllBlocks.COGWHEEL;
        } else if (state.getBlock() instanceof EncasedShaftBlock) {
            replacement = AllBlocks.SHAFT;
        } else {
            return false;
        }
        return world.isRemote || replace(world, pos, state, replacement, false);
    }

    private static boolean replace(World world, BlockPos pos, IBlockState state,
                                   KineticBlock replacement, boolean initializePorts) {
        TileEntity oldTile = world.getTileEntity(pos);
        NBTTagCompound saved = oldTile == null ? null : oldTile.writeToNBT(new NBTTagCompound());
        IBlockState next = replacement.getDefaultState().withProperty(KineticBlock.AXIS,
                ((KineticBlock) state.getBlock()).getAxis(state));
        if (!world.setBlockState(pos, next, 3)) {
            return false;
        }
        TileEntity newTile = world.getTileEntity(pos);
        if (newTile != null && saved != null) {
            newTile.readFromNBT(saved);
        }
        if (initializePorts && newTile instanceof EncasedCogwheelTileEntity cog) {
            cog.initializePorts();
        } else if (newTile instanceof com.cogwork.cogworkbridge.kinetic.KineticTileEntity kinetic) {
            kinetic.refreshKinetics();
        }
        return true;
    }
}
