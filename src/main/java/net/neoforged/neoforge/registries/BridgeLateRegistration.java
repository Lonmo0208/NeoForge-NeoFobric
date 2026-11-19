/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.registries;

import com.mojang.logging.LogUtils;
import java.util.function.Consumer;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

/// Registration that arrives after the startup [RegisterEvent] pass, for mods a loader bridge runs.
///
/// A mod loaded through a bridge can reach a shared library's platform implementation at any time -
/// the library then asks FML for the mod's event bus and registers its entries from a listener on it.
/// On a native platform mod that code runs during construction, inside the registration window; a
/// bridged mod can reach it from ordinary gameplay instead - Fuzs' Item Interactions creates its
/// registries when the first container is clicked, and the calling code asks puzzleslib's NeoForge
/// registry manager for its mod event bus at that moment. The bus the bridge gives the mod (see
/// `net.neoforged.fabricbridge.shell`) hands every listener that arrives after the window closed to
/// [#replayTo], so the entries still register exactly once.
///
/// The replay fires one [RegisterEvent] per registry to that one listener, with the target registry
/// unfrozen for the call and frozen again afterwards - the same unfreeze/register/freeze cycle
/// [RegistryManager] itself runs when it applies a registry snapshot. Registries the game ships are
/// frozen long before gameplay, so without that cycle `Registry.register` refuses the entry.
///
/// This is a degradation path, not a registration path: the entries become available on the side that
/// ran the listener and stay missing from any registry snapshot another side may already have sent.
/// A bridged mod that registers lazily is in exactly the state a Fabric client/server pair would be in,
/// and single-player - where both sides share this process - sees no difference at all.
@ApiStatus.Internal
public final class BridgeLateRegistration {
    private static final Logger LOGGER = LogUtils.getLogger();

    /// Set once the startup registration window closed (the bridge's late shell pass ran).
    private static volatile boolean windowClosed;

    private BridgeLateRegistration() {}

    /// Closes the startup registration window: listeners that arrive after this are replayed.
    public static void closeWindow() {
        windowClosed = true;
    }

    public static boolean isWindowClosed() {
        return windowClosed;
    }

    /// Replays one [RegisterEvent] per registry into the given listener, best effort.
    ///
    /// The listener decides which registry it cares about - it filters on the event's registry key -
    /// so every registry is offered. A listener that is not a `RegisterEvent` listener at all is
    /// recognised by the first cast failing and is left alone.
    ///
    /// @param listener a listener the bridge handed over after the window closed
    public static void replayTo(Consumer<?> listener) {
        if (!windowClosed || listener == null) {
            return;
        }
        replay(null, listener);
    }

    /// Posts the [RegisterEvent] pass for every registry onto one bridged mod's bus, unfreezing each
    /// registry for the call.
    ///
    /// A bridged mod's listeners register during the Fabric entrypoints, which the bridge runs after
    /// the platform's own pass - [GameData#postRegisterEventsForShellMods] is the catch-up pass. By the
    /// time it runs, the game has often frozen the registries already (the client boots its registry
    /// state more than once), so the plain post fails with "Registry is already frozen" and the
    /// entries - an attachment type, say - never exist. Unfreezing per registry is the same cycle
    /// [RegistryManager] uses when it applies a snapshot.
    ///
    /// @param bus the bridged mod's event bus
    public static void postForBus(net.neoforged.bus.api.IEventBus bus) {
        if (bus == null) {
            return;
        }
        replay(bus, null);
    }

    private static void replay(net.neoforged.bus.api.IEventBus bus, Consumer<?> listener) {
        boolean delivered = false;
        int dispatched = 0;
        for (Identifier name : GameData.getRegistrationOrder()) {
            Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(name);
            if (registry == null) {
                continue;
            }
            ResourceKey<? extends Registry<?>> key = ResourceKey.createRegistryKey(name);
            boolean unfroze = false;
            try {
                if (registry instanceof BaseMappedRegistry<?> forgeRegistry && registry instanceof MappedRegistry<?> mapped
                        && mapped.isFrozen()) {
                    forgeRegistry.unfreeze(false);
                    unfroze = true;
                }
                var event = new RegisterEvent(key, registry);
                if (bus != null) {
                    bus.post(event);
                } else {
                    @SuppressWarnings("unchecked")
                    var registerListener = (Consumer<RegisterEvent>) listener;
                    registerListener.accept(event);
                }
                delivered = true;
                dispatched++;
            } catch (ClassCastException notARegisterListener) {
                if (!delivered) {
                    // The listener is for some other event; nothing to replay for it.
                    return;
                }
                LOGGER.warn("[NeoFobric] A late registration listener rejected the event for {}", name,
                        notARegisterListener);
            } catch (Throwable t) {
                LOGGER.warn("[NeoFobric] Late registration into {} failed", name, t);
            } finally {
                if (unfroze && registry instanceof MappedRegistry<?> mapped) {
                    try {
                        mapped.freeze();
                    } catch (Throwable t) {
                        LOGGER.warn("[NeoFobric] Could not re-freeze {} after a late registration", name, t);
                    }
                }
            }
        }
        if (dispatched > 0) {
            LOGGER.info("[NeoFobric] Replayed the register event over {} registries for a listener that arrived "
                    + "after the window closed", dispatched);
        }
    }
}
