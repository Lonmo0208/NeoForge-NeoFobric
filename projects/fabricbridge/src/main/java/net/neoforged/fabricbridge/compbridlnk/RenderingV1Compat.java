package net.neoforged.fabricbridge.compbridlnk;


import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * fabric-rendering-v1 on the 26w14aCp26 line: the mod targets Minecraft 26.1.2's GUI renderer, and
 * 26w14a predates pieces of it.
 *
 * <p>{@code GuiRendererMixin} shadows {@code GuiRenderer.pictureInPictureRenderers}, a field that only
 * exists from 26.1.2 on, so the mixin cannot apply here (measured: "@Shadow field
 * pictureInPictureRenderers was not located"). Its one externally visible effect is the
 * {@code GuiRendererExtensions} interface it would have carried onto the target, and
 * {@code GameRendererMixin.guiRendererReady} unconditionally casts the game's GUI renderer to that
 * interface - the cast threw {@code ClassCastException} and took the launch down. The mixin is dropped
 * (picture-in-picture GUI rendering is a known gap of this branch) and the cast is guarded: without
 * the interface the ready callback is simply skipped, which is what a Fabric install without the
 * extension would do.
 *
 * <p>{@code RenderPipelineBuilderMixin} is dropped for the same shape reason: its
 * {@code @WrapOperation} wraps a {@code RenderPipeline.Builder} call whose signature differs on 26w14a,
 * and the wrapper's Operation argument no longer matches ("has an invalid signature"). Custom
 * {@code FabricRenderPipeline} construction is the known gap there.
 */
public final class RenderingV1Compat {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoFobric/Compat/RenderingV1");

    private static final String RENDERING_MIXINS = "fabric-rendering-v1.mixins.json";
    private static final String EXTENSIONS = "net/fabricmc/fabric/impl/client/rendering/GuiRendererExtensions";
    private static final String GAME_RENDERER_MIXIN =
            "net/fabricmc/fabric/mixin/client/rendering/GameRendererMixin.class";

    private RenderingV1Compat() {}

    /**
     * Removes the mixin the 26w14a shapes still cannot carry from the rendering mixin config.
     *
     * <p>{@code GuiRendererMixin} is no longer dropped: the fork keeps the vanilla
     * {@code pictureInPictureRenderers} map and the {@code ImmutableMap.builder()} call in the
     * constructor, so its {@code @Shadow} resolves and its registrations land in the map the renderer
     * falls back to (see the GuiRenderer patch). {@code RenderPipelineBuilderMixin} still wraps a
     * {@code RenderPipeline.Builder} method whose signature differs on this base.
     *
     * @return the filtered config text, or {@code null} when this is not the rendering config or
     *         nothing needed dropping
     */
    public static String withoutInapplicableMixins(String configText) {
        var filtered = configText.replaceAll("(?m)^\\s*\"RenderPipelineBuilderMixin\"\\s*,?\\s*\\r?\\n", "");
        return filtered.equals(configText) ? null : filtered;
    }

    /**
     * Guards the GUI renderer cast inside GameRendererMixin's ready callback.
     *
     * @return the rewritten bytes, or {@code null} when this entry is not the mixin class or nothing
     *         needed guarding
     */
    public static byte[] guardGuiRendererCast(byte[] classBytes, String entryName) {
        if (!GAME_RENDERER_MIXIN.equals(entryName)) {
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
            changed |= guardCastIn(method);
        }
        if (!changed) {
            return null;
        }
        var writer = new ClassWriter(0);
        node.accept(writer);
        LOGGER.info("[NeoFobric] Guarded GameRendererMixin's GUI renderer cast - GuiRendererMixin is not"
                + " applied on this branch (its @Shadow field is 26.1.2-only), so the ready callback"
                + " skips instead of casting");
        return writer.toByteArray();
    }

    /**
     * Turns {@code ((GuiRendererExtensions) this.guiRenderer).fabric_*} into an instanceof-guarded
     * call.
     *
     * <p>The guarded shape is the whole method: these handlers are straight-line void callbacks, so
     * the guard jumps to a return appended at the end, and the one branch target gets a frame rebuilt
     * from the method's own locals.
     */
    private static boolean guardCastIn(MethodNode method) {
        MethodInsnNode interfaceCall = null;
        TypeInsnNode cast = null;
        FieldInsnNode fieldRead = null;
        FrameNode originalFrame = null;
        for (var insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (originalFrame == null && insn instanceof FrameNode frame && frame.local != null) {
                originalFrame = frame;
            }
            if (insn instanceof TypeInsnNode candidate
                    && candidate.getOpcode() == Opcodes.CHECKCAST
                    && EXTENSIONS.equals(candidate.desc)) {
                cast = candidate;
                var prev = cast.getPrevious();
                if (prev instanceof FieldInsnNode read && read.getOpcode() == Opcodes.GETFIELD) {
                    fieldRead = read;
                }
                continue;
            }
            if (cast != null && insn.getOpcode() == Opcodes.INVOKEINTERFACE) {
                interfaceCall = (MethodInsnNode) insn;
                break;
            }
        }
        if (cast == null || fieldRead == null || interfaceCall == null) {
            return false;
        }
        // Rebuild the whole handler: the shipped method carries its own stack map frames, which the
        // inserted branch would invalidate. The captured nodes are the originals; everything else is
        // the guarded sequence with one frame at the skip target. The frame's locals come from the
        // original frames - the descriptor alone mis-states the long parameter lists these handlers
        // carry (measured: a frame built from it verified as "top" in locals).
        java.util.List<Object> locals;
        if (originalFrame != null && originalFrame.local != null) {
            locals = new java.util.ArrayList<>(originalFrame.local);
        } else {
            locals = new java.util.ArrayList<>();
            if ((method.access & Opcodes.ACC_STATIC) == 0) {
                locals.add("net/fabricmc/fabric/mixin/client/rendering/GameRendererMixin");
            }
            for (var arg : Type.getArgumentTypes(method.desc)) {
                locals.add(arg.getDescriptor().startsWith("[") ? arg.getDescriptor() : arg.getInternalName());
            }
        }
        var skipLabel = new LabelNode();
        method.instructions.clear();
        method.tryCatchBlocks = null;
        method.localVariables = null;
        method.visibleTypeAnnotations = null;
        method.invisibleTypeAnnotations = null;
        var insns = method.instructions;
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(fieldRead);
        insns.add(new InsnNode(Opcodes.DUP));
        insns.add(new TypeInsnNode(Opcodes.INSTANCEOF, EXTENSIONS));
        insns.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        insns.add(cast);
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new FieldInsnNode(Opcodes.GETFIELD, interfaceCall.owner, "submitNodeStorage",
                "Lnet/minecraft/client/renderer/SubmitNodeStorage;"));
        insns.add(interfaceCall);
        // The fall-through path ends here, its stack empty.
        insns.add(new InsnNode(Opcodes.RETURN));
        // The skip path arrives with the un-cast renderer still on the stack.
        insns.add(skipLabel);
        insns.add(new FrameNode(Opcodes.F_FULL, locals.size(), locals.toArray(), 1,
                new Object[] {"net/minecraft/client/gui/render/GuiRenderer"}));
        insns.add(new InsnNode(Opcodes.POP));
        insns.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = Math.max(method.maxStack, 3);
        return true;
    }
}
