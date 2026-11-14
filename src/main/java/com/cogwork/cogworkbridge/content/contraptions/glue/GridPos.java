package com.cogwork.cogworkbridge.content.contraptions.glue;

/** Minimal integer block position used by the glue selection core. */
public record GridPos(int x, int y, int z) {

    public GridPos offset(Direction direction) {
        return new GridPos(x + direction.dx(), y + direction.dy(), z + direction.dz());
    }
}
