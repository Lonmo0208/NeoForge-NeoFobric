package net.neoforged.fabricbridge.compbridlnk;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Targeted compatibility patches for c2me.
 *
 * <p>c2me starts its FlowSched worker threads from {@code GlobalExecutors}' static initialiser. Each
 * worker immediately runs a prologue that loads {@code ThreadPriorityPresets} (and its OSHI/native
 * bindings) to set its own thread priority. That class load happens while the main thread is still
 * inside {@code MixinProcessor.selectConfigs}, and Mixin's config map is not safe against a class load
 * from another thread at that moment: the worker dies with
 * {@code MixinException: java.util.ConcurrentModificationException} before it ever picks up a task.
 *
 * <p>Replacing the prologue with a no-op removes the only class load the fresh workers perform during
 * mixin selection. Thread priorities are a tuning detail; chunk generation does not depend on them.
 */
public final class C2meCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoFobric/Compat/c2me");

    private C2meCompat() {}

    /**
     * {@return the class bytes with the worker prologue neutralised, or {@code null} when this is not
     * c2me's {@code GlobalExecutors} or it carries no prologue}
     */
    public static byte[] neutraliseWorkerPrologue(byte[] classBytes, String entryName) {
        var className = entryName.endsWith(".class")
                ? entryName.substring(0, entryName.length() - ".class".length())
                : entryName;
        if (!"com/ishland/c2me/base/common/GlobalExecutors".equals(className)) {
            return null;
        }
        ClassNode node;
        try {
            node = new ClassNode();
            new ClassReader(classBytes).accept(node, 0);
        } catch (Throwable t) {
            return null;
        }
        for (var method : node.methods) {
            if (!"lambda$static$1".equals(method.name) || !"()V".equals(method.desc)) {
                continue;
            }
            method.instructions.clear();
            method.instructions.add(new InsnNode(Opcodes.RETURN));
            method.tryCatchBlocks.clear();
            method.localVariables = null;
            var writer = new ClassWriter(0);
            node.accept(writer);
            LOGGER.info("[NeoFobric] Neutralised c2me's worker thread-priority prologue: it loaded classes"
                    + " on the new workers while Mixin was selecting configs, which killed them with a"
                    + " ConcurrentModificationException");
            return writer.toByteArray();
        }
        return null;
    }

    /**
     * Bounds c2me's shutdown wait, which otherwise never ends while another mod keeps loading chunks.
     *
     * <p>{@code MixinMinecraftServer} spins on {@code TheChunkSystem.itemCount()} while a level closes,
     * logging "waiting for N chunks to unload" every other second, and only stops when the count reaches
     * zero. MCA's structure locator is a background executor that keeps synchronously generating chunks
     * (its worker shows up as {@code MCA-Structure-Locator} in the task trace), so the count hovers around
     * twenty forever and "save and quit" hangs with the server thread spinning.
     *
     * <p>The receiver is already on the stack at that call site, so it can be handed to a static hook that
     * answers the same question and gives up after a bounded wait. Nothing else about c2me changes: the
     * count is reported untouched until the deadline passes.
     *
     * @return the rewritten class bytes, or {@code null} when this is not that mixin or the call is gone
     */
    public static byte[] boundShutdownWait(byte[] classBytes, String entryName) {
        var className = entryName.endsWith(".class")
                ? entryName.substring(0, entryName.length() - ".class".length())
                : entryName;
        if (!"com/ishland/c2me/rewrites/chunksystem/mixin/MixinMinecraftServer".equals(className)) {
            return null;
        }
        ClassNode node;
        try {
            node = new ClassNode();
            new ClassReader(classBytes).accept(node, 0);
        } catch (Throwable t) {
            return null;
        }
        var changed = false;
        for (var method : node.methods) {
            for (var instruction : method.instructions.toArray()) {
                if (!(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call)) {
                    continue;
                }
                if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !"com/ishland/c2me/rewrites/chunksystem/common/TheChunkSystem".equals(call.owner)
                        || !"itemCount".equals(call.name)
                        || !"()I".equals(call.desc)) {
                    continue;
                }
                var hook = new org.objectweb.asm.tree.MethodInsnNode(Opcodes.INVOKESTATIC,
                        "net/neoforged/fabricbridge/FabricBridgeHooks", "boundedChunkCount",
                        "(Lcom/ishland/c2me/rewrites/chunksystem/common/TheChunkSystem;)I", false);
                method.instructions.set(call, hook);
                changed = true;
            }
        }
        if (!changed) {
            return null;
        }
        var writer = new ClassWriter(0);
        node.accept(writer);
        LOGGER.info("[NeoFobric] Bounded c2me's chunk-unload wait: a background mod still loading chunks"
                + " kept it spinning forever, so save-and-quit never finished");
        return writer.toByteArray();
    }
}
