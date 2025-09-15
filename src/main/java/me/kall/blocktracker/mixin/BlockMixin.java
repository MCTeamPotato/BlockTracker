package me.kall.blocktracker.mixin;

import me.kall.blocktracker.api.Trackable;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Block.class)
public abstract class BlockMixin implements Trackable {
    @Unique
    private boolean block$isTracked, worldGen$accepted;

    @Override
    public boolean trackable$isTracked() {
        return this.block$isTracked;
    }

    @Override
    public void trackable$setTracked(boolean tracked) {
        this.block$isTracked = tracked;
    }

    @Override
    public boolean worldGen$accepted() {
        return this.worldGen$accepted;
    }

    @Override
    public void worldGen$setAccepted(boolean accepted) {
        this.worldGen$accepted = accepted;
    }
}
