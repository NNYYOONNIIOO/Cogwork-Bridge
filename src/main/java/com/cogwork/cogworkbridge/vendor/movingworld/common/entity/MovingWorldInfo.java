/*
 * Derived from MovingWorld (https://github.com/darkevilmac/MovingWorld).
 * Upstream: src/main/java/com/elytradev/movingworld/common/entity/MovingWorldInfo.java
 * Revision: 400225200ea4f913047f67dbfb44e882fcf997d8.
 * Modified by Cogwork Bridge contributors: relocated packages and adapted
 * for integration with Cogwork Bridge contraptions and their lifecycle.
 * Licensed under the Apache License, Version 2.0; see
 * META-INF/licenses/MovingWorld-LICENSE.txt and THIRD-PARTY-NOTICES.txt.
 */
package com.cogwork.cogworkbridge.vendor.movingworld.common.entity;

import java.util.UUID;

/**
 * Extend from this class to make ship info for your own needs.
 */
public class MovingWorldInfo {

    private String name;
    private UUID owner;

    public MovingWorldInfo() {
        name = "";
        owner = null;
    }

    public String getName() {
        return name;
    }

    public MovingWorldInfo setName(String name) {
        this.name = name;
        return this;
    }

    public UUID getOwner() {
        return owner;
    }

    public MovingWorldInfo setOwner(UUID owner) {
        this.owner = owner;
        return this;
    }

}