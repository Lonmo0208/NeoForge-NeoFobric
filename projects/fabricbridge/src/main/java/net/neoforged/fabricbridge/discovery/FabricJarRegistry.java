/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fabricbridge.discovery;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the Fabric mods the bridge discovered during FML's mod discovery phase.
 *
 * <p>Discovery runs on a background thread while the early loading screen is shown; the results
 * are consumed later on the main thread (class processor linking and loader boot), after FML's
 * off-thread discovery has been joined, so the concurrent collections here are a safety net.
 */
public final class FabricJarRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(FabricJarRegistry.class);

    public static final String FABRIC_MOD_JSON = "fabric.mod.json";

    private static final Map<Path, FabricModInfo> FABRIC_JARS = new ConcurrentHashMap<>();

    /** Registration order of {@link #FABRIC_JARS}, which is also the composite module's content order. */
    private static final java.util.List<Path> REGISTRATION_ORDER = new java.util.concurrent.CopyOnWriteArrayList<>();
    /**
     * Fabric jar paths that are part of the FML game content layer (either a jar in the mods
     * folder or a nested jar extracted to the bridge cache). fabric-loader's own classpath
     * management ({@code addToClassPath}) is a no-op for these.
     */
    private static final Set<Path> GAME_LAYER_PATHS = ConcurrentHashMap.newKeySet();

    /**
     * Jars the bridge deliberately left to FML because they declare a NeoForge mod file, even when they
     * also carry a {@code fabric.mod.json} (the multi-loader builds of Sodium and Iris). Fabric-loader's
     * dev-mode classpath discovery enumerates every {@code fabric.mod.json} on the game class loader and
     * would otherwise read them as Fabric mods; see {@code BridgedLauncher}.
     */
    private static final Set<Path> FOREIGN_MOD_PATHS = ConcurrentHashMap.newKeySet();

    /** {@link #foreignModFileNames()}, rebuilt only when another jar is left to FML. */
    private static volatile Set<String> FOREIGN_MOD_FILE_NAMES;

    /** Bumped whenever {@link #FOREIGN_MOD_PATHS} grows, so {@code BridgedLauncher} can drop its wrapper. */
    private static final java.util.concurrent.atomic.AtomicLong FOREIGN_MOD_GENERATION = new java.util.concurrent.atomic.AtomicLong();

    private FabricJarRegistry() {}

    public static void markForeignModPath(Path path) {
        if (FOREIGN_MOD_PATHS.add(path)) {
            FOREIGN_MOD_FILE_NAMES = null;
            FOREIGN_MOD_GENERATION.incrementAndGet();
        }
    }

    /** {@return a counter that changes whenever another jar is left to FML} */
    public static long foreignModGeneration() {
        return FOREIGN_MOD_GENERATION.get();
    }

    /** {@return whether this exact jar was left to FML} */
    public static boolean isForeignModPath(Path path) {
        return FOREIGN_MOD_PATHS.contains(path);
    }

    public static boolean hasForeignMods() {
        return !FOREIGN_MOD_PATHS.isEmpty();
    }

    /** {@return the file names of the jars left to FML} */
    public static Set<String> foreignModFileNames() {
        var names = FOREIGN_MOD_FILE_NAMES;
        if (names != null) {
            return names;
        }
        synchronized (FabricJarRegistry.class) {
            if (FOREIGN_MOD_FILE_NAMES == null) {
                var built = new java.util.HashSet<String>(FOREIGN_MOD_PATHS.size());
                for (var path : FOREIGN_MOD_PATHS) {
                    built.add(path.getFileName().toString());
                }
                FOREIGN_MOD_FILE_NAMES = Set.copyOf(built);
            }
            return FOREIGN_MOD_FILE_NAMES;
        }
    }

    public static boolean hasFabricMods() {
        return !FABRIC_JARS.isEmpty();
    }

    public static void register(Path path, FabricModInfo info) {
        if (FABRIC_JARS.put(path, info) == null) {
            REGISTRATION_ORDER.add(path);
        }
    }

    public static FabricModInfo get(Path path) {
        return FABRIC_JARS.get(path);
    }

    public static void markGameLayerPath(Path path) {
        GAME_LAYER_PATHS.add(path);
    }

    public static boolean isGameLayerPath(Path path) {
        return GAME_LAYER_PATHS.contains(path);
    }

    public static List<Path> getPaths() {
        return List.copyOf(FABRIC_JARS.keySet());
    }

    /**
     * { all registered Fabric mods as an unmodifiable snapshot of path to metadata}
     */
    public static java.util.SequencedMap<Path, FabricModInfo> getEntries() {
        // A ConcurrentHashMap has no order, but the composite module resolves duplicate entries by the order
        // its contents were registered in, and everything that has to agree with the classes it serves -
        // the mixin configs in particular - has to see the jars in that same order.
        var ordered = new java.util.LinkedHashMap<Path, FabricModInfo>();
        for (var path : REGISTRATION_ORDER) {
            var info = FABRIC_JARS.get(path);
            if (info != null) {
                ordered.put(path, info);
            }
        }
        FABRIC_JARS.forEach(ordered::putIfAbsent);
        return java.util.Collections.unmodifiableSequencedMap(ordered);
    }
}
