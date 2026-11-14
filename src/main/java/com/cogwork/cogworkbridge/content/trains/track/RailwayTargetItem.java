package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

/** Select a track first, then place the station, signal or observer within sixteen blocks. */
public final class RailwayTargetItem extends ItemBlock {
    private static final String SELECTION="RailwayTarget";
    private final String kind;
    public RailwayTargetItem(Block block,String kind){super(block);this.kind=kind;}
    @Override public boolean hasEffect(ItemStack stack){return selected(stack)||super.hasEffect(stack);}
    private static boolean selected(ItemStack stack){return stack.hasTagCompound()&&stack.getTagCompound().hasKey(SELECTION,10);}
    private static void clear(ItemStack stack){
        if(!stack.hasTagCompound())return;stack.getTagCompound().removeTag(SELECTION);
        if(stack.getTagCompound().isEmpty())stack.setTagCompound(null);
    }
    private static void status(EntityPlayer player,String key){
        player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.track_target."+key),true);
    }
    @Override public EnumActionResult onItemUse(EntityPlayer player,World world,BlockPos pos,EnumHand hand,
            EnumFacing face,float x,float y,float z) {
        ItemStack stack=player.getHeldItem(hand);
        if(player.isSneaking()&&selected(stack)){
            if(!world.isRemote){clear(stack);player.inventory.markDirty();status(player,"clear");}
            return EnumActionResult.SUCCESS;
        }
        IBlockState clicked=world.getBlockState(pos);
        if(clicked.getBlock() instanceof TrackBlock) {
            if(!world.isRemote) {
                TrackShape shape=clicked.getValue(TrackBlock.SHAPE);
                if(shape.junction()){status(player,"junction");return EnumActionResult.FAIL;}
                RailwayNetworkData graph=RailwayNetworkData.get(world);
                graph.update(world,pos);
                // Create selects the positive track direction when the player
                // looks against that axis, not along it.
                boolean front=shape.x*player.getLookVec().x+shape.z*player.getLookVec().z<0;
                if(!graph.canTarget(pos,kind,front)){status(player,"occupied");return EnumActionResult.FAIL;}
                NBTTagCompound root=stack.hasTagCompound()?stack.getTagCompound():new NBTTagCompound();
                NBTTagCompound data=new NBTTagCompound();data.setLong("Pos",pos.toLong());
                data.setInteger("Dimension",world.provider.getDimension());
                data.setBoolean("Front",front);
                root.setTag(SELECTION,data);stack.setTagCompound(root);player.inventory.markDirty();status(player,"set");
            }
            return EnumActionResult.SUCCESS;
        }
        if(!selected(stack)) {if(!world.isRemote)status(player,"missing");return EnumActionResult.FAIL;}
        NBTTagCompound data=stack.getTagCompound().getCompoundTag(SELECTION);
        BlockPos target=BlockPos.fromLong(data.getLong("Pos"));boolean front=data.getBoolean("Front");
        BlockPos placed=clicked.getBlock().isReplaceable(world,pos)?pos:pos.offset(face);
        if(data.getInteger("Dimension")!=world.provider.getDimension()||target.distanceSq(placed)>=16D*16D
                || !world.isBlockLoaded(target)||!(world.getBlockState(target).getBlock() instanceof TrackBlock)) {
            if(!world.isRemote)status(player,"invalid");return EnumActionResult.FAIL;
        }
        if(!world.isRemote&&!RailwayNetworkData.get(world).canTarget(target,kind,front)){
            status(player,"occupied");return EnumActionResult.FAIL;
        }
        EnumActionResult result=super.onItemUse(player,world,pos,hand,face,x,y,z);
        if(result==EnumActionResult.SUCCESS && !world.isRemote) {
            if(world.getTileEntity(placed) instanceof RailwayTargetTile tile)tile.bind(target,front);
            clear(stack);player.inventory.markDirty();status(player,"success");
        }
        return result;
    }
}
