package me.kall.blocktracker.api;

import net.minecraft.world.level.block.Block;

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
}