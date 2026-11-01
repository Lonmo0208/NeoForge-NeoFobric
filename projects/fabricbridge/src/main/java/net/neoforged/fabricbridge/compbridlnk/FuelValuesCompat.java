package net.neoforged.fabricbridge.compbridlnk;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wires the bridge's fuel-value event dispatch into {@code FuelValues}.
 *
 * <p>fabric-content-registries serves its fuel-value API by wrapping the {@code remove(TagKey)} call in
 * {@code FuelValues.vanillaBurnTimes(provider, features, baseUnit)}. On the 26w14a line that method only
 * forwards to {@code vanillaBurnTimes(FuelValues$Builder, int)} - the body with the actual values moved
 * there - so Fabric's wrapper has nothing to anchor on and never fires (see {@code FuelValuesBridge}).
 *
 * <p>Two straight-line edits restore it: the forwarding method records its three arguments for the
 * duration of the call, and the real body runs the Fabric events before the vanilla values go in, with
 * the exclusion event right behind the vanilla {@code remove}. No branches are added inside the body
 * (the exclusion call is reached by {@code DUP}-ing the builder that {@code remove} already left on the
 * stack), so the class's stack map frames stay valid.
 */
public final class FuelValuesCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoFobric/Compat/FuelValues");

    private static final String FUEL_VALUES_CLASS = "net/minecraft/world/level/block/entity/FuelValues.class";
    private static final String BRIDGE = "net/neoforged/fabricbridge/FuelValuesBridge";
    private static final String BUILDER_DESC = "Lnet/minecraft/world/level/block/entity/FuelValues$Builder;";

    private FuelValuesCompat() {}

    /**
     * @return the rewritten class bytes, or {@code null} when this is not {@code FuelValues} or the
     *         expected methods are absent
     */
    public static byte[] inject(byte[] classBytes, String entryName) {
        if (!FUEL_VALUES_CLASS.equals(entryName)) {
            return null;
        }
        ClassNode node;
        try {
            node = new ClassNode();
            new ClassReader(classBytes).accept(node, 0);
        } catch (Throwable t) {
            return null;
        }

        MethodNode forwarding = null;
        MethodNode body = null;
        for (var method : node.methods) {
            if (!"vanillaBurnTimes".equals(method.name)) {
                continue;
            }
            if ("(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/world/flag/FeatureFlagSet;I)Lnet/minecraft/world/level/block/entity/FuelValues;".equals(method.desc)) {
                forwarding = method;
            } else if (("(" + BUILDER_DESC + "I)Lnet/minecraft/world/level/block/entity/FuelValues;").equals(method.desc)) {
                body = method;
            }
        }
        if (forwarding == null || body == null) {
            return null;
        }

        var head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new VarInsnNode(Opcodes.ALOAD, 1));
        head.add(new VarInsnNode(Opcodes.ILOAD, 2));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "begin",
                "(Ljava/lang/Object;Ljava/lang/Object;I)V", false));
        forwarding.instructions.insert(head);

        // end() right before the tail return; the forwarding body is a single return.
        for (var insn = forwarding.instructions.getLast(); insn != null; insn = insn.getPrevious()) {
            if (insn.getOpcode() == Opcodes.ARETURN) {
                forwarding.instructions.insertBefore(insn,
                        new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "end", "()V", false));
                break;
            }
        }

        // build events at the head of the real body (builder is argument 0).
        var bodyHead = new InsnList();
        bodyHead.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bodyHead.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "buildEvents", "(Ljava/lang/Object;)V", false));
        body.instructions.insert(bodyHead);

        // exclusion events right behind the vanilla remove(TagKey) call: DUP the builder it left on the
        // stack and consume the copy.
        var injected = false;
        for (AbstractInsnNode insn = body.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode call
                    && "remove".equals(call.name)
                    && "net/minecraft/world/level/block/entity/FuelValues$Builder".equals(call.owner)) {
                var after = new InsnList();
                after.add(new InsnNode(Opcodes.DUP));
                after.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "exclusionEvents", "(Ljava/lang/Object;)V", false));
                body.instructions.insert(call, after);
                injected = true;
                break;
            }
        }
        if (!injected) {
            return null;
        }

        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        LOGGER.info("[NeoFobric] Wired Fabric's fuel-value events into FuelValues: the 26w14a shape moved the"
                + " real body behind a forwarding overload, where Fabric's own wrapper can no longer reach it");
        return writer.toByteArray();
    }
}
