/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Re-sends a player's maps in full a few times after they join or change dimension.
 *
 * <p>A map's first update after a login or dimension change is a full patch, and it is sent from the
 * player's first tick - while the client may still be loading terrain or reconfiguring. When that one
 * message is lost, only small incremental patches follow and the client's copy stays blank until the map
 * is dirtied again. Re-marking the whole map dirty a few seconds later makes the next sync carry every
 * colour again, and repeating it covers clients whose world load outlasts the first attempts. Each
 * attempt costs one map-sized message per map carried.
 */
@EventBusSubscriber(modid = NeoForgeMod.MOD_ID)
public final class MapResync {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NeoForge/MapResync");

    /** Attempts after login/dimension change, in ticks: 10s, 30s and 60s. */
    private static final int[] DELAY_TICKS = {200, 600, 1200};

    private static final Map<ServerPlayer, ArrayDeque<Integer>> PENDING = new IdentityHashMap<>();
    private static int ticks;

    private MapResync() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            schedule(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            schedule(player);
        }
    }

    private static void schedule(ServerPlayer player) {
        var deadlines = PENDING.computeIfAbsent(player, ignored -> new ArrayDeque<>());
        deadlines.clear();
        for (int delay : DELAY_TICKS) {
            deadlines.add(ticks + delay);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ticks++;
        if (PENDING.isEmpty()) {
            return;
        }
        var iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var player = entry.getKey();
            var deadlines = entry.getValue();
            if (player.isRemoved()) {
                iterator.remove();
                continue;
            }
            if (deadlines.isEmpty()) {
                iterator.remove();
                continue;
            }
            if (deadlines.peekFirst() > ticks) {
                continue;
            }
            deadlines.pollFirst();
            try {
                resync(player);
            } catch (Throwable t) {
                LOGGER.warn("Could not re-send the maps of {}", player.getPlainTextName(), t);
            }
            if (deadlines.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private static void resync(ServerPlayer player) {
        var inventory = player.getInventory();
        int resynced = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            var mapId = stack.get(DataComponents.MAP_ID);
            if (mapId == null) {
                continue;
            }
            MapItemSavedData data = MapItem.getSavedData(stack, player.level());
            if (data == null) {
                continue;
            }
            // setColor marks the pixel's area dirty for every holder, so touching two opposite corners
            // makes the next sync carry the whole map again. The stored colours are left unchanged.
            data.getHoldingPlayer(player);
            data.setColor(0, 0, data.colors[0]);
            data.setColor(127, 127, data.colors[127 + 127 * 128]);
            resynced++;
        }
        LOGGER.debug("Re-sent {} map(s) to {} after the join/dimension change", resynced, player.getPlainTextName());
    }
}
