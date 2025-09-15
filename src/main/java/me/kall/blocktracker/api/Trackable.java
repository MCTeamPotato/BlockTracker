package me.kall.blocktracker.api;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import me.kall.blocktracker.data.BlockTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public interface Trackable {
    boolean trackable$isTracked();
    void trackable$setTracked(boolean tracked);

    boolean worldGen$accepted();
    void worldGen$setAccepted(boolean accepted);

    static boolean isTracked(Block block) {
        return ((Trackable)block).trackable$isTracked();
    }

    static boolean acceptWorldGen(Block block) {
        return ((Trackable)block).worldGen$accepted();
    }

    static void trackBlock(Block block) {
        BlockTracker.TRACKED_BLOCKS.put(ForgeRegistries.BLOCKS.getKey(block), true);
    }

    static void trackBlock(ResourceLocation block) {
        BlockTracker.TRACKED_BLOCKS.put(block, true);
    }

    static void trackBlock(Block block, boolean acceptWorldGen) {
        BlockTracker.TRACKED_BLOCKS.put(ForgeRegistries.BLOCKS.getKey(block), acceptWorldGen);
    }

    static void trackBlock(ResourceLocation block, boolean acceptWorldGen) {
        BlockTracker.TRACKED_BLOCKS.put(block, acceptWorldGen);
    }

    static LongSet getTrackedBlocks(ServerLevel level, ChunkPos chunkPos, Block block) {
        return getTrackedBlocks(level, chunkPos, ForgeRegistries.BLOCKS.getKey(block));
    }

    static LongSet getTrackedBlocks(ServerLevel level, ChunkPos chunkPos, @Nullable ResourceLocation blockId) {
        if (blockId == null) return LongSets.emptySet();
        if (!BlockTracker.TRACKED_BLOCKS.containsKey(blockId)) return LongSets.emptySet();
        Long2ObjectMap<Object2ObjectMap<ResourceLocation, LongSet>> chunkMap = BlockTracker.get(level).blockStorage.get(level.dimension().location());
        if (chunkMap == null) return LongSets.emptySet();
        Object2ObjectMap<ResourceLocation, LongSet> blockMap = chunkMap.get(chunkPos.toLong());
        if (blockMap == null) return LongSets.emptySet();
        LongSet blocks = blockMap.get(blockId);
        return blocks == null ? LongSets.emptySet() : LongSets.unmodifiable(blocks);
    }
}