/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fabricbridge;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs Fabric's fuel-value events on a {@code FuelValues} whose shape Fabric's own wrapper no longer
 * matches.
 *
 * <p>fabric-content-registries wraps the {@code remove(TagKey)} call inside
 * {@code FuelValues.vanillaBurnTimes(HolderLookup$Provider, FeatureFlagSet, int)} and serves its whole
 * {@code FuelValueEvents} API from there. On the 26w14a line that three-argument method is only a
 * two-line forward: it builds a {@code FuelValues.Builder} and hands it to
 * {@code vanillaBurnTimes(FuelValues$Builder, int)}, which is where the values (including that
 * {@code remove} call) actually live. The wrapper therefore anchors on a body with no such call, never
 * fires, and a fuel a Fabric mod registered never lands in {@code FuelValues} - measured as the fabric
 * testmods' fuel tests failing with "fuel hopper should have been emptied".
 *
 * <p>The bridge injects its own calls instead ({@code MixinJarPatcher#injectFuelValuesBridge}):
 * {@link #begin} on the forwarding method records the provider, the feature set and the base smelt time,
 * {@link #buildEvents} runs inside the real body before the vanilla values are added, and
 * {@link #exclusionEvents} runs right behind the {@code remove} call that follows them - the same order
 * Fabric's wrapper uses. Everything is reflective because the bridge never compiles against Fabric API.
 */
public final class FuelValuesBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoFobric/Compat/FuelValues");

    /** One entry: the provider, the feature set and the base smelt time, in Fabric's own order. */
    private static final ThreadLocal<Object[]> CONTEXT = new ThreadLocal<>();

    private static volatile boolean unavailable;

    private FuelValuesBridge() {}

    /** Called at the head of the forwarding {@code vanillaBurnTimes(provider, features, baseUnit)}. */
    public static void begin(Object registries, Object features, int baseUnit) {
        CONTEXT.set(new Object[] { registries, features, baseUnit });
    }

    /** Called just before the forwarding method returns. */
    public static void end() {
        CONTEXT.remove();
    }

    /** Called at the head of the real body, before the vanilla values are added. */
    public static void buildEvents(Object builder) {
        dispatch("BUILD", builder);
    }

    /** Called right behind the vanilla {@code remove} call, so exclusions land last. */
    public static void exclusionEvents(Object builder) {
        dispatch("EXCLUSIONS", builder);
    }

    private static void dispatch(String eventName, Object builder) {
        var context = CONTEXT.get();
        if (context == null || unavailable) {
            return;
        }
        try {
            var loader = net.fabricmc.loader.impl.launch.FabricLauncherBase.getLauncher().getTargetClassLoader();
            var eventField = Class.forName("net.fabricmc.fabric.api.registry.FuelValueEvents", true, loader)
                    .getField(eventName).get(null);
            var invokerMethod = eventField.getClass().getMethod("invoker");
            var invoker = invokerMethod.invoke(eventField);
            var invokerType = invokerMethod.getReturnType();

            var contextType = Class.forName("net.fabricmc.fabric.api.registry.FuelValueEvents$Context", true, loader);
            var builderType = Class.forName("net.minecraft.world.level.block.entity.FuelValues$Builder", true, loader);
            var contextImpl = Class.forName("net.fabricmc.fabric.impl.content.registry.FuelRegistryEventsContextImpl", true, loader);
            var contextInstance = contextImpl
                    .getConstructor(holderProviderType(loader), featureFlagSetType(loader), int.class)
                    .newInstance(context[0], context[1], context[2]);

            for (Method method : invokerType.getMethods()) {
                var parameters = method.getParameterTypes();
                if (parameters.length == 2 && parameters[0].isAssignableFrom(builderType)
                        && parameters[1].isAssignableFrom(contextImpl)) {
                    method.invoke(invoker, builder, contextInstance);
                    return;
                }
            }
            LOGGER.warn("[NeoFobric] Fabric's {} invoker carries no (builder, context) method - fuel values were not applied", eventName);
        } catch (Throwable t) {
            if (!unavailable) {
                unavailable = true;
                LOGGER.warn("[NeoFobric] Could not run Fabric's fuel-value events; Fabric-registered fuel values are unavailable on this launch", t);
            }
        }
    }

    private static Class<?> holderProviderType(ClassLoader loader) throws ClassNotFoundException {
        return Class.forName("net.minecraft.core.HolderLookup$Provider", true, loader);
    }

    private static Class<?> featureFlagSetType(ClassLoader loader) throws ClassNotFoundException {
        return Class.forName("net.minecraft.world.flag.FeatureFlagSet", true, loader);
    }
}
