package com.cogwork.cogworkbridge.core;

import java.io.File;

/**
 * An upstream Create JAR found in {@code mods/} that is used purely as an asset
 * source. It is never loaded as a mod: the core plugin removes it from Cleanroom
 * and MixinBooter discovery, then re-mounts it as a resource pack.
 */
public final class TargetJar {

    private final File file;
    private final String implementationVersion;

    TargetJar(File file, String implementationVersion) {
        this.file = file;
        this.implementationVersion = implementationVersion;
    }

    public File getFile() {
        return file;
    }

    public String getImplementationVersion() {
        return implementationVersion;
    }

}
