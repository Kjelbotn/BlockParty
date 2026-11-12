package no.bangerfabrikken.blockparty.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import no.bangerfabrikken.blockparty.arena.Arena;
import no.bangerfabrikken.blockparty.arena.ArenaGenerator;
import no.bangerfabrikken.blockparty.arena.ArenaManager;
import no.bangerfabrikken.blockparty.arena.PlayerStatsManager;
import no.bangerfabrikken.blockparty.arena.MusicTrackManager;
import no.bangerfabrikken.blockparty.arena.SchematicArenaGenerator;

public final class BlockPartyCommands {
    private BlockPartyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("blockparty"));
        dispatcher.register(root("bp"));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .executes(context -> quickJoin(context.getSource()))
                .then(Commands.literal("generate")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> generate(context.getSource(), StringArgumentType.getString(context, "id"), ArenaGenerator.DEFAULT_SIZE))
                                .then(Commands.argument("size", IntegerArgumentType.integer(ArenaGenerator.MIN_SIZE, ArenaGenerator.MAX_SIZE))
                                        .executes(context -> generate(context.getSource(), StringArgumentType.getString(context, "id"), IntegerArgumentType.getInteger(context, "size"))))))
                .then(Commands.literal("xmas").executes(context -> join(context.getSource(), "xmas")))
                .then(Commands.literal("arenas")
                        .executes(context -> publicArenaList(context.getSource())))
                .then(Commands.literal("create")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> create(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("pos1")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> setCorner(context.getSource(), StringArgumentType.getString(context, "id"), 1))))
                .then(Commands.literal("pos2")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> setCorner(context.getSource(), StringArgumentType.getString(context, "id"), 2))))
                .then(Commands.literal("setlobby")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> setLobby(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("join")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> join(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("leave")
                        .executes(context -> leave(context.getSource())))
                .then(Commands.literal("start")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> start(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("forcestart")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> forceStart(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("stop")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> stop(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> remove(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("demolish")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> demolish(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("list")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("status")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> status(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("leaderboard").executes(context -> leaderboard(context.getSource())))
                .then(Commands.literal("statsreset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> resetStats(context.getSource(),
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("rank").executes(context -> rank(context.getSource())))
                .then(Commands.literal("ranklist").executes(context -> rankList(context.getSource())))
                .then(Commands.literal("profile").executes(context -> rank(context.getSource())))
                .then(Commands.literal("vote")
                        .then(Commands.argument("track", StringArgumentType.word())
                                .executes(context -> vote(context.getSource(), StringArgumentType.getString(context, "track")))))
                .then(Commands.literal("music")
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> reloadMusic(context.getSource()))))
                .then(Commands.literal("help").executes(context -> help(context.getSource())));
    }

    private static int quickJoin(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ArenaManager manager = ArenaManager.get();
        Arena arena = manager.findArena(player.getUUID());
        if (arena == null) arena = manager.findDefaultLobbyArena();
        if (arena == null) {
            source.sendFailure(Component.literal("Fant ingen konfigurert BlockParty-lobby. Administrator må først bruke /bp generate <id> 40."));
            return 0;
        }
        Arena current = manager.findArena(player.getUUID());
        if (current != null && current != arena) current.leave(player, false);
        arena.join(player);
        player.sendSystemMessage(Component.literal("§6BlockParty av Bangerfabrikken er under utvikling. Bugs kan forekomme. Har du forslag til funksjoner eller forbedringer? Ta gjerne kontakt med oss på Discord!"));
        return 1;
    }

    private static int generate(CommandSourceStack source, String id, int size) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if ("xmas".equalsIgnoreCase(id)) return generateXmas(source, player);
        if (!id.toLowerCase(java.util.Locale.ROOT).matches("[a-z0-9_-]{1,32}")) {
            source.sendFailure(Component.literal("Arena-ID må inneholde 1–32 tegn: a-z, 0-9, _ eller -."));
            return 0;
        }

        ArenaManager manager = ArenaManager.get();
        Arena existing = manager.get(id);
        if (existing != null && (existing.getPlayerCount() > 0 || existing.isRunning())) {
            source.sendFailure(Component.literal("Arenaen finnes allerede og er i bruk. Bruk /bp leave og stopp spillet før du genererer den på nytt."));
            return 0;
        }

        try {
            source.sendSuccess(() -> Component.literal("Bygger BlockParty-stadion på " + size + " x " + size + " blokker ...").withStyle(ChatFormatting.YELLOW), true);
            ArenaGenerator.Result layout = ArenaGenerator.generate(player.serverLevel(), player.blockPosition(), size);
            if (existing != null) manager.remove(id);

            Arena arena = manager.create(id, player);
            arena.setCorner(1, layout.firstCorner());
            arena.setCorner(2, layout.secondCorner());
            arena.setDisplayCenter(layout.displayCenter());
            arena.setLobby(player.serverLevel(), layout.lobbyX(), layout.lobbyY(), layout.lobbyZ(), layout.lobbyYaw(), layout.lobbyPitch());
            manager.save(source.getServer());
            player.teleportTo(player.serverLevel(), layout.lobbyX(), layout.lobbyY(), layout.lobbyZ(), layout.lobbyYaw(), layout.lobbyPitch());
            arena.ensureLobbyItems(player);

            source.sendSuccess(() -> Component.literal("Ferdig! Arena '" + id + "' er generert. Høyreklikk den turkise Nether Star i hotbaren for å bli med; bruk boken for musikkavstemning.")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
            source.sendSuccess(() -> Component.literal("Obs: Generatoren bygger over området rundt deg. Velg et åpent område uten bygninger du vil beholde." )
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal("Kunne ikke generere arenaen: " + exception.getMessage()));
            return 0;
        }
    }


    private static int generateXmas(CommandSourceStack source, ServerPlayer player) {
        ArenaManager manager = ArenaManager.get();
        Arena existing = manager.get("xmas");
        if (existing != null && (existing.getPlayerCount() > 0 || existing.isRunning())) {
            source.sendFailure(Component.literal("Xmas-arenaen er i bruk. Stopp kampen og tøm arenaen før du genererer på nytt."));
            return 0;
        }
        try {
            source.sendSuccess(() -> Component.literal("Genererer BlockParty Xmas ...").withStyle(ChatFormatting.YELLOW), true);
            SchematicArenaGenerator.Result layout = SchematicArenaGenerator.generate(player.serverLevel(), player.blockPosition());
            if (existing != null) manager.remove("xmas");
            Arena arena = manager.create("xmas", player);
            arena.setCorner(1, layout.floorFirst());
            arena.setCorner(2, layout.floorSecond());
            arena.setTargetDisplay(layout.displayCenter(), layout.displayYaw());
            arena.setLobby(player.serverLevel(), layout.lobbyX(), layout.lobbyY(), layout.lobbyZ(), layout.lobbyYaw(), layout.lobbyPitch());
            manager.save(source.getServer());
            player.teleportTo(player.serverLevel(), layout.lobbyX(), layout.lobbyY(), layout.lobbyZ(), layout.lobbyYaw(), layout.lobbyPitch());
            arena.ensureLobbyItems(player);
            source.sendSuccess(() -> Component.literal("Xmas er generert! Bruk /bp xmas for å gå inn.").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Kunne ikke generere Xmas: " + exception.getMessage()));
            return 0;
        }
    }

    private static int publicArenaList(CommandSourceStack source) {
        var arenas = ArenaManager.get().getAll();
        if (arenas.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Det finnes ingen BlockParty-arenaer ennå."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Tilgjengelige BlockParty-arenaer:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (Arena arena : arenas) {
            source.sendSuccess(() -> Component.literal("• " + arena.getId() + " — bruk /bp join " + arena.getId())
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }

    private static int create(CommandSourceStack source, String id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            Arena arena = ArenaManager.get().create(id, player);
            ArenaManager.get().save(source.getServer());
            source.sendSuccess(() -> Component.literal("Opprettet arena '" + arena.getId() + "'. For automatisk bygging: /bp generate " + id + " 40.").withStyle(ChatFormatting.GREEN), true);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
        }
        return 1;
    }

    private static int setCorner(CommandSourceStack source, String id, int corner) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Arena arena = getArena(source, id);
        if (arena == null) return 0;
        if (arena.getPlayerCount() > 0) {
            source.sendFailure(Component.literal("Tøm arenaen for spillere før du endrer gulvoppsettet."));
            return 0;
        }
        if (arena.getLevel() != player.serverLevel()) {
            source.sendFailure(Component.literal("Du må velge begge hjørnene i samme dimensjon som arenaen ble opprettet i."));
            return 0;
        }
        BlockPos floorBlock = player.blockPosition().below();
        if (corner == 1 && arena.getPos2() != null && arena.getPos2().getY() != floorBlock.getY()) {
            source.sendFailure(Component.literal("Hjørnene må være på samme Y-høyde."));
            return 0;
        }
        if (corner == 2 && arena.getPos1() != null && arena.getPos1().getY() != floorBlock.getY()) {
            source.sendFailure(Component.literal("Hjørnene må være på samme Y-høyde."));
            return 0;
        }
        arena.setCorner(corner, floorBlock);
        ArenaManager.get().save(source.getServer());
        source.sendSuccess(() -> Component.literal("Pos" + corner + " for '" + id + "' satt til " + floorBlock.toShortString() + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setLobby(CommandSourceStack source, String id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Arena arena = getArena(source, id);
        if (arena == null) return 0;
        if (arena.getPlayerCount() > 0) {
            source.sendFailure(Component.literal("Tøm arenaen for spillere før du endrer lobbyspawn."));
            return 0;
        }
        if ((arena.getPos1() != null || arena.getPos2() != null) && arena.getLevel() != player.serverLevel()) {
            source.sendFailure(Component.literal("Lobbyen må være i samme dimensjon som gulvet."));
            return 0;
        }
        arena.setLobby(player);
        ArenaManager.get().save(source.getServer());
        source.sendSuccess(() -> Component.literal("Lobbyspawn for '" + id + "' er oppdatert.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int join(CommandSourceStack source, String id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Arena arena = getArena(source, id);
        if (arena == null) return 0;
        Arena current = ArenaManager.get().findArena(player.getUUID());
        if (current != null && current != arena) current.leave(player, true);
        arena.join(player);
        return 1;
    }

    private static int leave(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Arena arena = ArenaManager.get().findArenaForLeave(player);
        if (arena == null) {
            source.sendFailure(Component.literal("Fant ingen aktiv BlockParty-økt i nærheten. Hvis du fortsatt sitter fast, kontakt en administrator."));
            return 0;
        }
        arena.leave(player, true);
        return 1;
    }

    private static int start(CommandSourceStack source, String id) {
        Arena arena = getArena(source, id);
        if (arena == null) return 0;
        if (arena.getPlayerCount() < Arena.MIN_PLAYERS) {
            source.sendFailure(Component.literal("Vanlig start krever minst 2 spillere. Bruk /bp forcestart " + id + " for å teste alene."));
            return 0;
        }
        try {
            arena.start(source.getServer());
            source.sendSuccess(() -> Component.literal("Startet BlockParty-arenaen '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
        } catch (IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
        }
        return 1;
    }

    private static int forceStart(CommandSourceStack source, String id) {
        Arena arena = getArena(source, id);
        if (arena == null) return 0;
        if (arena.getPlayerCount() != 1) {
            source.sendFailure(Component.literal("Force-start er ment for solo-testing og krever nøyaktig én påmeldt spiller."));
            return 0;
        }
        try {
            arena.start(source.getServer());
            source.sendSuccess(() -> Component.literal("Solo-test startet for arena '" + id + "'.").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
            return 1;
        } catch (IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int stop(CommandSourceStack source, String id) {
        Arena arena = getArena(source, id);
        if (arena == null) return 0;
        arena.stop(source.getServer());
        source.sendSuccess(() -> Component.literal("Stoppet arenaen '" + id + "'.").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int demolish(CommandSourceStack source, String id) {
        Arena arena = getArena(source, id);
        if (arena == null) return 0;
        if (arena.getPlayerCount() > 0 || arena.isRunning()) {
            source.sendFailure(Component.literal("Arenaen må være tom og spillet må være stoppet før den kan pakkes ned."));
            return 0;
        }
        if (arena.getPos1() == null || arena.getPos2() == null) {
            source.sendFailure(Component.literal("Arenaen har ingen generert stadionflate å rive."));
            return 0;
        }
        ArenaGenerator.demolish(arena.getLevel(), arena.getPos1(), arena.getPos2(), arena.getLobbyX(), arena.getLobbyZ());
        ArenaManager.get().remove(id);
        ArenaManager.get().save(source.getServer());
        source.sendSuccess(() -> Component.literal("BlockParty-arenaen '" + id + "' er revet og fjernet fra listen.").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        source.sendSuccess(() -> Component.literal("Merk: Området tømmes til luft. Terreng som ble overskrevet ved generering kan ikke gjenopprettes automatisk." ).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int remove(CommandSourceStack source, String id) {
        try {
            boolean removed = ArenaManager.get().remove(id);
            if (!removed) {
                source.sendFailure(Component.literal("Fant ikke arenaen '" + id + "'."));
                return 0;
            }
            ArenaManager.get().save(source.getServer());
            source.sendSuccess(() -> Component.literal("Fjernet arenaen '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int list(CommandSourceStack source) {
        if (ArenaManager.get().getAll().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Ingen BlockParty-arenaer er opprettet ennå."), false);
            return 1;
        }
        for (Arena arena : ArenaManager.get().getAll()) source.sendSuccess(() -> Component.literal(arena.getStatusText()), false);
        return 1;
    }

    private static int status(CommandSourceStack source, String id) {
        Arena arena = getArena(source, id);
        if (arena == null) return 0;
        source.sendSuccess(() -> Component.literal(arena.getStatusText()), false);
        return 1;
    }

    private static int resetStats(CommandSourceStack source, String playerName) {
        if (!PlayerStatsManager.resetByName(source.getServer(), playerName)) {
            source.sendFailure(Component.literal("Fant ingen BlockParty-statistikk for spilleren '" + playerName + "'."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("BlockParty-statistikken til " + playerName
                + " er nullstilt: poeng, win streak, best win streak, personlig rekord, seire og MVP-priser.")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        return 1;
    }

    private static int leaderboard(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerStatsManager.sendLeaderboard(player);
        return 1;
    }

    private static int rank(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerStatsManager.sendPlayerRank(player);
        return 1;
    }

    private static int rankList(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerStatsManager.sendRankList(player);
        return 1;
    }

    private static int vote(CommandSourceStack source, String trackId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Arena arena = ArenaManager.get().findLobbyArenaFor(player);
        if (arena == null) arena = ArenaManager.get().findArena(player.getUUID());
        if (arena == null) {
            source.sendFailure(Component.literal("Du må stå i en åpen BlockParty-lobby for å stemme."));
            return 0;
        }
        return arena.voteMusic(player, trackId) ? 1 : 0;
    }

    private static int reloadMusic(CommandSourceStack source) {
        MusicTrackManager.load(source.getServer());
        ArenaManager.get().refreshLobbyDisplays();
        int loaded = MusicTrackManager.getTracks().size();
        source.sendSuccess(() -> Component.literal("Lastet inn " + loaded + " BlockParty-sanger fra blockparty-music.properties.")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        return loaded;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("BlockParty-kommandoer:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("/bp generate <id> [størrelse] – bygg Classic-arenaen"), false);
        source.sendSuccess(() -> Component.literal("/bp arenas – vis valgbare arenaer | /bp join <id> – velg arena"), false);
        source.sendSuccess(() -> Component.literal("/bp create <id> – opprett en tom, manuelt konfigurert arena"), false);
        source.sendSuccess(() -> Component.literal("/bp pos1 <id> og /bp pos2 <id> – velg gulvets motsatte hjørner"), false);
        source.sendSuccess(() -> Component.literal("/bp setlobby <id> – oppdater lobbyspawn"), false);
        source.sendSuccess(() -> Component.literal("Høyreklikk den turkise Join-stjernen i lobbyen for å bli med. /bp join <id> er fortsatt tilgjengelig som reserve."), false);
        source.sendSuccess(() -> Component.literal("Høyreklikk Music Vote-boken for å stemme på musikk | /bp leave – forlat"), false);
        source.sendSuccess(() -> Component.literal("/bp start <id> – start manuelt | /bp forcestart <id> – admin solo-test (1 spiller) | /bp stop <id> – stopp"), false);
        source.sendSuccess(() -> Component.literal("Sanger under 1:30 spilles med 10 runder. Sanger på 1:30 eller mer spilles med 20 runder. Rundetiden skaleres etter låtlengden."), false);
        source.sendSuccess(() -> Component.literal("/bp list – vis arenaer | /bp status <id> – vis status"), false);
        source.sendSuccess(() -> Component.literal("/bp leaderboard – se topp 5 | /bp rank – spillerprofil | /bp ranklist – alle ranker og poengkrav"), false);
        source.sendSuccess(() -> Component.literal("/bp statsreset <navn> – nullstill en spillers BlockParty-statistikk (admin)"), false);
        source.sendSuccess(() -> Component.literal("Lobbyen teller ned fra 60 sekunder. /bp start <id> starter umiddelbart."), false);
        source.sendSuccess(() -> Component.literal("/bp music reload – last inn egendefinert spilleliste (admin)"), false);
        return 1;
    }

    private static Arena getArena(CommandSourceStack source, String id) {
        Arena arena = ArenaManager.get().get(id);
        if (arena == null) source.sendFailure(Component.literal("Fant ikke BlockParty-arenaen '" + id + "'. Bruk /bp list."));
        return arena;
    }
}
