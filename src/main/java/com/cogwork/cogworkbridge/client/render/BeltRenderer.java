package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem;
import com.cogwork.cogworkbridge.client.BeltModels;
import net.minecraft.item.EnumDyeColor;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import java.util.Random;

public final class BeltRenderer extends TileEntitySpecialRenderer<BeltTileEntity> {
    @Override public void render(BeltTileEntity belt, double x, double y, double z, float partialTicks,
                                 int destroyStage, float alpha) {
        if (belt.getWorld() == null) return;
        IBlockState state = belt.getContraptionRenderState() != null ? belt.getContraptionRenderState()
                : belt.getWorld().getBlockState(belt.getPos());
        renderSurface(belt, state, x, y, z, partialTicks);
        if (belt.casing() != BeltTileEntity.CasingType.NONE) {
            IBakedModel casing = BeltModels.casing(belt.modelKey(),
                    belt.casing() == BeltTileEntity.CasingType.ANDESITE ? "andesite" : "brass",
                    belt.hasPulley() && belt.index() > 0 && belt.index() + 1 < belt.length());
            if (casing != null) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(x, y, z);
                FastStateRendering.getInstance().renderFast(casing, state);
                GlStateManager.popMatrix();
            }
        }
        if (belt.hasPulley() && state.getBlock() instanceof com.cogwork.cogworkbridge.content.logistics.belt.BeltBlock block) {
            EnumFacing.Axis axis = block.getAxis(state);
            EnumFacing facing = EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, axis);
            IBlockState pulley = AllBlocks.BELT_PULLEY.getDefaultState().withProperty(RenderPartBlock.FACING, facing);
            BlockPos renderPos = belt.getContraptionRenderPos() == null ? belt.getPos() : belt.getContraptionRenderPos();
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            RotatingModelRenderer.renderRotated(belt.getWorld(), renderPos, pulley, axis,
                    RotatingModelRenderer.kineticAngle(belt, partialTicks));
            GlStateManager.popMatrix();
        }
        renderTransportedItem(belt, belt.getCarried(), belt.renderPosition(partialTicks),
                belt.carriedAngle(), belt.carriedSideOffset(), x, y, z);
        renderTransportedItem(belt, belt.getProcessingOutput(), belt.renderOutputPosition(partialTicks),
                belt.outputAngle(), belt.outputSideOffset(), x, y, z);
        for (BeltTileEntity.TutorialItem item : belt.tutorialItems())
            renderTransportedItem(belt, item.stack(), item.position(partialTicks), item.angle(), item.offset(), x, y, z);
    }

    private static void renderTransportedItem(BeltTileEntity belt, ItemStack stack, float position,
                                              float angle, float sideOffset, double x, double y, double z) {
        if (stack.isEmpty()) return;
        boolean packageItem = PackageItem.isPackage(stack);
        double offset = position - 0.5D;
        BlockPos step = belt.step();
        boolean diagonal = step.getY() != 0 && (step.getX() != 0 || step.getZ() != 0);
        boolean onSlope = diagonal && (belt.index() != 0 || offset >= 0)
                && (belt.index() != belt.length() - 1 || offset <= 0);
        double verticalOffset = offset;
        if (diagonal && belt.index() == 0) verticalOffset = Math.max(0, verticalOffset);
        if (diagonal && belt.index() == belt.length() - 1) verticalOffset = Math.min(0, verticalOffset);
        double sideX = -step.getZ() * sideOffset;
        double sideZ = step.getX() * sideOffset;
        double itemX = x + 0.5D + step.getX() * offset + sideX;
        double itemZ = z + 0.5D + step.getZ() * offset + sideZ;
        boolean upright = angle == 180.0F;
        Minecraft mc = Minecraft.getMinecraft();
        boolean blockItem = mc.getRenderItem().getItemModelWithOverrides(stack, belt.getWorld(), null).isGui3d();
        double thinItemLift = !upright && !blockItem ? 1.0D / 16.0D : 0.0D;
        double itemY = y + 15.0D / 16.0D + step.getY() * verticalOffset + thinItemLift;
        int copies = Math.min(4, 1 + (31 - Integer.numberOfLeadingZeros(stack.getCount())) / 2);
        Random random = new Random((long) angle);
        GlStateManager.pushMatrix();
        GlStateManager.translate(itemX, itemY, itemZ);
        if (onSlope) {
            if (step.getX() != 0) GlStateManager.rotate(45 * step.getX() * step.getY(), 0, 0, 1);
            else GlStateManager.rotate(-45 * step.getZ() * step.getY(), 1, 0, 0);
            GlStateManager.translate(0, 1 / 8D, 0);
        }
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableRescaleNormal();
        if (upright) {
            GlStateManager.rotate(angle, 0, 1, 0);
            GlStateManager.translate(0, 3 / 32D, 1 / 16D);
        } else {
            GlStateManager.rotate(angle, 0, 1, 0);
        }
        for (int i = 0; i < copies; i++) {
            GlStateManager.pushMatrix();
            if (i > 0 && blockItem) {
                GlStateManager.translate(random.nextFloat() * 1 / 16D * i, 0,
                        random.nextFloat() * 1 / 16D * i);
            }
            if (!upright && !blockItem) {
                GlStateManager.translate(0, -3 / 32D, 0);
                GlStateManager.rotate(90, 1, 0, 0);
            }
            if (packageItem) GlStateManager.translate(0, 0.25D, 0);
            float scale = packageItem ? 1.5F : 0.5F;
            GlStateManager.scale(scale, scale, scale);
            mc.getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
            if (!upright) {
                if (!blockItem) GlStateManager.rotate(10, 0, 1, 0);
                GlStateManager.translate(0, blockItem ? 1 / 64D : 1 / 16D, 0);
            }
        }
        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
    private static void renderSurface(BeltTileEntity belt, IBlockState state, double x, double y, double z, float partial) {
        IBakedModel[] models = BeltModels.models(belt.modelKey()); if (models == null) return;
        boolean diagonal = belt.step().getY() != 0 && (belt.step().getX() != 0 || belt.step().getZ() != 0);
        EnumDyeColor dye = belt.color();
        String texturePath = dye == null
                ? (diagonal ? "textures/block/belt_diagonal_scroll.png" : "textures/block/belt_scroll.png")
                : "textures/block/belt/" + (dye == EnumDyeColor.SILVER ? "light_gray" : dye.getName())
                + (diagonal ? "_diagonal_scroll.png" : "_scroll.png");
        ResourceLocation texture = new ResourceLocation("create", texturePath);
        BlockPos step = belt.step();
        boolean sideways = state.getBlock() instanceof com.cogwork.cogworkbridge.content.logistics.belt.BeltBlock
                && ((com.cogwork.cogworkbridge.content.logistics.belt.BeltBlock) state.getBlock()).getAxis(state) == EnumFacing.Axis.Y;
        boolean diagonalDirection = step.getY() != 0 && (step.getX() != 0 || step.getZ() != 0);
        boolean vertical = step.getY() != 0 && step.getX() == 0 && step.getZ() == 0;
        boolean alongX = step.getX() != 0 || vertical && state.getValue(com.cogwork.cogworkbridge.kinetic.KineticBlock.AXIS) == EnumFacing.Axis.Z;
        boolean negativeAxis = vertical ? step.getY() < 0 : step.getX() < 0 || step.getZ() < 0;
        float direction = negativeAxis ? -1F : 1F;
        if ((diagonalDirection && ((step.getY() < 0) ^ alongX))
                || (!sideways && !diagonalDirection && alongX)
                || (sideways && negativeAxis)) direction = -direction;
        double scroll = belt.getSpeed() * direction
                * (belt.getWorld().getTotalWorldTime() + partial) / (31.5D * 16D);
        scroll -= Math.floor(scroll);
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y, z);
        boolean offsetEnabled = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        float previousFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
        float previousUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-1F, -1F);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        Tessellator tessellator = Tessellator.getInstance(); BufferBuilder buffer = tessellator.getBuffer();
        int light = belt.getWorld().getCombinedLight(belt.getPos(), 0);
        SurfaceLight surfaceLight = diagonal && belt.getContraptionRenderState() == null
                ? new SurfaceLight(belt, light) : null;
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        GlStateManager.disableLighting();
        buffer.begin(7, DefaultVertexFormats.BLOCK);
        for (int i = 0; i < models.length; i++) {
            IBakedModel model = models[i]; if (model == null) continue;
            float offset = (float) (((scroll + (i == 1 ? 0.5D : 0D)) % 1D) * (diagonal ? 3D / 8D : 0.5D));
            for (EnumFacing side : EnumFacing.VALUES) append(buffer, model.getQuads(state, side, 0L), offset, diagonal, light, surfaceLight);
            append(buffer, model.getQuads(state, null, 0L), offset, diagonal, light, surfaceLight);
        }
        tessellator.draw();
        if (lighting) GlStateManager.enableLighting();
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GL11.glPolygonOffset(previousFactor, previousUnits);
        if (!offsetEnabled) GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GlStateManager.popMatrix();
    }
    private static void append(BufferBuilder buffer, Iterable<BakedQuad> quads, float scroll, boolean diagonal, int light,
                               SurfaceLight surfaceLight) {
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad.getSprite();
            float width = sprite.getMaxU() - sprite.getMinU(), height = sprite.getMaxV() - sprite.getMinV();
            if (width <= 0 || height <= 0) continue;
            int[] data = quad.getVertexData().clone(); int stride = data.length / 4;
            for (int i = 0; i < 4; i++) {
                int uv = i * stride + 4;
                float u = (Float.intBitsToFloat(data[uv]) - sprite.getMinU()) / width;
                float v = (Float.intBitsToFloat(data[uv + 1]) - sprite.getMinV()) / height;
                data[uv] = Float.floatToRawIntBits(u);
                // The scrolling image is 16x32, while the source model uses a
                // 16x16 sprite. Preserve the source pixel scale for both halves;
                // 3/8 is the scrolling period, not the model's UV scale.
                data[uv + 1] = Float.floatToRawIntBits(v * 0.5F + scroll);
            }
            buffer.addVertexData(data);
            if (surfaceLight == null) buffer.putBrightness4(light, light, light, light);
            else {
                int[] lights = surfaceLight.forQuad(data, stride, quad.getFace());
                buffer.putBrightness4(lights[0], lights[1], lights[2], lights[3]);
            }
            EnumFacing face = quad.getFace();
            float shade = diagonal ? slopedShade(data, stride)
                    : face == EnumFacing.DOWN ? 0.5F : face == EnumFacing.UP || face == null ? 1F
                    : face.getAxis() == EnumFacing.Axis.Z ? 0.8F : 0.6F;
            buffer.putColorRGB_F4(shade, shade, shade);
        }
    }

    private static float slopedShade(int[] data, int stride) {
        float x0 = Float.intBitsToFloat(data[0]), y0 = Float.intBitsToFloat(data[1]), z0 = Float.intBitsToFloat(data[2]);
        float ax = Float.intBitsToFloat(data[stride]) - x0, ay = Float.intBitsToFloat(data[stride + 1]) - y0;
        float az = Float.intBitsToFloat(data[stride + 2]) - z0;
        float bx = Float.intBitsToFloat(data[stride * 2]) - x0, by = Float.intBitsToFloat(data[stride * 2 + 1]) - y0;
        float bz = Float.intBitsToFloat(data[stride * 2 + 2]) - z0;
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float total = Math.abs(nx) + Math.abs(ny) + Math.abs(nz);
        if (total < 1.0E-6F) return 1F;
        return (Math.abs(nx) * 0.6F + Math.abs(ny) * (ny >= 0 ? 1F : 0.5F)
                + Math.abs(nz) * 0.8F) / total;
    }

    /** Sample the actual world positions, including geometry beyond its segment. */
    private static final class SurfaceLight {
        private final BeltTileEntity belt;
        private final int fallback;
        private final java.util.Map<BlockPos, Integer> samples = new java.util.HashMap<>();
        private final int[] result = new int[4];
        private SurfaceLight(BeltTileEntity belt, int center) {
            this.belt = belt;
            this.fallback = center;
        }
        private int light(BlockPos pos) {
            Integer found = samples.get(pos);
            if (found != null) return found;
            int value = belt.getWorld().isBlockLoaded(pos) ? belt.getWorld().getCombinedLight(pos, 0) : fallback;
            samples.put(pos, value);
            return value;
        }
        private int[] forQuad(int[] data, int stride, EnumFacing face) {
            for (int i = 0; i < 4; i++) {
                double x = belt.getPos().getX() + Float.intBitsToFloat(data[i * stride]);
                double y = belt.getPos().getY() + Float.intBitsToFloat(data[i * stride + 1]);
                double z = belt.getPos().getZ() + Float.intBitsToFloat(data[i * stride + 2]);
                int block = 0, sky = 0;
                // Include both sides of a block boundary, so matching vertices
                // on adjacent sloped models use identical light values.
                for (double dx : new double[]{-0.001D, 0.001D}) for (double dz : new double[]{-0.001D, 0.001D}) {
                    BlockPos at = new BlockPos(x + dx, y + 0.01D, z + dz);
                    for (BlockPos sample : new BlockPos[]{at, at.up()}) {
                        int value = light(sample);
                        block = Math.max(block, value & 0xffff);
                        sky = Math.max(sky, (value >>> 16) & 0xffff);
                    }
                }
                result[i] = (sky << 16) | block;
            }
            return result;
        }
    }

}
