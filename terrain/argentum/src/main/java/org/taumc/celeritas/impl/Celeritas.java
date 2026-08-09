package org.taumc.celeritas.impl;

import org.taumc.celeritas.impl.config.CeleritasConfig;
import org.taumc.celeritas.impl.config.JsonOptionStorage;

import java.nio.file.Path;

public class Celeritas {
    public static String VERSION;
    public static CeleritasConfig CONFIG = new CeleritasConfig();
    public static JsonOptionStorage<CeleritasConfig> CONFIG_STORAGE;

    public static void onInitializeClient(String version, Path configDirectory) {
        VERSION = version;
        CONFIG_STORAGE = JsonOptionStorage.load(configDirectory.resolve("kalia.json"), CeleritasConfig.class, CeleritasConfig::new, CeleritasConfig::validate);
        CONFIG = CONFIG_STORAGE.getData();
    }
}
