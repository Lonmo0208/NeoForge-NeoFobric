package net.neoforged.fabricbridge.compbridlnk;


import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
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
 * <p>{@code GuiRendererMixin} is kept: the fork's {@code GuiRenderer} patch keeps the vanilla
 * {@code pictureInPictureRenderers} map and the {@code ImmutableMap.builder()} call in the constructor,
 * so the mixin's {@code @Shadow} resolves and its registrations land in the map the renderer falls back
 * to. {@code RenderPipelineBuilderMixin} wraps {@code RenderPipeline.Builder.buildSnippet}'s
 * {@code NEW RenderPipeline.Snippet} call; 26w14a added a twelfth constructor parameter
 * ({@code stencilTest}), so the wrapper's {@code Operation} argument no longer matches and MixinExtras
 * rejects the whole mixin ("has an invalid signature"). The class is rewritten here to the twelve-argument
 * shape (one more {@code Optional} parameter, carried through the lambda and the {@code Operation.call}
 * array), so the mixin applies and custom {@code FabricRenderPipeline} construction works.
 */
public final class RenderingV1Compat {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoFobric/Compat/RenderingV1");

    private static final String RENDERING_MIXINS = "fabric-rendering-v1.mixins.json";
    private static final String PIPELINE_BUILDER_MIXIN =
            "net/fabricmc/fabric/mixin/client/rendering/RenderPipelineBuilderMixin.class";
    private static final String PIPELINE_BUILDER_HANDLER = "copyUsePipelineDrawModeForGuiToSnippet";
    private static final String PIPELINE_BUILDER_LAMBDA = "lambda$copyUsePipelineDrawModeForGuiToSnippet$0";
    private static final String PIPELINE_INTERNALS = "net/fabricmc/fabric/impl/client/rendering/FabricRenderPipelineInternals";
    private static final String OPERATION = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String OPTIONAL = "java/util/Optional";

    private RenderingV1Compat() {}

    /**
     * Removes the mixin the 26w14a shapes still cannot carry from the rendering mixin config.
     *
     * <p>{@code GuiRendererMixin} is kept: the fork keeps the vanilla {@code pictureInPictureRenderers}
     * map and the {@code ImmutableMap.builder()} call in the constructor, so its {@code @Shadow}
     * resolves and its registrations land in the map the renderer falls back to (see the GuiRenderer
     * patch). {@code RenderPipelineBuilderMixin} is kept too when its class could be rewritten to the
     * 26w14a snippet shape (see {@link #patchRenderPipelineBuilderMixin}); when it could not, it is
     * dropped here so a required-mixin failure does not take the launch down.
     *
     * @return the filtered config text, or {@code null} when this is not the rendering config or
     *         nothing needed dropping
     */
    public static String withoutInapplicableMixins(String configText, boolean keepPipelineBuilderMixin) {
        if (keepPipelineBuilderMixin) {
            return null;
        }
        var filtered = configText.replaceAll("(?m)^\\s*\"RenderPipelineBuilderMixin\"\\s*,?\\s*\\r?\\n", "");
        return filtered.equals(configText) ? null : filtered;
    }

