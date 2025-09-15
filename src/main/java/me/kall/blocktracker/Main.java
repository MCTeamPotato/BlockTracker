package me.kall.blocktracker;

import me.kall.blocktracker.data.BlockTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Main.MOD_ID)
public final class Main {
    public static final String MOD_ID = "blocktracker";
    public static final String MOD_NAME = "BlockTracker";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    public Main(IEventBus bus, Dist dist, ModContainer container) {
        BlockTracker.register(NeoForge.EVENT_BUS);
    }
}
