/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.renderstate;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Reconciles a custom {@link MapItemSavedData} with the map data the client level holds for the same id.
 *
 * <p>An item map may keep its own map data (NeoForge's {@code MapItem#getCustomMapData}) and synchronise it
 * over a channel of its own. A message sent on such a channel can be missed - during the login and
 * dimension-change reconfiguration the client is not in the play phase yet - while the ordinary map channel
 * still delivered the very same map, whose result is the level's map data. The custom data then exists
 * (its decorations arrived later) but stays colour-less, which renders the map blank.
 *
 * <p>Only pixels the custom data does not have are filled in, so colours the custom data carries always win
 * and a map that was fully synchronised is left untouched.
 */
public final class MapColourSync {
    private MapColourSync() {}

    public static void reconcile(MapId mapId, MapItemSavedData mapData) {
        if (mapId == null || mapData == null) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) {
            return;
        }
        var synced = level.getMapData(mapId);
        if (synced == null || synced == mapData || synced.colors.length != mapData.colors.length) {
            return;
        }
        var target = mapData.colors;
        var source = synced.colors;
        boolean filled = false;
        for (int i = 0; i < target.length; i++) {
            if (target[i] == 0 && source[i] != 0) {
                target[i] = source[i];
                filled = true;
            }
        }
        if (filled) {
            minecraft.getMapTextureManager().update(mapId, mapData);
        }
    }
}
