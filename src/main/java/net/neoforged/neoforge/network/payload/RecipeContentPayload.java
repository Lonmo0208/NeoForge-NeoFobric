/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.network.payload;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * We use this to transfer the actual recipe content from server to client.
 */
@ApiStatus.Internal
public record RecipeContentPayload(
        Set<RecipeType<?>> recipeTypes,
        List<RecipeHolder<?>> recipes) implements CustomPacketPayload {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<RecipeContentPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NeoForgeMod.MOD_ID, "recipe_content"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeContentPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.RECIPE_TYPE).apply(ByteBufCodecs.collection(HashSet::new)), RecipeContentPayload::recipeTypes,
            RecipeHolder.STREAM_CODEC.apply(ByteBufCodecs.list()), RecipeContentPayload::recipes,
            RecipeContentPayload::new);

    public static RecipeContentPayload create(Collection<RecipeType<?>> recipeTypes, RecipeMap recipes) {
        return create(recipeTypes, recipes, null);
    }

    /**
     * NeoFobric: builds the payload, dropping recipes that cannot be encoded.
     *
     * <p>A recipe serializer is free to use a network codec that only accepts one specific instance (the vanilla
     * {@code StreamCodec.unit} shape), while the recipe manager holds a different one - Enderite Mod builds its
     * special recipe exactly like that. Encoding such a recipe throws, and because this payload is encoded on the
     * netty thread the throw killed the connection right after the player joined. A recipe the network cannot
     * carry is now left out instead of taking the connection down; the client simply does not receive it.
     *
     * @param registryAccess the registry access to resolve the recipes against, or {@code null} to skip the check
     */
    public static RecipeContentPayload create(Collection<RecipeType<?>> recipeTypes, RecipeMap recipes,
            @Nullable RegistryAccess registryAccess) {
        var recipeTypeSet = Set.copyOf(recipeTypes);
        // Fast-path for empty recipe type set (if no mod wants to sync anything)
        if (recipeTypeSet.isEmpty()) {
            return new RecipeContentPayload(recipeTypeSet, List.of());
        } else {
            var recipeSubset = recipes.values().stream().filter(h -> recipeTypeSet.contains(h.value().getType())).toList();
            if (registryAccess == null) {
                return new RecipeContentPayload(recipeTypeSet, recipeSubset);
            }

            var encodable = new ArrayList<RecipeHolder<?>>(recipeSubset.size());
            for (RecipeHolder<?> holder : recipeSubset) {
                ByteBuf bytes = Unpooled.buffer();
                try {
                    // A registry-aware buffer, because some serializers resolve registry entries while encoding.
                    var registryBuf = new RegistryFriendlyByteBuf(bytes, registryAccess);
                    @SuppressWarnings({ "rawtypes", "unchecked" })
                    var codec = (StreamCodec) RecipeHolder.STREAM_CODEC;
                    codec.encode(registryBuf, holder);
                    encodable.add(holder);
                } catch (Throwable t) {
                    LOGGER.warn("Not sending recipe {} to the client: its serializer cannot encode it ({})",
                            holder.id().identifier(), t.toString());
                } finally {
                    bytes.release();
                }
            }
            return new RecipeContentPayload(recipeTypeSet, List.copyOf(encodable));
        }
    }

    @Override
    public Type<RecipeContentPayload> type() {
        return TYPE;
    }
}
