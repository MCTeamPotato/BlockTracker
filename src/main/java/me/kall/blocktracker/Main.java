package me.kall.blocktracker;

import me.kall.blocktracker.data.BlockTracker;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Main.MOD_ID)
public final class Main {
    public static final String MOD_ID = "blocktracker";
    public static final String MOD_NAME = "BlockTracker";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    public Main() {
        BlockTracker.register(MinecraftForge.EVENT_BUS);
    }
}
