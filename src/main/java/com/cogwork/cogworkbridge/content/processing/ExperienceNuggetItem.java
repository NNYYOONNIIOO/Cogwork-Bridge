package com.cogwork.cogworkbridge.content.processing;

import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Create's experience nugget releases three experience per item. */
public final class ExperienceNuggetItem extends Item {
    public ExperienceNuggetItem() { setCreativeTab(ModCreativeTab.INSTANCE); }
    @Override public boolean hasEffect(ItemStack stack) { return true; }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            world.playSound(null, player.getPosition(), xy177.farmersfuturedelight.common.registry.FFDSounds.AMETHYST_BLOCK_BREAK,
                    net.minecraft.util.SoundCategory.PLAYERS, .5F, 1F);
            int used = player.isSneaking() ? 1 : stack.getCount();
            int remaining = used * 3, count = Math.min(5, used);
            Vec3d look = player.getLookVec();
            Vec3d side = look.crossProduct(new Vec3d(-.75D, 0, 0).rotateYaw(-player.rotationYaw * (float) Math.PI / 180F));
            Vec3d origin = player.getPositionEyes(1F).add(look.scale(.5D)).add(side);
            for (int i = count; i > 0; i--) {
                int value = (remaining + i - 1) / i; remaining -= value;
                Vec3d random = new Vec3d(world.rand.nextDouble() * 2 - 1, world.rand.nextDouble() * 2 - 1,
                        world.rand.nextDouble() * 2 - 1).normalize().scale(.1D);
                Vec3d motion = look.scale(.2D).add(0, .2D, 0).add(random);
                EntityXPOrb orb = new EntityXPOrb(world, origin.x, origin.y, origin.z, value);
                orb.motionX = motion.x; orb.motionY = motion.y; orb.motionZ = motion.z;
                world.spawnEntity(orb);
            }
            stack.shrink(used);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
}
