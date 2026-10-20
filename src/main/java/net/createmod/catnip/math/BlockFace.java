// Catnip/Ponder utility; adapted from Create Fly 26.2 (9df7016). See licenses/Ponder-LICENSE and licenses/Create-Fly-CC0.
package net.createmod.catnip.math;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.createmod.catnip.data.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class BlockFace extends Pair<BlockPos, Direction> {
    public static Codec<BlockFace> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BlockPos.CODEC.fieldOf(
            "pos").forGetter(BlockFace::getPos),
        Direction.CODEC.fieldOf("direction").forGetter(BlockFace::getFace)
    ).apply(instance, BlockFace::new));

    public static StreamCodec<FriendlyByteBuf, BlockFace> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        BlockFace::getPos,
        Direction.STREAM_CODEC,
        BlockFace::getFace,
        BlockFace::new
    );

    public BlockFace(BlockPos first, Direction second) {
        super(first, second);
    }

    public boolean isEquivalent(BlockFace other) {
        if (equals(other)) {
            return true;
        }
        return getConnectedPos().equals(other.getPos()) && getPos().equals(other.getConnectedPos());
    }

    public BlockPos getPos() {
        return getFirst();
    }

    public Direction getFace() {
        return getSecond();
    }

    public Direction getOppositeFace() {
        return getSecond().getOpposite();
    }

    public BlockFace getOpposite() {
        return new BlockFace(getConnectedPos(), getOppositeFace());
    }

    public BlockPos getConnectedPos() {
        return getPos().relative(getFace());
    }

}
