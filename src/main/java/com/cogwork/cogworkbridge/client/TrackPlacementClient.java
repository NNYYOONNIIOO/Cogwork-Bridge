package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.client.render.FastStateRendering;
import com.cogwork.cogworkbridge.content.trains.track.TrackCurve;
import com.cogwork.cogworkbridge.content.trains.track.TrackItem;
import com.cogwork.cogworkbridge.content.trains.track.TrackConnectPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.EXTBlendColor;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import java.util.Map;

/** Client-only preview of the current two-point railway placement. */
@Mod.EventBusSubscriber(modid=Reference.MOD_ID,value=Side.CLIENT)
public final class TrackPlacementClient {
    private TrackPlacementClient() { }

    @SubscribeEvent(priority=EventPriority.LOWEST) public static void connect(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND || event.getEntityPlayer().isSneaking()) return;
        ItemStack stack = event.getEntityPlayer().getHeldItemMainhand();
        if (!(stack.getItem() instanceof TrackItem) || !TrackItem.selected(stack) || event.getFace() == null || event.getHitVec() == null) return;
        BlockPos pos = event.getPos(); Vec3d hit = event.getHitVec();
        ModNetwork.CHANNEL.sendToServer(new TrackConnectPacket(pos, event.getFace(),
                (float) (hit.x - pos.getX()), (float) (hit.y - pos.getY()), (float) (hit.z - pos.getZ()), GuiScreen.isCtrlKeyDown()));
        event.setCanceled(true); event.setCancellationResult(EnumActionResult.SUCCESS);
    }

    @SubscribeEvent public static void render(RenderWorldLastEvent event) {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.world==null || mc.player==null || mc.currentScreen!=null || mc.player.isSneaking())return;
        ItemStack stack=mc.player.getHeldItemMainhand();
        if(!(stack.getItem() instanceof TrackItem item))return;
        RayTraceResult hit=mc.objectMouseOver;
        Entity view=mc.getRenderViewEntity();
        if(view==null || hit==null || hit.typeOfHit!=RayTraceResult.Type.BLOCK || hit.sideHit==null || hit.hitVec==null)return;
        BlockPos clicked=hit.getBlockPos();
        TrackItem.Preview preview=item.preview(mc.world,mc.player,stack,clicked,hit.sideHit,
                (float)(hit.hitVec.x-clicked.getX()),(float)(hit.hitVec.y-clicked.getY()),(float)(hit.hitVec.z-clicked.getZ()),GuiScreen.isCtrlKeyDown());
        if(preview==null)return;
        double partial=event.getPartialTicks();
        double x=view.lastTickPosX+(view.posX-view.lastTickPosX)*partial;
        double y=view.lastTickPosY+(view.posY-view.lastTickPosY)*partial;
        double z=view.lastTickPosZ+(view.posZ-view.lastTickPosZ)*partial;
        GlStateManager.enableBlend(); GlStateManager.enableDepth(); GlStateManager.depthMask(false);
        GL14.glBlendColor(1F,1F,1F,preview.valid()?.55F:.25F);
        GlStateManager.blendFunc(EXTBlendColor.GL_CONSTANT_ALPHA_EXT,EXTBlendColor.GL_ONE_MINUS_CONSTANT_ALPHA_EXT);
        try {
            for(Map.Entry<BlockPos,IBlockState> entry:preview.pavement().entrySet()) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(entry.getKey().getX()-x,entry.getKey().getY()-y,entry.getKey().getZ()-z);
                FastStateRendering.getInstance().renderFast(entry.getValue());
                GlStateManager.popMatrix();
            }
            for(Map.Entry<BlockPos,IBlockState> entry:preview.placements().entrySet()) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(entry.getKey().getX()-x,entry.getKey().getY()-y,entry.getKey().getZ()-z);
                FastStateRendering.getInstance().renderFast(entry.getValue());
                GlStateManager.popMatrix();
            }
        } finally {
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.depthMask(true);
        }
        GlStateManager.disableTexture2D();
        GlStateManager.glLineWidth(3F);
        AxisAlignedBB source=new AxisAlignedBB(preview.first()).grow(.025D).offset(-x,-y,-z);
        RenderGlobal.drawSelectionBoundingBox(source,preview.valid()?.4F:1F,preview.valid()?1F:.25F,.35F,.8F);
        TrackCurve curve=preview.curve();
        if(curve!=null) {
            BufferBuilder buffer=Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_LINES,DefaultVertexFormats.POSITION_COLOR);
            int segments=Math.max(8,(int)Math.ceil(curve.length()*4D));
            for(int i=0;i<segments;i++) {
                Vec3d a=curve.point(i/(double)segments),b=curve.point((i+1)/(double)segments);
                Vec3d da=curve.tangent(i/(double)segments),db=curve.tangent((i+1)/(double)segments);
                Vec3d na=new Vec3d(-da.z,0,da.x).normalize().scale(.375D);
                Vec3d nb=new Vec3d(-db.z,0,db.x).normalize().scale(.375D);
                for(int side:new int[]{-1,1}) {
                    Vec3d p=a.add(na.scale(side)),q=b.add(nb.scale(side));
                    float red=preview.valid()?.4F:1F,green=preview.valid()?1F:.25F;
                    buffer.pos(p.x-x,p.y-y+.2D,p.z-z).color(red,green,.35F,.85F).endVertex();
                    buffer.pos(q.x-x,q.y-y+.2D,q.z-z).color(red,green,.35F,.85F).endVertex();
                }
            }
            Tessellator.getInstance().draw();
        }
        GlStateManager.glLineWidth(1F); GlStateManager.enableTexture2D();
        GlStateManager.disableBlend(); GlStateManager.color(1F,1F,1F,1F);
    }
}
