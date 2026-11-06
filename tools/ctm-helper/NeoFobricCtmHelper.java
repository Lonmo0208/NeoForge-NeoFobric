package me.pepperbell.continuity.client.model;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import me.pepperbell.continuity.client.util.RenderUtil;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableMeshImpl;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs Continuity's connected-texture transform for a renderer that collects models through the
 * level-aware five-argument {@code BlockStateModel.collectParts}.
 *
 * <p>The transform lives in Fabric's FRAPI {@code emitQuads}. This helper emits the model into a
 * FRAPI mesh (Indigo's {@code MutableMeshImpl}), resolves each quad's sprite through Continuity's
 * own block-atlas finder, and rebuilds ordinary {@code BlockStateModelPart}s. It lives in
 * Continuity's package inside Continuity's own jar on purpose: the bridge's classes are not visible
 * to the Fabric mod class loader, while Minecraft and Fabric API are.
 */
public final class NeoFobricCtmHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoFobricCTM/Helper");
    private static final AtomicInteger CALLS = new AtomicInteger();

    private NeoFobricCtmHelper() {}

    public static void collect(BlockStateModel model, BlockAndTintGetter level, BlockPos pos,
            BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        boolean first = CALLS.getAndIncrement() == 0;
        boolean levelEmpty = level == BlockAndTintGetter.EMPTY;
        boolean fabricModel = model instanceof FabricBlockStateModel;
        SpriteFinder finder = !levelEmpty && fabricModel ? RenderUtil.getSpriteFinder() : null;
        if (first) {
            LOGGER.info("[NeoFobric] CTM helper collect: model={}, levelEmpty={}, fabricModel={}, finder={}",
                    model.getClass().getName(), levelEmpty, fabricModel, finder != null);
        }
        if (levelEmpty || !fabricModel || finder == null) {
            model.collectParts(random, parts);
            return;
        }
        try {
            MutableMesh mesh = new MutableMeshImpl();
            QuadEmitter emitter = mesh.emitter();
            ((FabricBlockStateModel) model).emitQuads(emitter, level, pos, state, random, direction -> false);
            QuadCollection.Builder builder = new QuadCollection.Builder();
            // The part's ambient-occlusion flag is what Sodium's chunk renderer reads; the per-quad
            // MaterialInfo flag only reaches renderers that look at it. Continuity marks every cutout
            // model (grass, flowers, cross plants) ambientOcclusion=FALSE, and forcing the part to true
            // makes Sodium smooth-light those quads inside the block's own space: the surrounding grass
            // occludes them to roughly a fifth of the light and they render almost black without a
            // shader pack. A part is unoccluded when no quad asks for AO.
            boolean[] useAo = {false};
            mesh.forEach(quad -> {
                TextureAtlasSprite sprite = finder.find(quad);
                if (sprite == null) {
                    return;
                }
                if (net.fabricmc.fabric.api.util.TriState.FALSE != quad.ambientOcclusion()) {
                    useAo[0] = true;
                }
                // toBakedQuad() goes through the six-argument MaterialInfo constructor, which defaults
                // ambientOcclusion to true; carry the quad's own flag over for renderers that read it.
                BakedQuad plain = quad.toBakedQuad(sprite);
                BakedQuad baked = plain;
                if (net.fabricmc.fabric.api.util.TriState.FALSE == quad.ambientOcclusion()) {
                    var mi = plain.materialInfo();
                    baked = new BakedQuad(
                            plain.position0(), plain.position1(), plain.position2(), plain.position3(),
                            plain.packedUV0(), plain.packedUV1(), plain.packedUV2(), plain.packedUV3(),
                            plain.direction(),
                            new BakedQuad.MaterialInfo(mi.sprite(), mi.layer(), mi.itemRenderType(),
                                    mi.tintIndex(), mi.shade(), mi.lightEmission(), false),
                            plain.bakedNormals(), plain.bakedColors());
                }
                Direction cullFace = quad.cullFace();
                if (cullFace == null) {
                    builder.addUnculledFace(baked);
                } else {
                    builder.addCulledFace(cullFace, baked);
                }
            });
            parts.add(new SimpleModelWrapper(builder.build(), useAo[0], model.particleMaterial()));
            if (first) {
                LOGGER.info("[NeoFobric] CTM helper produced {} part(s) with {} quad(s)",
                        parts.size(), ((MutableMeshImpl) mesh).size());
            }
        } catch (Throwable t) {
            if (first) {
                LOGGER.warn("[NeoFobric] CTM helper fell back to the vanilla parts", t);
            }
            model.collectParts(random, parts);
        }
    }
}
