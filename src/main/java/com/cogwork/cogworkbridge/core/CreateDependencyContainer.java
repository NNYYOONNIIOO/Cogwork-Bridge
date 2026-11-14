package com.cogwork.cogworkbridge.core;

import com.google.common.eventbus.EventBus;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;

import java.io.File;
import java.util.Collections;

/**
 * Forge dependency marker for the separately installed Create archive.
 * This container belongs to Cogwork Bridge; it never loads Create's code.
 */
public final class CreateDependencyContainer extends DummyModContainer {
    public CreateDependencyContainer() {
        super(metadata());
    }

    private static ModMetadata metadata() {
        ModMetadata metadata = new ModMetadata();
        metadata.modId = "create_1_21_1";
        metadata.name = "Create for Minecraft 1.21.1 (resource dependency)";
        // This is the supported Minecraft version, not Create's own version.
        metadata.version = "1.21.1";
        metadata.description = "The original Create 1.21.1 archive detected by Cogwork Bridge.";
        metadata.authorList = Collections.singletonList("The Creators of Create");
        return metadata;
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        return true;
    }

    @Override
    public boolean isImmutable() {
        return true;
    }

    @Override
    public File getSource() {
        // Forge may expose injected containers as resource packs. Point to
        // this bridge, so the original jar is mounted only by our adapter.
        return CogworkBridgeCore.getCoremodSource();
    }
}
