package com.cogwork.cogworkbridge.content.logistics.arm;

import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import javax.annotation.Nullable;
import java.util.List;
import net.minecraftforge.items.ItemHandlerHelper;

public final class MechanicalArmBlock extends KineticBlock {
    public static final PropertyBool CEILING = PropertyBool.create("ceiling");
    public MechanicalArmBlock() {
        super(Material.IRON); setHardness(2); setResistance(6); setLightOpacity(0);
        setDefaultState(getDefaultState().withProperty(CEILING, false));
    }
    @Override public boolean hasAxis() { return false; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, CEILING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(CEILING, (meta & 1) != 0); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(CEILING) ? 1 : 0; }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z,
            int meta, EntityLivingBase placer) { return getDefaultState().withProperty(CEILING, side == EnumFacing.DOWN); }
    @Override public EnumFacing.Axis getAxis(IBlockState state) { return EnumFacing.Axis.Y; }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) { return false; }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new MechanicalArmTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(CEILING) ? new AxisAlignedBB(0, 0.125D, 0, 1, 1, 1)
                : new AxisAlignedBB(0, 0, 0, 1, 0.875D, 1);
    }
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
                                                 List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        boolean ceiling = state.getValue(CEILING);
        addBox(pos, query, boxes, ceiling
                ? new AxisAlignedBB(0, 10 / 16D, 0, 1, 1, 1)
                : new AxisAlignedBB(0, 0, 0, 1, 6 / 16D, 1));
        addBox(pos, query, boxes, ceiling
                ? new AxisAlignedBB(2 / 16D, 6 / 16D, 2 / 16D, 14 / 16D, 1, 14 / 16D)
                : new AxisAlignedBB(2 / 16D, 0, 2 / 16D, 14 / 16D, 10 / 16D, 14 / 16D));
        addBox(pos, query, boxes, ceiling
                ? new AxisAlignedBB(3 / 16D, 2 / 16D, 3 / 16D, 13 / 16D, 1, 13 / 16D)
                : new AxisAlignedBB(3 / 16D, 0, 3 / 16D, 13 / 16D, 14 / 16D, 13 / 16D));
    }
    private static void addBox(BlockPos pos, AxisAlignedBB query, List<AxisAlignedBB> boxes, AxisAlignedBB local) {
        AxisAlignedBB box = local.offset(pos);
        if (query.intersects(box)) boxes.add(box);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (!(world.getTileEntity(pos) instanceof MechanicalArmTileEntity arm) || player.getHeldItem(hand).getItem() == AllItems.WRENCH)
            return false;
        if (player.getHeldItem(hand).getItem() == AllItems.GOGGLES && !arm.hasGoggles()) {
            if (!world.isRemote) arm.equipGoggles();
            return true;
        }
        if (!arm.getHeldItem().isEmpty()) {
            if (!world.isRemote) ItemHandlerHelper.giveItemToPlayer(player, arm.takeHeld());
            return true;
        }
        if (player.isSneaking() && player.getHeldItem(hand).isEmpty()) {
            if (!world.isRemote) arm.cycleMode(player);
            return true;
        }
        return false;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof MechanicalArmTileEntity arm)
            ItemTransfer.drop(world, pos, arm.takeHeld());
        super.breakBlock(world, pos, state);
    }
}
