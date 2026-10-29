/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Display-only mod list rows contributed by a loader bridge, e.g. Fabric mods running through
 * NeoForge's Fabric bridge.
 *
 * <p>Such mods are not part of FML's mod list, so {@link ModListScreen} cannot discover them through
 * {@link net.neoforged.fml.ModList}. The bridge publishes what a player should see into a small JSON
 * manifest while it discovers the mods, and this class reads that manifest back.
 *
 * <p>The manifest is written on every launch, so a mod the player removed cannot leave a ghost row
 * behind. A launch without the bridge leaves the {@code neofobric.active} system property unset,
 * which suppresses the read entirely - otherwise a manifest from an earlier launch would list mods
 * that are not running.
 *
 * <p>Manifest location: {@code <gameDir>/.neofobric/mods-menu.json}.
 */
final class ModListContributions {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoForge/ModListContributions");

    /** Set by the bridge during mod discovery. A launch that never sets it shows no contributed rows. */
    private static final String ACTIVE_FLAG = "neofobric.active";

    private static final Path MANIFEST = FMLPaths.GAMEDIR.get().resolve(".neofobric").resolve("mods-menu.json");

    /**
     * One contributed row, already resolved to the strings the mod list screen shows. Every component
     * is a literal: the metadata comes from another loader's manifest, where translation keys would
     * point at language files that loader's resource packs never load into this one.
     */
    record Contributed(String id, Component displayName, String version, Component authors, Component description,
            Component license, @Nullable String website, @Nullable String bundledBy) {}

    private static volatile List<Contributed> cache;

    private ModListContributions() {}

    /** {@return the contributed rows, in manifest order} Read once per launch. */
    static List<Contributed> contributions() {
        var cached = cache;
        if (cached != null) {
            return cached;
        }
        synchronized (ModListContributions.class) {
            if (cache == null) {
                cache = read();
            }
            return cache;
        }
    }

    private static List<Contributed> read() {
        if (!Boolean.getBoolean(ACTIVE_FLAG) || !Files.isRegularFile(MANIFEST)) {
            return List.of();
        }
        try (var reader = Files.newBufferedReader(MANIFEST, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("mods")) {
                return List.of();
            }
            var entries = new ArrayList<Contributed>();
            for (var element : root.getAsJsonObject().getAsJsonArray("mods")) {
                var entry = readEntry(element);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            LOGGER.debug("Read {} contributed mod list row(s) from {}", entries.size(), MANIFEST);
            return List.copyOf(entries);
        } catch (IOException | RuntimeException e) {
            // Display only: a broken manifest means the screen simply does not list these mods.
            LOGGER.warn("Failed to read the contributed mod list manifest {}", MANIFEST, e);
            return List.of();
        }
    }

    private static @Nullable Contributed readEntry(JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        var object = element.getAsJsonObject();
        var id = string(object, "id");
        if (id == null || id.isBlank()) {
            return null;
        }
        var name = orElse(string(object, "name"), id);
        return new Contributed(
                id,
                Component.literal(name),
                orElse(string(object, "version"), ""),
                Component.literal(orElse(string(object, "authors"), "")),
                Component.literal(orElse(string(object, "description"), "")),
                Component.literal(orElse(string(object, "license"), "")),
                string(object, "website"),
                string(object, "bundledBy"));
    }

    private static @Nullable String string(JsonObject object, String member) {
        var value = object.get(member);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static String orElse(@Nullable String value, String fallback) {
        return value == null ? fallback : value;
    }
}
