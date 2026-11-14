package com.cogwork.cogworkbridge.content.logistics.table;

import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public final class TableClothBlock extends BlockContainer {
    public static final IUnlistedProperty<Integer> CULLED_SIDES = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "culled_sides"; }
        @Override public boolean isValid(Integer value) { return value != null && value >= 0 && value < 16; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };

    public TableClothBlock() { super(Material.CLOTH); setHardness(.8F); }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[0], new IUnlistedProperty<?>[]{CULLED_SIDES});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState extended)) return state;
        int culled = 0;
        for (EnumFacing side : EnumFacing.HORIZONTALS)
            if (!shouldSideBeRendered(state, world, pos, side)) culled |= 1 << side.getHorizontalIndex();
        return extended.withProperty(CULLED_SIDES, culled);
    }
    @Override public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        if (side.getAxis().isHorizontal() && world.getBlockState(pos.offset(side)).getBlock() instanceof TableClothBlock)
            return false;
        return super.shouldSideBeRendered(state, world, pos, side);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TableClothTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return new AxisAlignedBB(0, 0, 0, 1, .0625, 1); }
    public static boolean priceOccluded(World world, BlockPos pos, TableClothTileEntity cloth) {
        BlockPos next=pos.offset(cloth.facing());
        return world.getBlockState(next).getBlock() instanceof TableClothBlock
                || world.isSideSolid(next.down(), cloth.facing().getOpposite());
    }
    public static net.minecraft.util.math.Vec3d priceCenter(World world, BlockPos pos, TableClothTileEntity cloth) {
        var v=priceOccluded(world,pos,cloth) ? new net.minecraft.util.math.Vec3d(.5,.046875,.953125)
                : new net.minecraft.util.math.Vec3d(.75,-.171875,1.046875);
        return v.subtract(.5,0,.5).rotateYaw((float)Math.toRadians(-cloth.facing().getHorizontalAngle())).add(.5,0,.5);
    }
    public static AxisAlignedBB priceBox(World world, BlockPos pos, TableClothTileEntity cloth) {
        var c=priceCenter(world,pos,cloth);
        boolean flat=priceOccluded(world,pos,cloth);
        double dx=flat||cloth.facing().getAxis()==EnumFacing.Axis.Z?.19:.025;
        double dz=flat||cloth.facing().getAxis()==EnumFacing.Axis.X?.19:.025;
        return new AxisAlignedBB(c.x-dx,c.y-(flat?.025:.19),c.z-dz,c.x+dx,c.y+(flat?.025:.19),c.z+dz);
    }
    public static boolean targetsPrice(World world, BlockPos pos, TableClothTileEntity cloth, net.minecraft.util.math.Vec3d local) {
        return cloth.isShop() && priceBox(world,pos,cloth).grow(.025).contains(local);
    }
    @Override public net.minecraft.util.math.RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos,
            net.minecraft.util.math.Vec3d start, net.minecraft.util.math.Vec3d end) {
        var normal=super.collisionRayTrace(state,world,pos,start,end);
        if (!(world.getTileEntity(pos) instanceof TableClothTileEntity cloth) || !cloth.isShop()) return normal;
        var hit=priceBox(world,pos,cloth).offset(pos).calculateIntercept(start,end);
        if (hit==null || normal!=null && normal.hitVec.squareDistanceTo(start)<=hit.hitVec.squareDistanceTo(start)) return normal;
        return new net.minecraft.util.math.RayTraceResult(hit.hitVec,hit.sideHit,pos);
    }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote && placer instanceof EntityPlayer player && stack.hasTagCompound()
                && stack.getTagCompound().hasKey("AutoRequest", 10) && world.getTileEntity(pos) instanceof TableClothTileEntity cloth)
            cloth.loadShop(stack.getTagCompound().getCompoundTag("AutoRequest"), player);
    }
    @Override public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        super.getDrops(drops, world, pos, state, fortune);
        if (!(world.getTileEntity(pos) instanceof TableClothTileEntity cloth) || !cloth.isShop()) return;
        for (ItemStack drop : drops) {
            if (Block.getBlockFromItem(drop.getItem()) != this) continue;
            net.minecraft.nbt.NBTTagCompound root = drop.hasTagCompound() ? drop.getTagCompound() : new net.minecraft.nbt.NBTTagCompound();
            root.setTag("AutoRequest", cloth.shopData()); drop.setTagCompound(root);
        }
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float x, float y, float z) {
        ItemStack held = player.getHeldItem(hand);
        if (side == EnumFacing.DOWN || held.getItem() == AllItems.WRENCH) return false;
        if (world.getTileEntity(pos) instanceof TableClothTileEntity cloth
                && targetsPrice(world,pos,cloth,new net.minecraft.util.math.Vec3d(x,y,z)) && cloth.maySetPrice(player)) {
            if (!world.isRemote && (!held.isEmpty() || player.isSneaking())) cloth.setPrice(held, player);
            return true;
        }
        if (!player.isSneaking() && Block.getBlockFromItem(held.getItem()) instanceof TableClothBlock) {
            EnumFacing adjacent = Math.abs(x - .5F) > Math.abs(z - .5F)
                    ? (x < .5F ? EnumFacing.WEST : EnumFacing.EAST) : (z < .5F ? EnumFacing.NORTH : EnumFacing.SOUTH);
            return held.getItem().onItemUse(player, world, pos, hand, adjacent, x, y, z) != EnumActionResult.FAIL;
        }
        if (player.isSneaking()) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof TableClothTileEntity cloth) cloth.interact(player, hand);
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TableClothTileEntity cloth) cloth.dropContents();
        super.breakBlock(world, pos, state);
    }
}
