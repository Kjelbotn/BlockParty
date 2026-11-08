package no.bangerfabrikken.blockparty;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import no.bangerfabrikken.blockparty.arena.ArenaManager;
import no.bangerfabrikken.blockparty.command.BlockPartyCommands;
import org.slf4j.Logger;

@Mod(BlockPartyMod.MOD_ID)
public final class BlockPartyMod {
    public static final String MOD_ID = "blockparty";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlockPartyMod(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);

        LOGGER.info("BlockParty {} initialised", "0.1.0-alpha");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        BlockPartyCommands.register(event.getDispatcher());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        ArenaManager.get().tick(event.getServer());
    }

    private void onServerStarted(ServerStartedEvent event) {
        ArenaManager.get().load(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        ArenaManager.get().save(event.getServer());
        ArenaManager.get().shutdown(event.getServer());
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ArenaManager.get().handleLogout(player);
        }
    }
}
