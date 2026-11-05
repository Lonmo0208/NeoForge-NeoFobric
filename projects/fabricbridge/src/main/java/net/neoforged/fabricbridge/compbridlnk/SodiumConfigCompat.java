/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fabricbridge.compbridlnk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sodium 0.9.3's config API resolves the registering mod's metadata through FML's {@code ModList},
 * which Fabric mods are not part of: iris (Fabric) registering its own options through Sodium's
 * builder then dies with {@code NullPointerException: Mod with id iris not found in ModList} inside
 * {@code ConfigLoaderForge.getModMetadata}.
 *
 * <p>The fix wraps the {@code ConfigManager.modInfoFunction} lookup: the platform function runs
 * first (so FML mods keep their exact behavior), and a miss falls back to the Fabric side's own
 * metadata. Installed from {@code FabricBridgeHooks} before the Fabric client entrypoints run,
 * which is the first moment any Fabric mod can reach the builder.
 */
public final class SodiumConfigCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(SodiumConfigCompat.class);

    private static final String CONFIG_MANAGER = "net.caffeinemc.mods.sodium.client.config.ConfigManager";
    private static final String MOD_METADATA = "net.caffeinemc.mods.sodium.client.config.ConfigManager$ModMetadata";

    private SodiumConfigCompat() {}

    public static void wrapConfigModInfoFunction() {
        try {
            var loader = Thread.currentThread().getContextClassLoader();
            var manager = Class.forName(CONFIG_MANAGER, false, loader);
            var metadataClass = Class.forName(MOD_METADATA, false, loader);
            var metadataCtor = metadataClass.getDeclaredConstructor(String.class, String.class);
            metadataCtor.setAccessible(true);
            var field = manager.getDeclaredField("modInfoFunction");
            field.setAccessible(true);
            var original = field.get(null);
            if (!(original instanceof java.util.function.Function<?, ?> platformFunction)) {
                LOGGER.info("[NeoFobric] Sodium's config mod-info lookup is not set yet - left alone");
                return;
            }

            @SuppressWarnings("unchecked")
            var platformLookup = (java.util.function.Function<Object, Object>) platformFunction;
            java.util.function.Function<Object, Object> bridging = id -> {
                try {
                    return platformLookup.apply(id);
                } catch (Throwable platformMiss) {
                    // The platform side only knows FML mods; a Fabric mod registering options through
                    // Sodium's builder lands here, and its own metadata is the correct answer.
                    var container = net.fabricmc.loader.api.FabricLoader.getInstance()
                            .getModContainer(String.valueOf(id));
                    if (container.isEmpty()) {
                        throw platformMiss;
                    }
                    var metadata = container.get().getMetadata();
                    try {
                        return metadataCtor.newInstance(metadata.getName(), metadata.getVersion().toString());
                    } catch (Throwable e) {
                        LOGGER.debug("[NeoFobric] Could not build the metadata record for {}", id, e);
                        throw platformMiss;
                    }
                }
            };
            field.set(null, bridging);
            LOGGER.info("[NeoFobric] Wrapped Sodium's config mod-info lookup - Fabric mods registering"
                    + " options through Sodium's builder resolve their metadata from the Fabric side");
        } catch (Throwable t) {
            // Older Sodium builds have no config API at all; absence is the everyday case.
            LOGGER.debug("[NeoFobric] Sodium's config API not present - nothing to wrap", t);
        }
    }
}
