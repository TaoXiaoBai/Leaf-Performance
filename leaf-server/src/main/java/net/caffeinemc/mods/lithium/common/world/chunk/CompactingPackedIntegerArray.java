// Leaf - Lithium - optimize chunk palette serialization
//
// Based on Lithium PR #709, commit d258ae5c3d61a052017934b33de7db8be2153d4e.
// Original author: ishland; compaction implementation by JellySquid.
// Original project: https://github.com/CaffeineMC/lithium
// Licensed under LGPL-3.0-only.
package net.caffeinemc.mods.lithium.common.world.chunk;

import net.minecraft.world.level.chunk.Palette;

public interface CompactingPackedIntegerArray {
    /**
     * Copies this packed array into {@code output}, remapping palette indices into {@code destinationPalette}.
     */
    <T> void lithium$compact(Palette<T> sourcePalette, Palette<T> destinationPalette, short[] output);
}