package me.kall.blocktracker.data;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.kall.blocktracker.api.Trackable;
import me.kall.blocktracker.event.BlockChangeEvent;
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
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@ApiStatus.Internal
@ParametersAreNonnullByDefault
public class BlockTracker extends SavedData {
    public static final String DATA_NAME = "TrackedBlockData";

    public static final Object2BooleanMap<ResourceLocation> TRACKED_BLOCKS = new Object2BooleanOpenHashMap<>();
    public static final Object2ObjectMap<ResourceLocation, Predicate<Block>> TRACKING_RULES = new Object2ObjectOpenHashMap<>();

    public final Object2ObjectMap<ResourceLocation, Long2ObjectMap<Object2ObjectMap<ResourceLocation, LongSet>>> blockStorage = new Object2ObjectOpenHashMap<>();

    public BlockTracker() {
        super(DATA_NAME);
    }

    public static void register(IEventBus bus) {
        bus.addListener(BlockTracker::initTracker);
        bus.addListener(BlockTracker::cleanData);
        bus.addListener(BlockTracker::blockChange);
    }

    private static void initTracker(@NotNull FMLServerStartingEvent event) {
        event.getServer().execute(() -> {
            for (Object2BooleanMap.Entry<ResourceLocation> entry : BlockTracker.TRACKED_BLOCKS.object2BooleanEntrySet()) {
                ResourceLocation blockId = entry.getKey();
                boolean worldGen = entry.getBooleanValue();
                Block block = getBlock(blockId);
                if (block instanceof Trackable) {
                    Trackable trackable = (Trackable) block;
                    trackable.trackable$setTracked(true);
                    trackable.worldGen$setAccepted(worldGen);
                }
            }
        });
    }

