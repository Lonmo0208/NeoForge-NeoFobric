/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

/**
 * Fabric's resource conditions ({@code fabric:load_conditions}) on data files that reach a NeoForge
 * loader.
 *
 * <p>Fabric's {@code fabric-resource-conditions-api-v1} module filters data files by wrapping the parse
 * call inside {@code SimpleJsonResourceReloadListener.scanDirectory}. NeoForge's recipe loading goes
 * through {@link net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener#scanDirectoryWithModifier},
 * a copy of that scan the module does not wrap, so a Fabric recipe whose conditions are false used to be
 * parsed (and fail on the missing serializer) instead of being skipped. The reload calls this hook, which
 * asks the bridge to evaluate the conditions exactly as the module would.
 *
 * <p>Without the bridge, or without Fabric API, every file is allowed: this hook never decides on its own.
 */
@ApiStatus.Internal
public final class FabricResourceConditions {
    private static final Logger LOGGER = LogUtils.getLogger();

    private FabricResourceConditions() {}

    /**
     * {@return whether the data file may be loaded}
     *
     * @param json the file's root element
     * @param type the loader's resource type, for diagnostics
     * @param id   the resource id of the file
     * @param ops  the ops the loader parses the file with; a {@code RegistryOps} carries the registry
     *             lookup the tag-population conditions need
     */
    public static boolean allow(JsonElement json, String type, Identifier id, DynamicOps<?> ops) {
        if (json == null || !json.isJsonObject()) {
            return true;
        }
        var object = json.getAsJsonObject();
        if (!object.has("fabric:load_conditions")) {
            return true;
        }
        try {
            return net.neoforged.fabricbridge.FabricBridgeHooks.allowFabricResourceConditions(object, type, id.toString(), ops);
        } catch (Throwable t) {
            // A condition that cannot be evaluated must not take the data file down with it.
            LOGGER.warn("Could not evaluate fabric:load_conditions of {} ({}); loading the file", id, type, t);
            return true;
        }
    }

    /**
     * {@return whether a NeoForge-only loader should skip this file}
     *
     * <p>Some folders are read by NeoForge alone. A Fabric jar's {@code loot_modifiers} files use a
     * Forge-shaped schema that Fabric's own (code-based) loot API never reads, so parsing them here only
     * fails on the missing serializer. The bridge answers which namespaces came from a Fabric jar.
     *
     * @param folder the loader's resource type with a trailing slash, e.g. {@code loot_modifiers/}
     * @param id     the resource id of the file
     */
    public static boolean skipForLoader(String folder, Identifier id) {
        if (folder == null || folder.isEmpty()) {
            return false;
        }
        // The loot modifier manager parses every file under data/*/loot_modifiers as a modifier. The
        // index file (global_loot_modifiers.json, {replace, entries}) is not one, and nothing here reads
        // it, so it used to fail the parse and log an error for every pack that ships one.
        if (folder.startsWith("loot_modifiers") && "global_loot_modifiers".equals(id.getPath())) {
            return true;
        }
        try {
            return net.neoforged.fabricbridge.FabricBridgeHooks.shouldSkipFabricLoaderData(folder, id.getNamespace());
        } catch (Throwable t) {
            // A hook failure must not hide files.
            return false;
        }
    }
}
