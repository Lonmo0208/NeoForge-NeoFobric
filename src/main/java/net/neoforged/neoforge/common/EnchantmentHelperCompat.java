/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Holds the ItemStack-shaped {@code getItemEnchantmentLevel} overload that 26.1.2-era loot mixins
 * anchor on, outside {@link EnchantmentHelper} itself.
 *
 * <p>The snapshot's own method takes an {@code ItemInstance}, and mods that inject into the bare
 * {@code "getItemEnchantmentLevel"} name with an {@code ItemInstance} handler (ae2's
 * {@code EnchantmentHelperMixin}) resolve that target by name only. If the ItemStack overload were
 * declared in the same class, Mixin would find two candidates, inject into both, and fail descriptor
 * validation on the one whose shape the handler does not match - the client then dies while the
 * server loads a world. Mixin only searches the target class's own methods, so keeping this overload
 * in the superclass leaves the bare-name target unambiguous.
 *
 * <p>Calls still work: {@link EnchantmentHelper} extends this class, javac emits the qualifying type
 * ({@code EnchantmentHelper}) as the invocation owner, and the JVM resolves a static call through the
 * superclass. That is what lets Curios' {@code @At(INVOKE_ASSIGN)} anchor on
 * {@code EnchantmentHelper.getItemEnchantmentLevel(Holder, ItemStack)} keep matching.
 */
public class EnchantmentHelperCompat {
    protected EnchantmentHelperCompat() {}

    @Deprecated
    public static int getItemEnchantmentLevel(Holder<Enchantment> enchantment, ItemStack piece) {
        return EnchantmentHelper.getItemEnchantmentLevelForInstance(enchantment, piece);
    }
}
