/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fabricbridge.transform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.MixinService;

import net.neoforged.fabricbridge.discovery.FabricJarRegistry;

/**
 * Serves a Fabric mod's classes to Mixin when the platform's own bytecode provider cannot.
 *
 * <p>Mixin prepares a config's mixin classes the first time the platform asks it about any class -
 * which happens while the mod files are still being handed over, before the game's module layer is
 * complete. At that moment FML's bytecode provider answers {@code null} for every class that lives in
 * the bridge's composite game library, and Mixin records each one as
 * {@code The specified mixin 'net.fabricmc...' was not found} and drops it for the rest of the run:
 * a launch with a large mod set lost 220 mixin classes (1416 log lines) and aborted in the game class
 * that the first dropped mixin would have served. One second later the very same lookup succeeds,
 * which is what pins this on the ordering rather than on the jars - the classes are all there.
 *
 * <p>The fallback sits in front of the platform's provider and only answers when that one gives up:
 * it reads the class from the Fabric jars the bridge discovered (the realigned copies, so the mixin
 * handler rewrites are the bytes Mixin sees). It never replaces a byte the platform could serve, so
 * the transformation pipeline's own providers keep priority.
 */
public final class FabricMixinBytecodeFallback implements IClassBytecodeProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(FabricMixinBytecodeFallback.class);

    private static final Map<String, ZipFile> OPEN_JARS = new LinkedHashMap<>();
    private static boolean installed;

    private final IClassBytecodeProvider delegate;

    private FabricMixinBytecodeFallback(IClassBytecodeProvider delegate) {
        this.delegate = delegate;
    }

    /** Installs the fallback once, after the Fabric jars are registered and before any config is prepared. */
    public static synchronized void install() {
        if (installed || Boolean.getBoolean("neofobric.noBytecodeFallback")) {
            return;
        }
        try {
            var service = MixinService.getService();
            if (service == null) {
                return;
            }
            var current = service.getBytecodeProvider();
            if (current instanceof FabricMixinBytecodeFallback || current == null) {
                return;
            }
            // setBytecodeProvider is public on FML's service; keep the platform's provider as the delegate.
            var setter = service.getClass().getMethod("setBytecodeProvider", IClassBytecodeProvider.class);
            setter.invoke(service, new FabricMixinBytecodeFallback(current));
            installed = true;
            LOGGER.info("[NeoFobric] Installed the Fabric mixin bytecode fallback (platform provider kept as delegate)");
        } catch (Throwable t) {
            LOGGER.warn("[NeoFobric] Could not install the Fabric mixin bytecode fallback", t);
        }
    }

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException {
        return getClassNode(name, false, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException {
        return getClassNode(name, runTransformers, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags) throws ClassNotFoundException {
        try {
            var node = delegate.getClassNode(name, runTransformers, readerFlags);
            if (node != null) {
                return node;
            }
        } catch (ClassNotFoundException | RuntimeException | java.io.IOException ignored) {
            // Fall through to the Fabric jars.
        }
        var bytes = fabricClassBytes(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, readerFlags);
        return node;
    }

    /** {@return the class bytes from a discovered Fabric jar, or {@code null}} */
    private static byte[] fabricClassBytes(String name) {
        var path = name.replace('.', '/') + ".class";
        var candidates = new java.util.LinkedHashSet<Path>(FabricJarRegistry.getEntries().keySet());
        candidates.addAll(FabricJarRegistry.foreignModPaths());
        for (var jar : candidates) {
            var zip = open(jar);
            if (zip == null) {
                continue;
            }
            ZipEntry entry;
            try {
                entry = zip.getEntry(path);
            } catch (IllegalStateException closed) {
                OPEN_JARS.remove(jarKey(jar));
                zip = open(jar);
                entry = zip == null ? null : zip.getEntry(path);
            }
            if (entry != null) {
                try (var in = zip.getInputStream(entry)) {
                    return in.readAllBytes();
                } catch (IOException e) {
                    LOGGER.debug("[NeoFobric] Could not read {} from {}", path, jar, e);
                }
            }
            // A mod's classes also live in the nested jars it bundles (JarJar). Mixin prepares the configs
            // of those mods in the same early window, and the platform's provider cannot see the nested
            // contents yet either - c2me's tlcache MixinBlock$1 is one of them, and without it Mixin's
            // inner-class generator produced a reference to a class nobody could load.
            var nested = nestedClassBytes(jar, path);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    /** Nested-jar class index per outer jar: resource path to bytes, built once. */
    private static final Map<String, Map<String, byte[]>> NESTED_INDEX = new java.util.concurrent.ConcurrentHashMap<>();

    private static byte[] nestedClassBytes(Path jar, String path) {
        var index = NESTED_INDEX.computeIfAbsent(jarKey(jar), key -> indexNestedJars(jar));
        return index.get(path);
    }

    private static Map<String, byte[]> indexNestedJars(Path jar) {
        var index = new java.util.HashMap<String, byte[]>();
        var zip = open(jar);
        if (zip == null) {
            return index;
        }
        var nestedNames = new java.util.ArrayList<String>();
        try {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var entryName = entry.getName();
                if (!entry.isDirectory() && entryName.endsWith(".jar")
                        && (entryName.startsWith("META-INF/jars/") || entryName.startsWith("META-INF/jarjar/"))) {
                    nestedNames.add(entryName);
                }
            }
        } catch (Throwable t) {
            return index;
        }
        for (var nestedName : nestedNames) {
            try (var in = zip.getInputStream(zip.getEntry(nestedName));
                    var nested = new java.util.zip.ZipInputStream(in)) {
                java.util.zip.ZipEntry entry;
                while ((entry = nested.getNextEntry()) != null) {
                    var entryName = entry.getName();
                    if (!entry.isDirectory() && entryName.endsWith(".class")) {
                        index.putIfAbsent(entryName, nested.readAllBytes());
                    }
                }
            } catch (Throwable t) {
                LOGGER.debug("[NeoFobric] Could not index the nested jars of {}", jar, t);
            }
        }
        return index;
    }

    private static synchronized ZipFile open(Path jar) {
        var key = jarKey(jar);
        var existing = OPEN_JARS.get(key);
        if (existing != null) {
            return existing;
        }
        try {
            var zip = new ZipFile(jar.toFile());
            OPEN_JARS.put(key, zip);
            return zip;
        } catch (IOException e) {
            LOGGER.debug("[NeoFobric] Could not open Fabric jar {}", jar, e);
            return null;
        }
    }

    private static String jarKey(Path jar) {
        return jar.toAbsolutePath().normalize().toString();
    }
}