    /**
     * Widens {@code RenderPipelineBuilderMixin}'s snippet wrapper to 26w14a's {@code RenderPipeline.Snippet}
     * constructor, which carries one more parameter ({@code Optional<StencilTest>}) than 26.1.2's.
     *
     * <p>MixinExtras matches a {@code @WrapOperation} handler by the wrapped call's exact descriptor; the
     * handler and its lambda therefore get one more {@code Optional} parameter, and the
     * {@code Operation.call(Object[])} array one more element. Both descriptors and the
     * {@code invokedynamic} bootstrap handle move with them.
     *
     * @return the rewritten bytes, or {@code null} when this entry is not the mixin class or its shape is
     *         not the one this rewrite knows
     */
    public static byte[] patchRenderPipelineBuilderMixin(byte[] classBytes, String entryName) {
        if (!PIPELINE_BUILDER_MIXIN.equals(entryName)) {
            return null;
        }
        ClassNode node;
        try {
            node = new ClassNode();
            new ClassReader(classBytes).accept(node, 0);
        } catch (Throwable t) {
            return null;
        }
        MethodNode handler = null;
        MethodNode lambda = null;
        for (var method : node.methods) {
            if (PIPELINE_BUILDER_HANDLER.equals(method.name)) {
                handler = method;
            } else if (PIPELINE_BUILDER_LAMBDA.equals(method.name)) {
                lambda = method;
            }
        }
        if (handler == null || lambda == null) {
            return null;
        }
        var handlerArgs = Type.getArgumentTypes(handler.desc);
        var lambdaArgs = Type.getArgumentTypes(lambda.desc);
        // Both still have 26.1.2's shape: the handler is eleven Optionals followed by the Operation, the
        // lambda the Operation followed by the same eleven Optionals. Anything else is a different build.
        if (handlerArgs.length != 12 || lambdaArgs.length != 12
                || !Type.getObjectType(OPERATION).equals(handlerArgs[11])
                || !Type.getObjectType(OPERATION).equals(lambdaArgs[0])) {
            return null;
        }
        InvokeDynamicInsnNode indy = null;
        for (var insn = handler.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof InvokeDynamicInsnNode candidate && "get".equals(candidate.name)) {
                indy = candidate;
                break;
            }
        }
        if (indy == null) {
            return null;
        }
        var optional = Type.getObjectType(OPTIONAL);
        var handlerParamTypes = new Type[13];
        var lambdaParamTypes = new Type[13];
        handlerParamTypes[12] = Type.getObjectType(OPERATION);
        lambdaParamTypes[0] = Type.getObjectType(OPERATION);
        for (int i = 0; i < 12; i++) {
            handlerParamTypes[i] = optional;
            lambdaParamTypes[i + 1] = optional;
        }
        var newHandlerDesc = Type.getMethodDescriptor(Type.getReturnType(handler.desc), handlerParamTypes);
        var newLambdaDesc = Type.getMethodDescriptor(Type.getReturnType(lambda.desc), lambdaParamTypes);
        var newIndyDesc = Type.getMethodDescriptor(Type.getObjectType("java/util/function/Supplier"),
                new Type[] {
                        Type.getObjectType(OPERATION), optional, optional, optional, optional, optional, optional,
                        optional, optional, optional, optional, optional, optional
                });
        // The bootstrap arguments are [samMethodType, implMethod, instantiatedMethodType]; only the handle
        // to the lambda changes, since its parameter list is part of the descriptor.
        var newBsmArgs = new Object[indy.bsmArgs.length];
        System.arraycopy(indy.bsmArgs, 0, newBsmArgs, 0, indy.bsmArgs.length);
        newBsmArgs[1] = new Handle(Opcodes.H_INVOKESTATIC, node.name, PIPELINE_BUILDER_LAMBDA, newLambdaDesc,
                false);

        var handlerInsns = new InsnList();
        // Operation is now the thirteenth parameter (slot 13 after `this`), then the twelve captured values.
        handlerInsns.add(new VarInsnNode(Opcodes.ALOAD, 13));
        for (int i = 1; i <= 12; i++) {
            handlerInsns.add(new VarInsnNode(Opcodes.ALOAD, i));
        }
        handlerInsns.add(new InvokeDynamicInsnNode("get", newIndyDesc, indy.bsm, newBsmArgs));
        handlerInsns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        handlerInsns.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "usePipelineDrawModeForGui",
                "L" + OPTIONAL + ";"));
        handlerInsns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PIPELINE_INTERNALS,
                "withSnippetUsePipelineVertexFormatForGui",
                "(Ljava/util/function/Supplier;L" + OPTIONAL + ";)"
                        + "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Snippet;",
                false));
        handlerInsns.add(new InsnNode(Opcodes.ARETURN));
        handler.instructions = handlerInsns;
        handler.desc = newHandlerDesc;
        handler.maxStack = 13;
        handler.maxLocals = 14;
        handler.localVariables = null;

        var lambdaInsns = new InsnList();
        lambdaInsns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        lambdaInsns.add(new IntInsnNode(Opcodes.BIPUSH, 12));
        lambdaInsns.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        for (int i = 0; i < 12; i++) {
            lambdaInsns.add(new InsnNode(Opcodes.DUP));
            lambdaInsns.add(i <= 5 ? new InsnNode(Opcodes.ICONST_0 + i) : new IntInsnNode(Opcodes.BIPUSH, i));
            lambdaInsns.add(new VarInsnNode(Opcodes.ALOAD, i + 1));
            lambdaInsns.add(new InsnNode(Opcodes.AASTORE));
        }
        lambdaInsns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, OPERATION, "call",
                "([Ljava/lang/Object;)Ljava/lang/Object;", true));
        lambdaInsns.add(new TypeInsnNode(Opcodes.CHECKCAST,
                "com/mojang/blaze3d/pipeline/RenderPipeline$Snippet"));
        lambdaInsns.add(new InsnNode(Opcodes.ARETURN));
        lambda.instructions = lambdaInsns;
        lambda.desc = newLambdaDesc;
        lambda.maxStack = 5;
        lambda.maxLocals = 13;
        lambda.localVariables = null;

        var writer = new ClassWriter(0);
        node.accept(writer);
        LOGGER.info("[NeoFobric] Widened RenderPipelineBuilderMixin's snippet wrapper to 26w14a's"
                + " twelve-argument RenderPipeline.Snippet constructor - FabricRenderPipeline builders"
                + " carry their GUI draw-mode flag again");
        return writer.toByteArray();
    }
}
