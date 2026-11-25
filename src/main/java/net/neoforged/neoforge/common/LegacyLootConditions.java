/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.KeyCompressor;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapLike;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.advancements.predicates.DataComponentMatchers;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.loot.predicates.MatchBlock;

/// Loot-condition aliases for data written against the pre-26.3 shape.
///
/// 26.3 renamed `minecraft:block_state_property` to `minecraft:match_block` and changed the field
/// names (`block` / `properties` became `blocks` / `state`). A mod whose data was ported from 26.2
/// still carries the old name, and because the whole loot-table registry is parsed as one unit, a
/// single such file fails the datapack load and the world refuses to open. Keep the old name as an
/// alias that reads the old shape and produces the same predicate.
public final class LegacyLootConditions {
    private LegacyLootConditions() {}

    /// `minecraft:block_state_property` with the pre-26.3 `block` + `properties` fields.
    ///
    /// The encoder is the regular `match_block` encoder (the legacy name is only read from old
    /// datapacks); only the decoder has to understand the old field names.
    public static final MapCodec<MatchBlock> BLOCK_STATE_PROPERTY = MapCodec.of(
            MatchBlock.MAP_CODEC,
            new MapDecoder<MatchBlock>() {
                @Override
                public <T> DataResult<MatchBlock> decode(DynamicOps<T> ops, MapLike<T> input) {
                    var block = BuiltInRegistries.BLOCK.holderByNameCodec().parse(ops, input.get("block"));
                    var properties = input.get("properties") == null
                            ? DataResult.success(Optional.<StatePropertiesPredicate>empty())
                            : StatePropertiesPredicate.CODEC.parse(ops, input.get("properties")).map(Optional::of);
                    return block.flatMap(holder -> properties.map(state -> new MatchBlock(new BlockPredicate(
                            Optional.of(HolderSet.direct(holder)),
                            state,
                            Optional.empty(),
                            DataComponentMatchers.ANY))));
                }

                @Override
                public <T> KeyCompressor<T> compressor(DynamicOps<T> ops) {
                    return new KeyCompressor<>(ops,
                            Stream.of(ops.createString("block"), ops.createString("properties")));
                }

                @Override
                public <T> Stream<T> keys(DynamicOps<T> ops) {
                    return Stream.of(ops.createString("block"), ops.createString("properties"));
                }
            });
}
