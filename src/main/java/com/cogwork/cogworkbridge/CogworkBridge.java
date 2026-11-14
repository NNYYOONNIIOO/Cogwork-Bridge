package com.cogwork.cogworkbridge;

import com.cogwork.cogworkbridge.proxy.IProxy;
import com.cogwork.cogworkbridge.core.CogworkBridgeCore;
import com.cogwork.cogworkbridge.registry.StateGuard;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Cogwork Bridge entry point.
 *
 * <p>Design notes (see docs/ and todo.md):
 * <ul>
 *   <li>Target runtime: Minecraft 1.12.2 + Cleanroom.</li>
 *   <li>Behavioural specification: Create 6.0.11 for MC 1.21.1.</li>
 *   <li>Code is MIT. Create's assets are All Rights Reserved and are never bundled;
 *       the client reads them at runtime from the required user-installed jar.</li>
 * </ul>
 */
@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.VERSION,
     acceptedMinecraftVersions = "[1.12.2]",
     dependencies = "required-after:farmers_future_delight;required-after:create_1_21_1@[1.21.1];after:ponder-legacy@[1.7.1,)",
     useMetadata = true)
public class CogworkBridge {

    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);

    @Mod.Instance(Reference.MOD_ID)
    public static CogworkBridge instance;

    @SidedProxy(modId = Reference.MOD_ID,
                clientSide = "com.cogwork.cogworkbridge.proxy.ClientProxy",
                serverSide = "com.cogwork.cogworkbridge.proxy.CommonProxy")
    public static IProxy proxy;

    @Mod.EventHandler
    public void construct(FMLConstructionEvent event) {
        CogworkBridgeCore.requireIsolatedUpstream();
        proxy.construct(event);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("{} {} starting (remote={})", Reference.MOD_NAME, Reference.VERSION, event.getSide().isClient());
        proxy.preInit(event);
        StateGuard.verifyAll();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
