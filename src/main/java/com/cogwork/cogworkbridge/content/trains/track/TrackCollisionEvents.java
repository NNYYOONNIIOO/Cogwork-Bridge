package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Include railway geometry that extends beyond the owning block's normal search radius. */
@Mod.EventBusSubscriber(modid=Reference.MOD_ID)
public final class TrackCollisionEvents {
    private TrackCollisionEvents() { }
    @SubscribeEvent public static void collect(GetCollisionBoxesEvent event) {
        AxisAlignedBB query=event.getAabb();
        int minX=(int)Math.floor(query.minX)-2,maxX=(int)Math.floor(query.maxX)+2;
        int minY=(int)Math.floor(query.minY)-1,maxY=(int)Math.floor(query.maxY)+1;
        int minZ=(int)Math.floor(query.minZ)-2,maxZ=(int)Math.floor(query.maxZ)+2;
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            pos.setPos(x,Math.max(0,minY),z);
            if(!event.getWorld().isBlockLoaded(pos))continue;
            for(int y=minY;y<=maxY;y++) {
                pos.setPos(x,y,z);
                IBlockState state=event.getWorld().getBlockState(pos);
                if(state.getBlock() instanceof TrackBlock) {
                    add(event,pos,TrackCollisionGeometry.straight(state.getValue(TrackBlock.SHAPE)));
                    TileEntity tile=event.getWorld().getTileEntity(pos);
                    if(tile instanceof TrackTileEntity track)add(event,pos,track.curveCollision());
                } else if(state.getBlock() instanceof FakeTrackBlock) {
                    TileEntity tile=event.getWorld().getTileEntity(pos);
                    if(tile instanceof FakeTrackTile track)add(event,pos,track.curveCollision());
                }
            }
        }
    }
    private static void add(GetCollisionBoxesEvent event,BlockPos pos,Iterable<AxisAlignedBB> localBoxes) {
        for(AxisAlignedBB box:localBoxes) {
            AxisAlignedBB worldBox=box.offset(pos);
            if(worldBox.intersects(event.getAabb()))event.getCollisionBoxesList().add(worldBox);
        }
    }
}
