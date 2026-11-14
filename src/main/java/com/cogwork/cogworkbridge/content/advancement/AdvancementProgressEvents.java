package com.cogwork.cogworkbridge.content.advancement;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class AdvancementProgressEvents {
    private AdvancementProgressEvents() { }
    @SubscribeEvent
    public static void attach(net.minecraftforge.event.AttachCapabilitiesEvent<net.minecraft.tileentity.TileEntity> event) {
        if (event.getObject().getClass().getName().startsWith("com.cogwork.cogworkbridge."))
            event.addCapability(new ResourceLocation(Reference.MOD_ID, "advancement_owner"), new AdvancementOwnerData());
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.PlaceEvent event) {
        ResourceLocation name = event.getPlacedBlock().getBlock().getRegistryName();
        if (name != null && Reference.MOD_ID.equals(name.getNamespace()))
            AllAdvancements.registerOwner(event.getWorld(), event.getPos(), event.getPlayer());
        if (event.getPlacedBlock().getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock placed) {
            net.minecraft.util.EnumFacing.Axis axis = placed.getAxis(event.getPlacedBlock());
            for (net.minecraft.util.EnumFacing a : net.minecraft.util.EnumFacing.VALUES) for (net.minecraft.util.EnumFacing b : net.minecraft.util.EnumFacing.VALUES) {
                if (a.getAxis() == axis || b.getAxis() == axis || a.getAxis() == b.getAxis()) continue;
                IBlockState other = event.getWorld().getBlockState(event.getPos().offset(a).offset(b));
                if (other.getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock cog
                        && cog.isLarge() != placed.isLarge() && cog.getAxis(other) == axis)
                    AllAdvancements.award(event.getPlayer(), "shifting_gears");
            }
        }
    }

    @SubscribeEvent
    public static void onMount(net.minecraftforge.event.entity.EntityMountEvent event) {
        if (!(event.getEntityMounting() instanceof EntityPlayer player) || player.world.isRemote
                || !(event.getEntityBeingMounted() instanceof com.cogwork.cogworkbridge.content.decoration.SeatEntity seat)
                || seat.getBoundContraption() == null) return;
        if (event.isMounting()) player.getEntityData().setTag("CogworkMountLocation", net.minecraft.nbt.NBTUtil.createPosTag(new net.minecraft.util.math.BlockPos(player)));
        else if (player.getEntityData().hasKey("CogworkMountLocation")) {
            net.minecraft.util.math.BlockPos start = net.minecraft.nbt.NBTUtil.getPosFromTag(player.getEntityData().getCompoundTag("CogworkMountLocation"));
            player.getEntityData().removeTag("CogworkMountLocation");
            if (player.getDistanceSq(start) >= 25000000D) AllAdvancements.award(player, "long_travel");
        }
    }
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.world.isRemote)
            AllAdvancements.checkPlayerEquipment(event.player);
    }
}
