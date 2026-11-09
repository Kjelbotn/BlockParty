package no.bangerfabrikken.blockparty;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import no.bangerfabrikken.blockparty.arena.ArenaManager;
import no.bangerfabrikken.blockparty.arena.PlayerStatsManager;
import no.bangerfabrikken.blockparty.arena.MusicTrackManager;
import no.bangerfabrikken.blockparty.command.BlockPartyCommands;
import no.bangerfabrikken.blockparty.sound.ModSounds;
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
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(this::onRightClickItem);
        ModSounds.SOUND_EVENTS.register(modEventBus);

        LOGGER.info("BlockParty {} initialised", "0.4.0-alpha");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        BlockPartyCommands.register(event.getDispatcher());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        ArenaManager.get().tick(event.getServer());
    }

    private void onServerStarted(ServerStartedEvent event) {
        PlayerStatsManager.load(event.getServer());
        ArenaManager.get().load(event.getServer());
        MusicTrackManager.load(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        ArenaManager.get().save(event.getServer());
        ArenaManager.get().shutdown(event.getServer());
        PlayerStatsManager.save(event.getServer());
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ArenaManager.get().handleBeaconInteraction(player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!no.bangerfabrikken.blockparty.arena.Arena.isMusicVotingBook(event.getItemStack())) return;
        no.bangerfabrikken.blockparty.arena.Arena arena = ArenaManager.get().findArena(player.getUUID());
        if (arena != null) {
            arena.openMusicVoting(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        } else {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Bli med i en BlockParty-arena før du stemmer på musikk.").withStyle(net.minecraft.ChatFormatting.YELLOW));
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ArenaManager.get().handleLogout(player);
        }
    }
}
