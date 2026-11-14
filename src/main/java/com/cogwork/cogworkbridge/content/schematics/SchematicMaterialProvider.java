package com.cogwork.cogworkbridge.content.schematics;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import javax.annotation.Nullable;
import java.util.List;

/** Blocks can add material costs from sanitized blueprint data without creating a live tile entity. */
public interface SchematicMaterialProvider {
    List<ItemStack> schematicMaterials(IBlockState state, @Nullable NBTTagCompound tileData);
}
