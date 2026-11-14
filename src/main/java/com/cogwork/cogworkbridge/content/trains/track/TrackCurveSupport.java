package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class TrackCurveSupport {
    private TrackCurveSupport() { }
    public static Set<BlockPos> positions(TrackCurve curve) {
        Set<BlockPos> positions=new LinkedHashSet<>();
        int samples=Math.max(8,(int)Math.ceil(curve.length()*4D));
        for(int i=1;i<samples;i++) {
            Vec3d p=curve.point(i/(double)samples);BlockPos block=new BlockPos(p.x,p.y+.125D,p.z);
            if(!block.equals(curve.first)&&!block.equals(curve.second))positions.add(block);
        }
        return positions;
    }
    public static boolean place(World world,TrackCurve curve) {
        Map<BlockPos,IBlockState> created=new LinkedHashMap<>();Set<BlockPos> linked=new LinkedHashSet<>();
        for(BlockPos pos:positions(curve)) {
            if(world.getBlockState(pos).getBlock() instanceof TrackBlock)continue;
            if(!(world.getBlockState(pos).getBlock() instanceof FakeTrackBlock)) {
                IBlockState previous=world.getBlockState(pos);
                if(!world.setBlockState(pos,AllBlocks.FAKE_TRACK.getDefaultState(),3)) {
                    rollback(world,curve,linked,created);return false;
                }
                created.put(pos,previous);
            }
            if(!(world.getTileEntity(pos) instanceof FakeTrackTile tile)) {
                rollback(world,curve,linked,created);return false;
            }
            tile.add(curve);linked.add(pos);
        }
        return true;
    }
    private static void rollback(World world,TrackCurve curve,Set<BlockPos> linked,Map<BlockPos,IBlockState> created) {
        for(BlockPos pos:linked)if(world.getTileEntity(pos) instanceof FakeTrackTile tile)tile.remove(curve);
        for(Map.Entry<BlockPos,IBlockState> entry:created.entrySet())world.setBlockState(entry.getKey(),entry.getValue(),3);
    }
    public static void remove(World world,TrackCurve curve,boolean drop,@Nullable BlockPos beingBroken) {
        RailwayNetworkData graph=RailwayNetworkData.get(world);graph.forgetCurve(curve);
        for(BlockPos endpoint:new BlockPos[]{curve.first,curve.second})
            if(world.isBlockLoaded(endpoint)&&world.getTileEntity(endpoint) instanceof TrackTileEntity track)
                track.remove(curve.other(endpoint));
        for(BlockPos pos:positions(curve))
            if(!pos.equals(beingBroken)&&world.isBlockLoaded(pos)&&world.getTileEntity(pos) instanceof FakeTrackTile tile)tile.remove(curve);
        if(drop&&world.getGameRules().getBoolean("doTileDrops")) {
            BlockPos at=beingBroken==null?curve.first:beingBroken;
            InventoryHelper.spawnItemStack(world,at.getX()+.5D,at.getY()+.2D,at.getZ()+.5D,
                    new ItemStack(Item.getItemFromBlock(AllBlocks.TRACK),curve.cost));
        }
    }
}
