package org.taumc.celeritas.impl;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.taumc.celeritas.impl.config.CeleritasConfig;
import org.taumc.celeritas.impl.config.JsonOptionStorage;

public class Celeritas implements ClientModInitializer {
    public static final String MODID = "kalia";
    public static String VERSION;
    public static CeleritasConfig CONFIG = new CeleritasConfig();
    public static JsonOptionStorage<CeleritasConfig> CONFIG_STORAGE;

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        VERSION = loader.getModContainer(MODID).orElseThrow().getMetadata().getVersion().toString();
        CONFIG_STORAGE = JsonOptionStorage.load(loader.getConfigDir().resolve("kalia.json"), CeleritasConfig.class, CeleritasConfig::new, CeleritasConfig::validate);
        CONFIG = CONFIG_STORAGE.getData();
    }
}
