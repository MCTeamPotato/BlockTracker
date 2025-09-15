package me.kall.blocktracker.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import org.jetbrains.annotations.NotNull;

public class BlockChangeEvent extends BlockEvent {
    private final BlockState oldState;
    private final ChangeStatus changeStatus;
    private final boolean isSameThread;

    public BlockChangeEvent(BlockPos pos, ServerLevel level, @NotNull BlockState oldState, BlockState newState, boolean isSameThread) {
        super(level, pos, newState);
        this.oldState = oldState;
        this.isSameThread = isSameThread;
        this.changeStatus = oldState.isAir() ? ChangeStatus.ADD : (newState.isAir() ? ChangeStatus.REMOVE : ChangeStatus.REPLACE);

    }

    public ChangeStatus getChangeStatus() {
        return changeStatus;
    }

    public BlockState getNewState() {
        return this.getState();
    }

    public BlockState getOldState() {
        return oldState;
    }

    public boolean isSameThread() {
        return isSameThread;
    }

    @Override
    public ServerLevel getLevel() {
        return (ServerLevel) super.getLevel();
    }

    public enum ChangeStatus {
        REMOVE, ADD, REPLACE
    }
}
