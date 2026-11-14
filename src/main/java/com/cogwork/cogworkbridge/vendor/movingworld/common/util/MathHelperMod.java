/*
 * Derived from MovingWorld (https://github.com/darkevilmac/MovingWorld).
 * Upstream: src/main/java/com/elytradev/movingworld/common/util/MathHelperMod.java
 * Revision: 400225200ea4f913047f67dbfb44e882fcf997d8.
 * Modified by Cogwork Bridge contributors: relocated packages and adapted
 * for integration with Cogwork Bridge contraptions and their lifecycle.
 * Licensed under the Apache License, Version 2.0; see
 * META-INF/licenses/MovingWorld-LICENSE.txt and THIRD-PARTY-NOTICES.txt.
 */
package com.cogwork.cogworkbridge.vendor.movingworld.common.util;

public class MathHelperMod {

    public static final float PI_HALF = (float) (Math.PI / 2D);

    public static double clamp_double(double d, double lowerbound, double upperbound) {
        return d < lowerbound ? lowerbound : d > upperbound ? upperbound : d;
    }

    public static int round_double(double d) {
        return (int) Math.round(d);
    }
}
