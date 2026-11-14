/*
 * Derived from MovingWorld (https://github.com/darkevilmac/MovingWorld).
 * Upstream: src/main/java/com/elytradev/movingworld/common/util/MaterialDensity.java
 * Revision: 400225200ea4f913047f67dbfb44e882fcf997d8.
 * Modified by Cogwork Bridge contributors: relocated packages and adapted
 * for integration with Cogwork Bridge contraptions and their lifecycle.
 * Licensed under the Apache License, Version 2.0; see
 * META-INF/licenses/MovingWorld-LICENSE.txt and THIRD-PARTY-NOTICES.txt.
 */
package com.cogwork.cogworkbridge.vendor.movingworld.common.util;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;

import java.util.HashMap;
import java.util.Map;

public class MaterialDensity {
    public static final float WATER_DENSITY = 1.000F;
    public static final float DEFAULT_DENSITY = 0.34f;

    private static Map<Material, Float> materialDensityMap = new HashMap<>();
    private static Map<String, Float> blockDensityMap = new HashMap<>();

    public static void addDensity(Material mat, float dens) {
        materialDensityMap.put(mat, dens);
    }

    public static void addDensity(Block block, float dens) {
        blockDensityMap.put(Block.REGISTRY.getNameForObject(block).toString(), dens);
    }

    public static float getDensity(IBlockState state) {
        if (state == null) return DEFAULT_DENSITY;
        Float f = blockDensityMap.get(Block.REGISTRY.getNameForObject(state.getBlock()));
        if (f != null) return f;
        return getDensity(state.getMaterial());
    }

    public static float getDensity(Material mat) {
        Float f = materialDensityMap.get(mat);
        if (f != null) return f;
        return DEFAULT_DENSITY;
    }
}
