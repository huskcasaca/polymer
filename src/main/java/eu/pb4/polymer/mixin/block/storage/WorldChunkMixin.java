package eu.pb4.polymer.mixin.block.storage;

import com.google.common.collect.ForwardingIterator;
import com.google.common.collect.Iterators;
import eu.pb4.polymer.api.block.PolymerBlock;
import eu.pb4.polymer.api.block.PolymerBlockUtils;
import eu.pb4.polymer.impl.interfaces.PolymerBlockPosStorage;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.TickScheduler;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeArray;
import net.minecraft.world.chunk.*;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin implements PolymerBlockPosStorage, Chunk {
    @Shadow
    public abstract ChunkPos getPos();

    @Shadow
    public abstract ChunkSection[] getSectionArray();

    @Shadow @Final private ChunkPos pos;

    @Inject(
            method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/biome/source/BiomeArray;Lnet/minecraft/world/chunk/UpgradeData;Lnet/minecraft/world/TickScheduler;Lnet/minecraft/world/TickScheduler;J[Lnet/minecraft/world/chunk/ChunkSection;Ljava/util/function/Consumer;)V",
            at = @At("TAIL")
    )
    private void polymer_polymerBlocksInit1(World world, ChunkPos pos, BiomeArray biomes, UpgradeData upgradeData, TickScheduler<Block> blockTickScheduler, TickScheduler<Fluid> fluidTickScheduler, long inhabitedTime, @Nullable ChunkSection[] sections, @Nullable Consumer<WorldChunk> loadToWorldConsumer, CallbackInfo info) {
        this.polymer_generatePolymerBlockSet();
    }

    @Inject(
            method = "<init>(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/ProtoChunk;Ljava/util/function/Consumer;)V",
            at = @At("TAIL")
    )
    private void polymer_polymerBlocksInit2(ServerWorld serverWorld, ProtoChunk protoChunk, Consumer<WorldChunk> consumer, CallbackInfo ci) {
        this.polymer_generatePolymerBlockSet();
    }

    @Inject(
            method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/biome/source/BiomeArray;)V",
            at = @At("TAIL")
    )
    private void polymer_polymerBlocksInit3(World world, ChunkPos pos, BiomeArray biomes, CallbackInfo ci) {
        this.polymer_generatePolymerBlockSet();
    }


    @Unique
    private void polymer_generatePolymerBlockSet() {
        for (var section : this.getSectionArray()) {
            if (section != null && !section.isEmpty()) {
                var container = section.getContainer();
                if (container.hasAny(PolymerBlockUtils.IS_POLYMER_BLOCK_STATE_PREDICATE)) {
                    var storage = (PolymerBlockPosStorage) section;
                    BlockState state;
                    for (byte x = 0; x < 16; x++) {
                        for (byte z = 0; z < 16; z++) {
                            for (byte y = 0; y < 16; y++) {
                                state = container.get(x, y, z);
                                if (state.getBlock() instanceof PolymerBlock) {
                                    storage.polymer_setPolymer(x, y, z);
                                }
                            }
                        }
                    }
                }
            }
        }

    }


    @Inject(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/ChunkSection;setBlockState(IIILnet/minecraft/block/BlockState;)Lnet/minecraft/block/BlockState;", shift = At.Shift.AFTER))
    private void polymer_addToList(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        if (state.getBlock() instanceof PolymerBlock) {
            this.polymer_setPolymer(pos.getX(), pos.getY(), pos.getZ());
        } else {
            this.polymer_removePolymer(pos.getX(), pos.getY(), pos.getZ());
        }
    }

    @Override
    public @Nullable Iterator<BlockPos.Mutable> polymer_iterator() {
        return new ForwardingIterator<>() {
            int current;
            Iterator<BlockPos.Mutable> currentIterator = Collections.emptyIterator();

            @Override
            protected Iterator<BlockPos.Mutable> delegate() {
                if (this.currentIterator == null || !this.currentIterator.hasNext()) {
                    var array = WorldChunkMixin.this.getSectionArray();
                    while (this.current < array.length) {
                        var s = array[this.current++];
                        var si = (PolymerBlockPosStorage) s;
                        if (s != null && si.polymer_hasAny()) {
                            this.currentIterator = si.polymer_iterator(ChunkSectionPos.from(WorldChunkMixin.this.getPos(), s.getYOffset() >> 4));
                            break;
                        }
                    }
                }

                return this.currentIterator;
            }
        };
    }

    @Override
    public void polymer_setPolymer(int x, int y, int z) {
        this.polymer_getSectionStorage(y).polymer_setPolymer(x, y, z);
    }

    @Override
    public void polymer_removePolymer(int x, int y, int z) {
        this.polymer_getSectionStorage(y).polymer_removePolymer(x, y, z);
    }

    @Override
    public boolean polymer_getPolymer(int x, int y, int z) {
        return this.polymer_getSectionStorage(y).polymer_getPolymer(x, y, z);
    }

    @Override
    public boolean polymer_hasAny() {
        for (var s : this.getSectionArray()) {
            if (s != null && ((PolymerBlockPosStorage) s).polymer_hasAny()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable ShortSet polymer_getBackendSet() {
        return null;
    }

    @Override
    public @Nullable Iterator<BlockPos.Mutable> polymer_iterator(ChunkSectionPos sectionPos) {
        return null;
    }

    private PolymerBlockPosStorage polymer_getSectionStorage(int y) {
        return (PolymerBlockPosStorage) this.getSection(this.getSectionIndex(y));
    }
}