    private static void cleanData(FMLServerStartedEvent event) {
        event.getServer().execute(() -> {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                BlockTracker blockTracker = BlockTracker.get(level);
                List<Pair<Long, ResourceLocation>> toRemove = new ArrayList<>();
                ResourceLocation dim = level.dimension().location();
                Long2ObjectMap<Object2ObjectMap<ResourceLocation, LongSet>> dimMap = blockTracker.blockStorage.get(dim);
                if (dimMap == null) continue;
                dimMap.forEach((chunkKey, blockMap) -> {
                    for (ResourceLocation id : blockMap.keySet()) {
                        if (!Trackable.isTracked(getBlock(id))) {
                            toRemove.add(new Pair<>(chunkKey, id));
                        }
                    }
                });
                for (Pair<Long, ResourceLocation> entry : toRemove) {
                    long chunkKey = entry.getFirst();
                    ResourceLocation block = entry.getSecond();
                    dimMap.get(chunkKey).remove(block);
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

        ResourceLocation oldId = getId(oldBlock);
        ResourceLocation newId = getId(newBlock);

        boolean isWorldGen = !event.isSameThread();

        if (Trackable.isTracked(oldBlock) && oldId != null) {
            boolean acceptWorldGen = Trackable.acceptWorldGen(oldBlock);
            if (acceptWorldGen || !isWorldGen) {
                level.getServer().execute(() -> {
                    Predicate<Block> rule = TRACKING_RULES.get(oldId);
                    if (rule == null) {
                        BlockTracker.get(level).removeBlock(level, pos, oldId);
                    } else {
                        if (rule.test(oldBlock)) BlockTracker.get(level).removeBlock(level, pos, oldId);
                    }
                });
            }
        }

        if (Trackable.isTracked(newBlock) && newId != null) {
            boolean acceptWorldGen = Trackable.acceptWorldGen(newBlock);
            if (acceptWorldGen || !isWorldGen) {
                level.getServer().execute(() -> {
                    Predicate<Block> rule = TRACKING_RULES.get(newId);
                    if (rule == null) {
                        BlockTracker.get(level).addBlock(level, pos, newId);
                    } else {
                        if (rule.test(newBlock)) BlockTracker.get(level).addBlock(level, pos, newId);
                    }
                });
            }
        }
    }

    public void load(CompoundTag nbt) {
        BlockTracker data = new BlockTracker();

        for (String dimKey : nbt.getAllKeys()) {
            ResourceLocation dimID = ResourceLocation.tryParse(dimKey);
            if (dimID == null) continue;

            CompoundTag dimTag = nbt.getCompound(dimKey);
            Long2ObjectOpenHashMap<Object2ObjectMap<ResourceLocation, LongSet>> chunkMap = new Long2ObjectOpenHashMap<>();

            for (String chunkKeyStr : dimTag.getAllKeys()) {
                long chunkKey = Long.parseLong(chunkKeyStr);

                CompoundTag chunkTag = dimTag.getCompound(chunkKeyStr);
                Object2ObjectOpenHashMap<ResourceLocation, LongSet> blockMap = new Object2ObjectOpenHashMap<>();

                for (String blockKey : chunkTag.getAllKeys()) {
                    ResourceLocation blockId = ResourceLocation.tryParse(blockKey);
                    if (blockId == null) continue;
                    ListTag posList = chunkTag.getList(blockKey, Constants.NBT.TAG_LONG);
                    LongSet posSet = new LongOpenHashSet();
                    for (Tag tag : posList) {
                        if (tag instanceof LongTag longTag) posSet.add(longTag.getAsLong());
                    }
                    blockMap.put(blockId, posSet);
                }

                chunkMap.put(chunkKey, blockMap);
            }

            data.blockStorage.put(dimID, chunkMap);
        }
    }

    public static @Nullable ResourceLocation getId(Block block) {
        return ForgeRegistries.BLOCKS.getKey(block);
    }

    public static @Nullable Block getBlock(ResourceLocation id) {
        return ForgeRegistries.BLOCKS.getValue(id);
    }

    public static void addRule(ResourceLocation block, @Nullable Predicate<Block> additionalRule) {
        if (additionalRule == null) return;
        Predicate<Block> rule = TRACKING_RULES.get(block);
        TRACKING_RULES.put(block, rule == null ? additionalRule : rule.and(additionalRule));
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        for (Object2ObjectMap.Entry<ResourceLocation, Long2ObjectMap<Object2ObjectMap<ResourceLocation, LongSet>>> dimEntry : blockStorage.object2ObjectEntrySet()) {
            CompoundTag dimTag = new CompoundTag();

            for (Long2ObjectMap.Entry<Object2ObjectMap<ResourceLocation, LongSet>> chunkEntry : dimEntry.getValue().long2ObjectEntrySet()) {
                CompoundTag chunkTag = new CompoundTag();

                for (Object2ObjectMap.Entry<ResourceLocation, LongSet> blockEntry : chunkEntry.getValue().object2ObjectEntrySet()) {
                    ListTag posList = new ListTag();
                    for (long posLong : blockEntry.getValue()) {
                        posList.add(LongTag.valueOf(posLong));
                    }
                    chunkTag.put(blockEntry.getKey().toString(), posList);
                }

                dimTag.put(Long.toString(chunkEntry.getLongKey()), chunkTag);
            }

            nbt.put(dimEntry.getKey().toString(), dimTag);
        }

        return nbt;
    }

    public static BlockTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BlockTracker::new, DATA_NAME);
    }

    public void addBlock(ServerLevel level, BlockPos pos, @Nullable ResourceLocation blockId) {
        if (blockId == null) return;

            ResourceLocation dim = level.dimension().location();
            long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
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
        }
    }

    public void removeBlock(ServerLevel level, BlockPos pos, @Nullable ResourceLocation blockId) {
        if (blockId == null) return;
        ResourceLocation dim = level.dimension().location();
        Long2ObjectMap<Object2ObjectMap<ResourceLocation, LongSet>> chunkMap = blockStorage.get(dim);
        if (chunkMap == null) return;

        long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Object2ObjectMap<ResourceLocation, LongSet> blockMap = chunkMap.get(chunkKey);
        if (blockMap == null) return;

        LongSet posSet = blockMap.get(blockId);
        if (posSet == null) return;

        if (posSet.remove(pos.asLong())) {
            setDirty();
            if (posSet.isEmpty()) {
                blockMap.remove(blockId);
                if (blockMap.isEmpty()) {
                    chunkMap.remove(chunkKey);
                    if (chunkMap.isEmpty()) {
                        blockStorage.remove(dim);
                    }
                }
            }
        }
    }
}