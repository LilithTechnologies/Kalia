package dev.rdh.argentum.impl;

import dev.rdh.argentum.impl.config.ArgentumConfig;
import dev.rdh.argentum.impl.config.JsonOptionStorage;

import java.nio.file.Path;

public class Argentum {
    public static String VERSION;
    public static ArgentumConfig CONFIG = new ArgentumConfig();
    public static JsonOptionStorage<ArgentumConfig> CONFIG_STORAGE;

    public static void onInitializeClient(String version, Path configDirectory) {
        VERSION = version;
        CONFIG_STORAGE = JsonOptionStorage.load(configDirectory.resolve("kalia.json"), ArgentumConfig.class, ArgentumConfig::new, ArgentumConfig::validate);
        CONFIG = CONFIG_STORAGE.getData();
    }
}
