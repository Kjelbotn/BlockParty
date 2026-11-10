package no.bangerfabrikken.blockparty;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import no.bangerfabrikken.blockparty.network.MusicControlPayload;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
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
        modEventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(this::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(this::onItemToss);
        ModSounds.SOUND_EVENTS.register(modEventBus);

        LOGGER.info("BlockParty {} initialised", "0.9.6-alpha");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        // This is a server-to-client payload. Its receiver is only invoked on the
        // physical client, where the client-only controller is present.
        event.registrar("1").playToClient(MusicControlPayload.TYPE, MusicControlPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        no.bangerfabrikken.blockparty.client.ClientMusicController.handle(payload)));
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
        ArenaManager.get().refreshLobbyDisplays();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        ArenaManager.get().save(event.getServer());
        ArenaManager.get().shutdown(event.getServer());
        PlayerStatsManager.save(event.getServer());
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack held = player.getItemInHand(event.getHand());
        if (handleLobbyItemInteraction(player, held)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (ArenaManager.get().handleBeaconInteraction(player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (handleLobbyItemInteraction(player, event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        no.bangerfabrikken.blockparty.arena.Arena arena = ArenaManager.get().findArena(player.getUUID());
        if (arena != null && arena.handlePowerupItemInteraction(player, event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private boolean handleLobbyItemInteraction(ServerPlayer player, ItemStack stack) {
        if (no.bangerfabrikken.blockparty.arena.Arena.isLeaveLobbyItem(stack)) {
            no.bangerfabrikken.blockparty.arena.Arena arena = ArenaManager.get().findArenaForLeave(player);
            if (arena != null && arena.containsPlayer(player.getUUID())) {
                arena.leave(player, true);
            } else {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Du er ikke registrert som deltaker i en BlockParty-arena.")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
            return true;
        }

        if (no.bangerfabrikken.blockparty.arena.Arena.isJoinGameItem(stack)) {
            no.bangerfabrikken.blockparty.arena.Arena arena = ArenaManager.get().findArena(player.getUUID());
            if (arena == null) arena = ArenaManager.get().findLobbyArenaFor(player);
            if (arena == null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Gå til BlockParty-lobbyen og høyreklikk Join-stjernen for å bli med.")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            } else {
                arena.join(player);
            }
            return true;
        }

        if (no.bangerfabrikken.blockparty.arena.Arena.isMusicVotingBook(stack)) {
            no.bangerfabrikken.blockparty.arena.Arena arena = ArenaManager.get().findLobbyArenaFor(player);
            if (arena == null) arena = ArenaManager.get().findArena(player.getUUID());
            if (arena != null) {
                arena.openMusicVoting(player);
            } else {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Musikkboken fungerer i BlockParty-lobbyen.")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
            return true;
        }
        return false;
    }

    private void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getEntity().getItem();
        no.bangerfabrikken.blockparty.arena.Arena lobbyArena = ArenaManager.get().findLobbyArenaFor(player);
        no.bangerfabrikken.blockparty.arena.Arena matchArena = ArenaManager.get().findArenaForLeave(player);
        boolean inLobby = lobbyArena != null;
        boolean inActiveMatch = matchArena != null && matchArena.isMatchParticipant(player);

        // Lobby controls are protected only while the player is actually inside
        // an open BlockParty lobby. Outside BlockParty, these are ordinary items.
        boolean protectedLobbyControl = inLobby && (
                no.bangerfabrikken.blockparty.arena.Arena.isJoinGameItem(stack)
                || no.bangerfabrikken.blockparty.arena.Arena.isMusicVotingBook(stack)
                || no.bangerfabrikken.blockparty.arena.Arena.isLeaveLobbyItem(stack));

        boolean concreteDuringMatch = inActiveMatch
                && no.bangerfabrikken.blockparty.util.BlockPartyColors.isConcrete(
                net.minecraft.world.level.block.Block.byItem(stack.getItem()).defaultBlockState());
        boolean protectedMatchItem = inActiveMatch && (
                concreteDuringMatch
                || no.bangerfabrikken.blockparty.arena.Arena.isTargetColorBlock(stack)
                || no.bangerfabrikken.blockparty.arena.Arena.isLeapFeather(stack));

        // Crucially, do not globally protect Nether Stars, Books or Redstone Dust:
        // in survival outside an active lobby/match they must be droppable as normal.
        if (!protectedLobbyControl && !protectedMatchItem) return;

        if (inActiveMatch && (no.bangerfabrikken.blockparty.arena.Arena.isTargetColorBlock(stack)
                || concreteDuringMatch)) {
            // Keep the target block in its fixed centre hotbar slot, never slot 1.
            matchArena.restoreTossedTargetBlock(player, stack);
        } else if (inLobby && no.bangerfabrikken.blockparty.arena.Arena.isLeaveLobbyItem(stack)) {
            // Keep the lobby exit control anchored in hotbar slot 9.
            player.getInventory().setItem(8, stack.copy());
            player.inventoryMenu.broadcastChanges();
        } else {
            player.getInventory().placeItemBackInInventory(stack.copy());
            player.inventoryMenu.broadcastChanges();
        }

        event.setCanceled(true);
        event.getEntity().discard();
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ArenaManager.get().handleLogout(player);
        }
    }
}
