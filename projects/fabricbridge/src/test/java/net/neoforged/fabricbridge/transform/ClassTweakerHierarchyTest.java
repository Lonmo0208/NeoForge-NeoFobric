/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fabricbridge.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * Pins the two shapes that made the transitive-interface pass fail once.
 *
 * <p>The rule text carries a generic signature ({@code FabricTagAppender<TT;>}) that is not a class name,
 * and the walk must never ask for the class that is currently being transformed: that re-entered the
 * transformation and came out as "Could not load super class TagAppender" during the data-generation
 * bootstrap.
 */
class ClassTweakerHierarchyTest {
    @Test
    void stripsTheGenericSignatureFromARuleInterface() {
        assertEquals("net/fabricmc/fabric/api/datagen/v1/provider/FabricTagAppender",
                ClassTweakerHierarchy.rawInterfaceName(
                        "net/fabricmc/fabric/api/datagen/v1/provider/FabricTagAppender<TT;>"));
        assertEquals("com/example/Plain", ClassTweakerHierarchy.rawInterfaceName("com/example/Plain"));
        assertEquals("x/Y", ClassTweakerHierarchy.rawInterfaceName("  x/Y  "));
    }

    @Test
    void walksTheWholeHierarchyWithoutAskingForTheClassItself() {
        var node = new ClassNode();
        node.name = "test/Child";
        node.superName = "test/Parent";
        node.interfaces = new ArrayList<>(List.of("test/Marker"));

        var asked = new ArrayList<String>();
        var bytes = (ClassTweakerHierarchy.ClassBytes) className -> {
            asked.add(className);
            return switch (className) {
                case "test.Parent" -> clazz("test/Parent", "java/lang/Object", "test/ParentMarker");
                case "test.Marker", "test.ParentMarker" ->
                    clazz(className.replace('.', '/'), "java/lang/Object");
                default -> throw new IllegalStateException("unexpected lookup " + className);
            };
        };

        var supertypes = ClassTweakerHierarchy.supertypes(node, bytes);
        assertTrue(supertypes.containsAll(Set.of("test/Child", "test/Parent", "test/Marker", "test/ParentMarker")));
        assertFalse(asked.contains("test.Child"), "the class being transformed has to come from its own node");
    }

    @Test
    void aCycleInTheHierarchyTerminates() {
        var node = new ClassNode();
        node.name = "test/A";
        node.superName = "test/B";
        var bytes = (ClassTweakerHierarchy.ClassBytes) className -> {
            if (className.equals("test.B")) {
                return clazz("test/B", "test/A");
            }
            throw new IllegalStateException("unexpected lookup " + className);
        };
        var supertypes = ClassTweakerHierarchy.supertypes(node, bytes);
        assertTrue(supertypes.containsAll(Set.of("test/A", "test/B")));
    }

    private static byte[] clazz(String name, String superName, String... interfaces) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, superName, interfaces);
        writer.visitEnd();
        return writer.toByteArray();
    }
}
