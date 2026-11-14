package com.cogwork.cogworkbridge.content.kinetics.clock;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;

/** Time follows the dimension clock; movement and the cuckoo require kinetic power. */
public final class CuckooClockTileEntity extends KineticTileEntity {
    private int animation, progress, lastTrigger = -1;
    private float hour, minute, previousHour, previousMinute;
    @Override protected boolean acceptsConnection(EnumFacing side) {
        return world != null && world.getBlockState(pos).getBlock() instanceof CuckooClockBlock
                && side == world.getBlockState(pos).getValue(CuckooClockBlock.FACING).getOpposite();
    }
    @Override public float getStressImpact() { return 1F; }
    @Override public void update() {
        super.update();
        if (world == null || getSpeed() == 0) return;
        long time = world.getWorldTime();
        int day = (int)Math.floorMod(time * (world.provider.isSurfaceWorld() ? 1 : 24),24000L);
        int hours = (day/1000+6)%24, minutes = day%1000*60/1000;
        if (world.isRemote) {
            previousHour=hour; previousMinute=minute;
            hour += net.minecraft.util.math.MathHelper.wrapDegrees((hours%12)*30F-hour)*.2F;
            minute += net.minecraft.util.math.MathHelper.wrapDegrees(minutes*6F-minute)*.2F;
            if (animation != 0) progress++;
            if (animation == 3 && progress > 30 && progress < 50 && world.rand.nextBoolean()) world.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE,pos.getX()+world.rand.nextDouble(),pos.getY()+world.rand.nextDouble(),pos.getZ()+world.rand.nextDouble(),0,.02,0);
            if (progress > 100) { animation=0; progress=0; }
            if (animation==0) {
                long tick=world.getTotalWorldTime();int period=world.provider.isSurfaceWorld()?16:3;
                if(tick%period==0) world.playSound(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,SoundEvents.BLOCK_NOTE_HAT,SoundCategory.BLOCKS,1/16F,tick%(period*2)==0?2F:1.5F,false);
            }
            return;
        }
        int trigger = hours==12 && minutes<5 ? 1 : hours==18 && minutes>31 && minutes<36 ? 2 : 0;
        if (trigger == 0) lastTrigger=-1;
        if (animation==0 && trigger!=0 && lastTrigger!=trigger && world.provider.isSurfaceWorld()) {
            if (trigger == 2) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardNear(world, pos, "cuckoo_clock", 32);
            lastTrigger=trigger;
            animation=((CuckooClockBlock)world.getBlockState(pos).getBlock()).isMysterious()?3:trigger;
            progress=0; markDirty(); sync();
        }
        if (animation==0) return;
        progress++;
        if (progress==1 || progress==21) world.playSound(null,pos,SoundEvents.BLOCK_NOTE_CHIME,SoundCategory.BLOCKS,2F,progress==1?.5F:.793701F);
        int step=animation==3?3:15;
        for(int phase=30;phase<=60;phase+=step) { if(progress==phase-step/3) world.playSound(null,pos,SoundEvents.BLOCK_CHEST_OPEN,SoundCategory.BLOCKS,1/16F,2F); if(progress==phase+step/3) world.playSound(null,pos,SoundEvents.BLOCK_CHEST_CLOSE,SoundCategory.BLOCKS,1/16F,2F); }
        if (progress>=30 && progress<=60 && (progress-30)%step==0)
            world.playSound(null,pos,animation==1?SoundEvents.ENTITY_PIG_AMBIENT:SoundEvents.ENTITY_CREEPER_HURT,SoundCategory.BLOCKS,.25F,animation==1?1F:3F);
        if (animation==3 && progress==40) world.playSound(null,pos,SoundEvents.ENTITY_TNT_PRIMED,SoundCategory.BLOCKS,1F,1F);
        if (animation==3 && progress==50) { world.setBlockToAir(pos); world.createExplosion(null,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,3F,true); return; }
        if (progress>100) { animation=0;progress=0;markDirty();sync(); }
    }
    public float hour(float partial) { return previousHour+(hour-previousHour)*partial; }
    public float minute(float partial) { return previousMinute+(minute-previousMinute)*partial; }
    public int animation() { return animation; }
    public float progress(float partial) { return progress+partial; }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n) { super.writeToNBT(n); n.setInteger("CuckooAnimation",animation); n.setInteger("CuckooProgress",progress); n.setInteger("LastTrigger",lastTrigger); return n; }
    @Override public void readFromNBT(NBTTagCompound n) { super.readFromNBT(n); animation=n.getInteger("CuckooAnimation");progress=n.getInteger("CuckooProgress");lastTrigger=n.hasKey("LastTrigger")?n.getInteger("LastTrigger"):-1; }
    @Override public NBTTagCompound getUpdateTag() { NBTTagCompound n=super.getUpdateTag(); n.setInteger("CuckooAnimation",animation); n.setInteger("CuckooProgress",progress); return n; }
    @Override public void handleUpdateTag(NBTTagCompound n) { super.handleUpdateTag(n); animation=n.getInteger("CuckooAnimation");progress=n.getInteger("CuckooProgress"); }
}
