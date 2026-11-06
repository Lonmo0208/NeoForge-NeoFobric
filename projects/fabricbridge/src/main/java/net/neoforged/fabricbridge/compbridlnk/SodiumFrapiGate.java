package net.neoforged.fabricbridge.compbridlnk;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Continuity's grass renders black when Sodium's NeoForge build runs its FRAPI emitter, and the emitter
 * runs exactly here: on upstream NeoForge the check below answers false because no Fabric renderer API
 * exists for FML to see, so Sodium falls back to its default emitter - the path where every quad is
 * re-tinted by Sodium's own per-block color provider. The bridge publishes the Fabric modules to FML,
 * so {@code isModInLoadingList("fabric-renderer-api-v1")} succeeds and the FRAPI emitter activates on a
 * build whose renderer-side mixins ({@code sodium-frapi}, skipped by its own {@code requiredMods}) were
 * never meant to back it. Continuity's child models then emit through Fabric's indigo, whose quad types
 * do not match what Sodium's wrapper expects (the measured {@code MutableMeshImpl$1 -> MutableQuadView}
 * ClassCastException), and the emitted quads lose the biome tint - grass, leaves and every CTM-wrapped
 * plant render pure black.
 *
 * <p>The fix is the state upstream never leaves: force the provider's {@code isEnabled()} to answer
 * false so the default emitter runs. Its {@code collectParts} path re-tints every quad itself, and
 * Continuity's level-aware {@code collectParts} (see {@link ContinuityCompat}) answers it with
 * correctly tinted parts.
 */
public final class SodiumFrapiGate {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoFobric/Compat/SodiumFrapiGate");

    public static final String PROVIDER_CLASS =
            "net/caffeinemc/mods/sodium/client/render/frapi/FRAPIEmitterProvider.class";

    private SodiumFrapiGate() {}

    /**
     * Rewrites {@code FRAPIEmitterProvider.isEnabled()} to answer false.
     *
     * @return the rewritten bytes, or {@code null} when this entry is not the provider or the method is
     *         missing (a future Sodium layout this gate does not understand - left untouched)
     */
    public static byte[] forceDefaultEmitter(byte[] classBytes) {
        ClassNode node;
        try {
            node = new ClassNode();
            new ClassReader(classBytes).accept(node, 0);
        } catch (Throwable t) {
            return null;
        }
        for (var method : node.methods) {
            if ("isEnabled".equals(method.name) && "()Z".equals(method.desc)) {
                method.instructions.clear();
                method.tryCatchBlocks = null;
                method.localVariables = null;
                method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                method.instructions.add(new InsnNode(Opcodes.IRETURN));
                method.maxStack = 1;
                var writer = new ClassWriter(0);
                node.accept(writer);
                LOGGER.info("[NeoFobric] Sodium's FRAPI emitter provider now answers disabled, so the "
                        + "default emitter (and its per-block tinting) runs on this build");
                return writer.toByteArray();
            }
        }
        LOGGER.warn("[NeoFobric] Sodium's FRAPI emitter provider carries no isEnabled()Z - left untouched");
        return null;
    }
}
