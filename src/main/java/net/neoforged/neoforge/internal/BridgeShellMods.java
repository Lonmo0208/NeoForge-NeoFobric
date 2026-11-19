/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.internal;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/// The FML mods a loader bridge created as shells for the mods it loads itself.
///
/// A bridged Fabric mod has no FML container of its own, and the NeoForge-side code it reaches needs
/// one to look up an event bus. The bridge therefore registers a class-less "shell" FML mod per
/// bridged mod (`neofobric.shellMods` names them, see the bridge's discovery) and republishes the
/// Fabric rows through `.neofobric/mods-menu.json`. The shells are bookkeeping, not mods a player
/// installed, so the mod list screen and the mod counter must not show them as separate rows.
@ApiStatus.Internal
public final class BridgeShellMods {
    private static volatile Set<String> ids;

    private BridgeShellMods() {}

    /// {@return the shell mod ids, or an empty set when no bridge set any}
    public static Set<String> ids() {
        var cached = ids;
        if (cached != null) {
            return cached;
        }
        synchronized (BridgeShellMods.class) {
            if (ids == null) {
                ids = read();
            }
            return ids;
        }
    }

    public static boolean isShell(@Nullable String modId) {
        return modId != null && ids().contains(modId);
    }

    /// {@return how many shells are actually in FML's mod list}
    public static int loadedShellCount() {
        if (ids().isEmpty()) {
            return 0;
        }
        return (int) ids().stream().filter(id -> ModList.get().getModContainerById(id).isPresent()).count();
    }

    private static Set<String> read() {
        var property = System.getProperty("neofobric.shellMods");
        if (property == null || property.isBlank()) {
            return Set.of();
        }
        var parsed = new LinkedHashSet<String>();
        for (var id : property.split(",")) {
            var trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        return Collections.unmodifiableSet(parsed);
    }
}
