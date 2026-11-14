package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.core.TargetJar;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.ModMetadata;

import java.io.File;
import java.util.Collections;

/**
 * Synthetic container that exposes the upstream Create jar to Forge's resource
 * pack system via {@link #getCustomResourcePackClass()}.
 *
 * <p>It is handed to {@code FMLClientHandler.addModAsResource} during mod
 * construction, which is early enough for the pack list to be built. The jar is
 * never registered as a mod: FML skips it because of the external manifest.
 */
public final class TargetResourceContainer extends DummyModContainer {

    private final TargetJar target;

    public TargetResourceContainer(TargetJar target) {
        super(createMetadata());
        this.target = target;
    }

    private static ModMetadata createMetadata() {
        ModMetadata metadata = new ModMetadata();
        metadata.modId = "cogworkbridge_source_assets";
        metadata.name = "Cogwork Bridge Source Assets";
        metadata.version = "1.0.0";
        metadata.authorList = Collections.singletonList("Cogwork Bridge");
        return metadata;
    }

    @Override
    public File getSource() {
        return target.getFile();
    }

    @Override
    public Class<?> getCustomResourcePackClass() {
        return TargetResourcePack.class;
    }

    @Override
    public boolean isImmutable() {
        return true;
    }
}