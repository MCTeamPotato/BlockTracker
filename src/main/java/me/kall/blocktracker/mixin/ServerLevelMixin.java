package me.kall.blocktracker.mixin;

import me.kall.blocktracker.event.BlockChangeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Shadow @Nonnull public abstract MinecraftServer getServer();

    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void onChange(BlockPos pos, BlockState blockState, BlockState newState, CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new BlockChangeEvent(pos, (ServerLevel) (Object) this, blockState, newState, this.getServer().isSameThread()));
    }
}
