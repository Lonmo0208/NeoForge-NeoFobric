/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fabricbridge.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.AnnotationNode;

/**
 * Pins the annotation walk behind the realign pass.
 *
 * <p>A target can sit directly on {@code @At}, inside a list of {@code @At}s (MixinExtras' {@code slice()}
 * and {@code @Slice(from = ...)}), or two levels down; the walk misses a level and the injection silently
 * stops matching, which is only visible as a feature that no longer fires.
 */
class MixinJarPatcherRealignTest {
    private static final String OLD_RUN =
            "run(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/multiplayer/ClientLevel;"
                    + "Lnet/minecraft/client/gui/screens/LevelLoadingScreen$Reason;)V";
    private static final String NEW_RUN =
            "run(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/multiplayer/ClientLevel;"
                    + "Lnet/minecraft/client/gui/screens/LevelLoadingScreen$Reason;"
                    + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;)V";

    @Test
    void realignsATargetOnAtDirectly() {
        var at = at(OLD_RUN);
        assertTrue(MixinJarPatcher.realignAnnotations(List.of(at), Map.of()));
        assertEquals(NEW_RUN, valueOf(at, "target"));
    }

    @Test
    void realignsTargetsInsideAnnotationLists() {
        var inner = at(OLD_RUN);
        var slice = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Slice;");
        slice.visit("from", List.of(inner));
        assertTrue(MixinJarPatcher.realignAnnotations(List.of(slice), Map.of()));
        assertEquals(NEW_RUN, valueOf(inner, "target"));
    }

    @Test
    void leavesUnknownTargetsAloneAndHonoursTheScopedMap() {
        var plain = at("tick()V");
        assertFalse(MixinJarPatcher.realignAnnotations(List.of(plain), Map.of()));
        assertEquals("tick()V", valueOf(plain, "target"));

        var params = "(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/multiplayer/ClientLevel;"
                + "Lnet/minecraft/client/gui/screens/LevelLoadingScreen$Reason;)";
        var scopedTarget = at("run" + params + "V");
        assertTrue(MixinJarPatcher.realignAnnotations(List.of(scopedTarget), Map.of(params, "(IX)")));
        assertEquals("run(IX)V", valueOf(scopedTarget, "target"));
    }

    private static AnnotationNode at(String target) {
        var at = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
        at.visit("value", "INVOKE");
        at.visit("target", target);
        return at;
    }

    @SuppressWarnings("unchecked")
    private static String valueOf(AnnotationNode annotation, String key) {
        for (var i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                return (String) annotation.values.get(i + 1);
            }
        }
        return null;
    }
}
