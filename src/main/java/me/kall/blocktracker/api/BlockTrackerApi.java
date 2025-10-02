package me.kall.blocktracker.api;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import me.kall.blocktracker.data.BlockTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public interface BlockTrackerApi {
    static void trackBlock(Block block, @Nullable Predicate<Block> additionalRule) {
        trackBlock(BlockTracker.getId(block), additionalRule);
    }

    static void trackBlock(ResourceLocation block, @Nullable Predicate<Block> additionalRule) {
        trackBlock(block, true, additionalRule);
    }

    static void trackBlock(Block block, boolean acceptWorldGen, @Nullable Predicate<Block> additionalRule) {
        trackBlock(BlockTracker.getId(block), acceptWorldGen, additionalRule);
    }

    static void trackBlock(ResourceLocation block, boolean acceptWorldGen, @Nullable Predicate<Block> additionalRule) {
        BlockTracker.TRACKED_BLOCKS.put(block, acceptWorldGen);
        BlockTracker.addRule(block, additionalRule);
    }

    static LongSet getTrackedBlocks(ServerLevel level, ChunkPos chunkPos, Block block) {
        return getTrackedBlocks(level, chunkPos, BlockTracker.getId(block));
    }

    static LongSet getTrackedBlocks(ServerLevel level, @NotNull ChunkPos chunkPos, @Nullable ResourceLocation blockId) {
        return getTrackedBlocks(level, chunkPos.toLong(), blockId);
    }

    static LongSet getTrackedBlocks(ServerLevel level, long chunkPos, Block block) {
        return getTrackedBlocks(level, chunkPos, BlockTracker.getId(block));
    }

    static LongSet getTrackedBlocks(ServerLevel level, long chunkPos, @Nullable ResourceLocation blockId) {
        if (blockId == null) return LongSets.emptySet();
        if (!BlockTracker.TRACKED_BLOCKS.containsKey(blockId)) return LongSets.emptySet();
        if (!level.getServer().isSameThread()) throw new UnsupportedOperationException("BlockTracker should be called on server thread");
        Long2ObjectMap<Object2ObjectMap<ResourceLocation, LongSet>> chunkMap = BlockTracker.get(level).blockStorage.get(level.dimension().location());
        if (chunkMap == null || chunkMap.isEmpty()) return LongSets.emptySet();
        Object2ObjectMap<ResourceLocation, LongSet> blockMap = chunkMap.get(chunkPos);
        if (blockMap == null || blockMap.isEmpty()) return LongSets.emptySet();
        LongSet blocks = blockMap.get(blockId);
        return blocks == null  || blocks.isEmpty() ? LongSets.emptySet() : LongSets.unmodifiable(blocks);
    }

    static @NotNull ObjectList<LongSet> getTrackedBlocks(ServerLevel level, ChunkPos center, int aroundRadius, Block block) {
        return getTrackedBlocks(level, center, aroundRadius, BlockTracker.getId(block));
    }

    static @NotNull ObjectList<LongSet> getTrackedBlocks(ServerLevel level, long center, int aroundRadius, Block block) {
        return getTrackedBlocks(level, center, aroundRadius, BlockTracker.getId(block));
    }

    static @NotNull ObjectList<LongSet> getTrackedBlocks(ServerLevel level, @NotNull ChunkPos center, int aroundRadius, @Nullable ResourceLocation blockId) {
        return getTrackedBlocks(level, center.toLong(), aroundRadius, blockId);
    }

    static @NotNull ObjectList<LongSet> getTrackedBlocks(ServerLevel level, long center, int aroundRadius, @Nullable ResourceLocation blockId) {
        ObjectList<LongSet> tracked = new ObjectArrayList<>();

        int centerX = ChunkPos.getX(center);
        int centerZ = ChunkPos.getZ(center);

        int minX = centerX - aroundRadius;
        int maxX = centerX + aroundRadius;
        int minZ = centerZ - aroundRadius;
        int maxZ = centerZ + aroundRadius;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                LongSet blocks = getTrackedBlocks(level, ChunkPos.asLong(x, z), blockId);
                if (blocks.isEmpty()) continue;
                tracked.add(blocks);
            }
        }

        return tracked;
    }

    static @NotNull LongSet getTrackedBlocksFlat(ServerLevel level, @NotNull ChunkPos center, int aroundRadius, @Nullable ResourceLocation blockId) {
        return getTrackedBlocksFlat(level, center.toLong(), aroundRadius, blockId);
    }

    static @NotNull LongSet getTrackedBlocksFlat(ServerLevel level, long center, int aroundRadius, Block block) {
        return getTrackedBlocksFlat(level, center, aroundRadius, BlockTracker.getId(block));
    }

    static @NotNull LongSet getTrackedBlocksFlat(ServerLevel level, @NotNull ChunkPos center, int aroundRadius, Block block) {
        return getTrackedBlocksFlat(level, center.toLong(), aroundRadius, BlockTracker.getId(block));
    }

    static @NotNull LongSet getTrackedBlocksFlat(ServerLevel level, long center, int aroundRadius, @Nullable ResourceLocation blockId) {
        LongSet tracked = new LongOpenHashSet();
        for (LongSet blocks : getTrackedBlocks(level, center, aroundRadius, blockId)) {
            if (blocks.isEmpty()) continue;
            tracked.addAll(blocks);
        }
        return tracked;
    }
}
