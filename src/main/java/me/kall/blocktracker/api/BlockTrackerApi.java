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

public final class BlockTrackerApi {
    static void trackBlock(Block block) {
        BlockTracker.TRACKED_BLOCKS.put(BlockTracker.getId(block), true);
    }

    static void trackBlock(ResourceLocation block) {
        BlockTracker.TRACKED_BLOCKS.put(block, true);
    }

    static void trackBlock(Block block, boolean acceptWorldGen) {
        BlockTracker.TRACKED_BLOCKS.put(BlockTracker.getId(block), acceptWorldGen);
    }

    static void trackBlock(ResourceLocation block, boolean acceptWorldGen) {
        BlockTracker.TRACKED_BLOCKS.put(block, acceptWorldGen);
    }

    static LongSet getTrackedBlocks(ServerLevel level, ChunkPos chunkPos, Block block) {
        return getTrackedBlocks(level, chunkPos, BlockTracker.getId(block));
    }

    static LongSet getTrackedBlocks(ServerLevel level, @NotNull ChunkPos chunkPos, @Nullable ResourceLocation blockId) {
        return getTrackedBlocks(level, chunkPos.toLong(), blockId);
    }

    static LongSet getTrackedBlocks(ServerLevel level, long chunkPos, @Nullable ResourceLocation blockId) {
        if (blockId == null) return LongSets.EMPTY_SET;
        if (!BlockTracker.TRACKED_BLOCKS.containsKey(blockId)) return LongSets.EMPTY_SET;
        Long2ObjectMap<Object2ObjectMap<ResourceLocation, LongSet>> chunkMap = BlockTracker.get(level).blockStorage.get(level.dimension().location());
        if (chunkMap == null) return LongSets.EMPTY_SET;
        Object2ObjectMap<ResourceLocation, LongSet> blockMap = chunkMap.get(chunkPos);
        if (blockMap == null) return LongSets.EMPTY_SET;
        LongSet blocks = blockMap.get(blockId);
        return blocks == null ? LongSets.EMPTY_SET : LongSets.unmodifiable(blocks);
    }

    static @NotNull ObjectList<LongSet> getTrackedBlocks(ServerLevel level, @NotNull ChunkPos center, int aroundRadius, @Nullable ResourceLocation blockId) {
        ObjectList<LongSet> tracked = new ObjectArrayList<>();

        int minX = center.x - aroundRadius;
        int maxX = center.x + aroundRadius;
        int minZ = center.z - aroundRadius;
        int maxZ = center.z + aroundRadius;

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
        LongSet tracked = new LongOpenHashSet();
        for (LongSet blocks : getTrackedBlocks(level, center, aroundRadius, blockId)) {
            if (blocks.isEmpty()) continue;
            tracked.addAll(blocks);
        }
        return tracked;
    }
}
