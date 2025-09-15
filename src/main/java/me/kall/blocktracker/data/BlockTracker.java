package me.kall.blocktracker.data;

import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.kall.blocktracker.Main;
import me.kall.blocktracker.api.Trackable;
import me.kall.blocktracker.event.BlockChangeEvent;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class BlockTracker extends SavedData {
    public static final String DATA_NAME = "TrackedBlockData";

    public static final Object2BooleanMap<ResourceLocation> TRACKED_BLOCKS = new Object2BooleanOpenHashMap<>();

    public final Object2ObjectMap<ResourceLocation, Long2ObjectMap<Object2ObjectMap<ResourceLocation, LongSet>>> blockStorage = new Object2ObjectOpenHashMap<>();

    public static void register(IEventBus bus) {
        bus.addListener(BlockTracker::initTracker);
        bus.addListener(BlockTracker::cleanData);
        bus.addListener(BlockTracker::blockChange);
    }

    private static void initTracker(@NotNull ServerStartingEvent event) {
        event.getServer().execute(() -> {
            for (Object2BooleanMap.Entry<ResourceLocation> entry : BlockTracker.TRACKED_BLOCKS.object2BooleanEntrySet()) {
                ResourceLocation blockId = entry.getKey();
                boolean worldGen = entry.getBooleanValue();
                Block block = getBlock(blockId);
                if (block instanceof Trackable trackable) {
                    trackable.trackable$setTracked(true);
                    trackable.worldGen$setAccepted(worldGen);
                    Main.LOGGER.debug("Successfully registered tracked block: {}", blockId);
                } else {
                    Main.LOGGER.warn("Failed to register tracked block: {} - Block not found", blockId);
                }
            }
        });
    }

    private static void cleanData(ServerStartedEvent event) {
        event.getServer().execute(() -> {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                BlockTracker blockTracker = BlockTracker.get(level);
                List<LongObjectPair<ResourceLocation>> toRemove = new ArrayList<>();
                ResourceLocation dim = level.dimension().location();
                Long2ObjectMap<Object2ObjectMap<ResourceLocation, LongSet>> dimMap = blockTracker.blockStorage.get(dim);
                if (dimMap == null) continue;
                dimMap.forEach((chunkKey, blockMap) -> {
                    for (ResourceLocation id : blockMap.keySet()) {
                        if (!Trackable.isTracked(getBlock(id))) {
                            toRemove.add(new LongObjectImmutablePair<>(chunkKey, id));
                        }
                    }
                });
                for (LongObjectPair<ResourceLocation> entry : toRemove) {
                    long chunkKey = entry.firstLong();
                    ResourceLocation block = entry.second();
                    try {
                        dimMap.get(chunkKey).remove(block);
                    } catch (Throwable e) {
                        Main.LOGGER.error("Failed to remove out-dated tracked block {} from chunk {}", block, new ChunkPos(chunkKey).toString());
                        Main.LOGGER.error("", e);
                    }
                }
            }
        });
    }

    private static void blockChange(BlockChangeEvent event) {
        BlockPos pos = event.getPos();
        BlockState oldState = event.getOldState();
        BlockState newState = event.getNewState();
        ServerLevel level = event.getLevel();
        Block oldBlock = oldState.getBlock();
        Block newBlock = newState.getBlock();

        boolean isWorldGen = !level.getServer().isSameThread();

        if (Trackable.isTracked(oldBlock)) {
            boolean acceptWorldGen = Trackable.acceptWorldGen(oldBlock);
            if (acceptWorldGen || !isWorldGen) {
                level.getServer().execute(() -> BlockTracker.get(level).removeBlock(level, pos, getId(oldBlock)));
            }
        }

        if (Trackable.isTracked(newBlock)) {
            boolean acceptWorldGen = Trackable.acceptWorldGen(newBlock);
            if (acceptWorldGen || !isWorldGen) {
                level.getServer().execute(() -> BlockTracker.get(level).addBlock(level, pos, getId(newBlock)));
            }
        }
    }

    public static BlockTracker load(CompoundTag nbt) {
        BlockTracker data = new BlockTracker();
        Main.LOGGER.info("Loading tracked block data...");

        for (String dimKey : nbt.getAllKeys()) {
            ResourceLocation dimID = ResourceLocation.tryParse(dimKey);
            if (dimID == null) {
                Main.LOGGER.warn("Skipping invalid dimension key in NBT: {}", dimKey);
                continue;
            }

            CompoundTag dimTag = nbt.getCompound(dimKey);
            Long2ObjectOpenHashMap<Object2ObjectMap<ResourceLocation, LongSet>> chunkMap = new Long2ObjectOpenHashMap<>();

            for (String chunkKeyStr : dimTag.getAllKeys()) {
                long chunkKey;
                try {
                    chunkKey = Long.parseLong(chunkKeyStr);
                } catch (NumberFormatException e) {
                    Main.LOGGER.warn("Skipping invalid chunk key in dimension {}: {}", dimID, chunkKeyStr);
                    Main.LOGGER.warn("", e);
                    continue;
                }

                CompoundTag chunkTag = dimTag.getCompound(chunkKeyStr);
                Object2ObjectOpenHashMap<ResourceLocation, LongSet> blockMap = new Object2ObjectOpenHashMap<>();

                for (String blockKey : chunkTag.getAllKeys()) {
                    ResourceLocation blockId = ResourceLocation.tryParse(blockKey);
                    if (blockId == null) {
                        Main.LOGGER.warn("Skipping invalid block key in chunk {}: {}", chunkKeyStr, blockKey);
                        continue;
                    }

                    ListTag posList = chunkTag.getList(blockKey, Tag.TAG_LONG);
                    LongSet posSet = new LongOpenHashSet();
                    for (Tag tag : posList) {
                        if (tag instanceof LongTag longTag) {
                            posSet.add(longTag.getAsLong());
                        } else {
                            Main.LOGGER.warn("Skipping invalid position tag in block {}: expected LONG, got {}", blockId, tag.getId());
                        }
                    }
                    blockMap.put(blockId, posSet);
                }

                chunkMap.put(chunkKey, blockMap);
            }

            data.blockStorage.put(dimID, chunkMap);
            Main.LOGGER.debug("Loaded {} chunks for dimension {}", chunkMap.size(), dimID);
        }

        Main.LOGGER.info("TrackedBlockData loaded successfully with {} dimensions", data.blockStorage.size());
        return data;
    }

    public static @Nullable ResourceLocation getId(Block block) {
        return ForgeRegistries.BLOCKS.getKey(block);
    }

    public static @Nullable Block getBlock(ResourceLocation id) {
        return ForgeRegistries.BLOCKS.getValue(id);
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        Main.LOGGER.info("Saving tracked block data...");
        int totalDimensions = 0;
        int totalChunks = 0;
        int totalBlocks = 0;

        for (Object2ObjectMap.Entry<ResourceLocation, Long2ObjectMap<Object2ObjectMap<ResourceLocation, LongSet>>> dimEntry : blockStorage.object2ObjectEntrySet()) {
            CompoundTag dimTag = new CompoundTag();
            int dimensionChunks = 0;
            int dimensionBlocks = 0;

            for (Long2ObjectMap.Entry<Object2ObjectMap<ResourceLocation, LongSet>> chunkEntry : dimEntry.getValue().long2ObjectEntrySet()) {
                CompoundTag chunkTag = new CompoundTag();
                int chunkBlocks = 0;

                for (Object2ObjectMap.Entry<ResourceLocation, LongSet> blockEntry : chunkEntry.getValue().object2ObjectEntrySet()) {
                    ListTag posList = new ListTag();
                    for (long posLong : blockEntry.getValue()) {
                        posList.add(LongTag.valueOf(posLong));
                    }
                    chunkTag.put(blockEntry.getKey().toString(), posList);
                    chunkBlocks++;
                }

                dimTag.put(Long.toString(chunkEntry.getLongKey()), chunkTag);
                dimensionChunks++;
                dimensionBlocks += chunkBlocks;
            }

            nbt.put(dimEntry.getKey().toString(), dimTag);
            totalDimensions++;
            totalChunks += dimensionChunks;
            totalBlocks += dimensionBlocks;

            Main.LOGGER.debug("Saved dimension {}: {} chunks, {} blocks", dimEntry.getKey(), dimensionChunks, dimensionBlocks);
        }

        Main.LOGGER.info("TrackedBlockData saved: {} dimensions, {} chunks, {} blocks", totalDimensions, totalChunks, totalBlocks);
        return nbt;
    }

    public static BlockTracker get(ServerLevel level) {
        try {
            return level.getDataStorage().computeIfAbsent(BlockTracker::load, BlockTracker::new, DATA_NAME);
        } catch (Exception e) {
            Main.LOGGER.error("Failed to get BlockTracker for level {}", level.dimension().location());
            Main.LOGGER.error("", e);
            return new BlockTracker();
        }
    }

    public void addBlock(ServerLevel level, BlockPos pos, @Nullable ResourceLocation blockId) {
        try {
            if (blockId == null) return;

            ResourceLocation dim = level.dimension().location();
            long chunkKey = ChunkPos.asLong(pos);
            long posLong = pos.asLong();

            Long2ObjectMap<Object2ObjectMap<ResourceLocation, LongSet>> dimMap = blockStorage.get(dim);
            if (dimMap == null) {
                dimMap = new Long2ObjectOpenHashMap<>();
                blockStorage.put(dim, dimMap);
            }

            Object2ObjectMap<ResourceLocation, LongSet> blockMap = dimMap.get(chunkKey);
            if (blockMap == null) {
                blockMap = new Object2ObjectOpenHashMap<>();
                dimMap.put(chunkKey, blockMap);
            }

            LongSet posSet = blockMap.get(blockId);
            if (posSet == null) {
                posSet = new LongOpenHashSet();
                blockMap.put(blockId, posSet);
            }

            if (posSet.add(posLong)) {
                setDirty();
                Main.LOGGER.debug("Added block {} at {} in dimension {}", blockId, pos, dim);
            }
        } catch (Exception e) {
            Main.LOGGER.error("Failed to add block {} at {} in dimension {}", blockId, pos, level.dimension().location());
            Main.LOGGER.error("", e);
        }
    }

    public void removeBlock(ServerLevel level, BlockPos pos, @Nullable ResourceLocation blockId) {
        try {
            if (blockId == null) return;
            ResourceLocation dim = level.dimension().location();
            Long2ObjectMap<Object2ObjectMap<ResourceLocation, LongSet>> chunkMap = blockStorage.get(dim);
            if (chunkMap == null) {
                Main.LOGGER.debug("Attempted to remove block from non-existent dimension: {}", dim);
                return;
            }

            long chunkKey = ChunkPos.asLong(pos);
            Object2ObjectMap<ResourceLocation, LongSet> blockMap = chunkMap.get(chunkKey);
            if (blockMap == null) {
                Main.LOGGER.debug("Attempted to remove block from non-existent chunk: {} in dimension {}", chunkKey, dim);
                return;
            }

            LongSet posSet = blockMap.get(blockId);
            if (posSet == null) {
                Main.LOGGER.debug("Attempted to remove non-existent block: {} in chunk {} dimension {}", blockId, chunkKey, dim);
                return;
            }

            if (posSet.remove(pos.asLong())) {
                setDirty();
                Main.LOGGER.debug("Removed block {} at {} in dimension {}", blockId, pos, dim);

                if (posSet.isEmpty()) {
                    blockMap.remove(blockId);
                    if (blockMap.isEmpty()) {
                        chunkMap.remove(chunkKey);
                        if (chunkMap.isEmpty()) {
                            blockStorage.remove(dim);
                            Main.LOGGER.debug("Removed empty dimension: {}", dim);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Main.LOGGER.error("Failed to remove block {} at {} in dimension {}", blockId, pos, level.dimension().location());
            Main.LOGGER.error("", e);
        }
    }
}
