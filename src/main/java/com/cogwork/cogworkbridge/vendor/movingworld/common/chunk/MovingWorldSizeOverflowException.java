/*
 * Derived from MovingWorld (https://github.com/darkevilmac/MovingWorld).
 * Upstream: src/main/java/com/elytradev/movingworld/common/chunk/MovingWorldSizeOverflowException.java
 * Revision: 400225200ea4f913047f67dbfb44e882fcf997d8.
 * Modified by Cogwork Bridge contributors: relocated packages and adapted
 * for integration with Cogwork Bridge contraptions and their lifecycle.
 * Licensed under the Apache License, Version 2.0; see
 * META-INF/licenses/MovingWorld-LICENSE.txt and THIRD-PARTY-NOTICES.txt.
 */
package com.cogwork.cogworkbridge.vendor.movingworld.common.chunk;

public class MovingWorldSizeOverflowException extends RuntimeException {
    private static final long serialVersionUID = 1995L;

    public MovingWorldSizeOverflowException() {
        super();
    }
}
