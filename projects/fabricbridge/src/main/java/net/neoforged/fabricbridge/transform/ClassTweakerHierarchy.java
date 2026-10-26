/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fabricbridge.transform;

import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * Pure helpers for the transitive-inject-interface pass, kept out of the class processor so they can be
 * pinned by a unit test without an FML class path.
 */
final class ClassTweakerHierarchy {
    private ClassTweakerHierarchy() {}

    /**
     * {@return the raw class name of a rule's interface, with any generic signature stripped}
     *
     * <p>A rule may spell the interface as {@code FabricTagAppender<TT;>}; that is a signature, not a class
     * name, and adding it verbatim made the first run die with a ClassNotFoundException on the interface.
     */
    static String rawInterfaceName(String iface) {
        var generic = iface.indexOf('<');
        return (generic >= 0 ? iface.substring(0, generic) : iface).strip();
    }

    /** The bytecode of another class, keyed by its dot-separated name. */
    interface ClassBytes {
        byte[] get(String dotName) throws ClassNotFoundException;
    }

    /**
     * {@return every supertype of {@code node}, itself included}
     *
     * <p>The node's own parents are taken from the node, never from {@code bytes}: asking for the class that
     * is being defined right now re-enters its transformation, which showed up as "Could not load super
     * class TagAppender" during the data-generation bootstrap. Ancestors are read through the provider and
     * the walk stops at the first repeated name, so a malformed cycle cannot spin.
     */
    static Set<String> supertypes(ClassNode node, ClassBytes bytes) {
        var collected = new java.util.LinkedHashSet<String>();
        collected.add(node.name);
        if (node.superName != null) {
            collect(node.superName, collected, 1, bytes);
        }
        for (var iface : node.interfaces) {
            collect(iface, collected, 1, bytes);
        }
        return Set.copyOf(collected);
    }

    private static void collect(String internalName, Set<String> out, int depth, ClassBytes bytes) {
        if (internalName == null || internalName.isEmpty() || depth > 64 || !out.add(internalName)) {
            return;
        }
        if (bytes == null) {
            return;
        }
        try {
            var reader = new ClassReader(bytes.get(internalName.replace('/', '.')));
            if (reader.getSuperName() != null) {
                collect(reader.getSuperName(), out, depth + 1, bytes);
            }
            for (var iface : reader.getInterfaces()) {
                collect(iface, out, depth + 1, bytes);
            }
        } catch (Throwable t) {
            // The hierarchy ends here; the class is not on the game class path.
        }
    }
}
