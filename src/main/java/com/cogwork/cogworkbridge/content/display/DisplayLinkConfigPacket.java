package com.cogwork.cogworkbridge.content.display;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.ByteBufUtils;

public final class DisplayLinkConfigPacket implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;
    private int mode, line;
    private String label = "";
    private boolean alternateFormat;
    private int rateInterval;
    private int namePercent=50,platformChars=3;
    public DisplayLinkConfigPacket() { }
    public DisplayLinkConfigPacket(BlockPos pos, int mode, int line, String label, boolean alternateFormat, int rateInterval) { this.pos = pos; this.mode = mode; this.line = line; this.label = label; this.alternateFormat = alternateFormat; this.rateInterval = rateInterval; }
    public DisplayLinkConfigPacket(BlockPos pos,int mode,int line,String label,boolean alternateFormat,int rateInterval,int name,int platform){this(pos,mode,line,label,alternateFormat,rateInterval);namePercent=name;platformChars=platform;}
    @Override public void fromBytes(ByteBuf buf) { pos = BlockPos.fromLong(buf.readLong()); mode = buf.readInt(); line = buf.readInt(); label = ByteBufUtils.readUTF8String(buf); alternateFormat = buf.readBoolean(); rateInterval = buf.readInt(); namePercent=buf.readInt();platformChars=buf.readInt(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeLong(pos.toLong()); buf.writeInt(mode); buf.writeInt(line); ByteBufUtils.writeUTF8String(buf, label); buf.writeBoolean(alternateFormat); buf.writeInt(rateInterval);buf.writeInt(namePercent);buf.writeInt(platformChars); }
    public static final class Handler implements IMessageHandler<DisplayLinkConfigPacket, IMessage> {
        @Override public IMessage onMessage(DisplayLinkConfigPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!player.world.isBlockLoaded(message.pos) || !player.canPlayerEdit(message.pos, net.minecraft.util.EnumFacing.UP, player.getHeldItemMainhand())
                        || player.getDistanceSqToCenter(message.pos) > 64D) return;
                TileEntity tile = player.world.getTileEntity(message.pos);
                if (tile instanceof DisplayLinkTileEntity link) {link.configureRailwayColumns(message.namePercent,message.platformChars);link.configure(message.mode, message.line, message.label, message.alternateFormat, message.rateInterval);}
            });
            return null;
        }
    }
}
