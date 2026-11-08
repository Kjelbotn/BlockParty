package no.bangerfabrikken.blockparty.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import no.bangerfabrikken.blockparty.arena.Arena;
import no.bangerfabrikken.blockparty.arena.ArenaManager;

public final class BlockPartyCommands {
    private BlockPartyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("blockparty"));
        dispatcher.register(root("bp"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
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
                .then(Commands.literal("stop")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> stop(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> remove(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("list")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("status")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> status(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("help").executes(context -> help(context.getSource())));
    }

    private static int create(CommandSourceStack source, String id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            Arena arena = ArenaManager.get().create(id, player);
            ArenaManager.get().save(source.getServer());
            source.sendSuccess(() -> Component.literal("Opprettet arena '" + arena.getId() + "'. Lobbyen er satt til posisjonen din. Sett gulvhjørnene med /bp pos1 og /bp pos2.").withStyle(ChatFormatting.GREEN), true);
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
        Arena arena = ArenaManager.get().findArena(player.getUUID());
        if (arena == null) {
            source.sendFailure(Component.literal("Du er ikke med i en BlockParty-arena."));
            return 0;
        }
        arena.leave(player, true);
        return 1;
    }

    private static int start(CommandSourceStack source, String id) {
        Arena arena = getArena(source, id);
        if (arena == null) return 0;
        try {
            arena.start(source.getServer());
            source.sendSuccess(() -> Component.literal("Startet BlockParty-arenaen '" + id + "'.").withStyle(ChatFormatting.GREEN), true);
        } catch (IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
        }
        return 1;
    }

    private static int stop(CommandSourceStack source, String id) {
        Arena arena = getArena(source, id);
        if (arena == null) return 0;
        arena.stop(source.getServer());
        source.sendSuccess(() -> Component.literal("Stoppet arenaen '" + id + "'.").withStyle(ChatFormatting.YELLOW), true);
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
        for (Arena arena : ArenaManager.get().getAll()) {
            source.sendSuccess(() -> Component.literal(arena.getStatusText()), false);
        }
        return 1;
    }

    private static int status(CommandSourceStack source, String id) {
        Arena arena = getArena(source, id);
        if (arena == null) return 0;
        source.sendSuccess(() -> Component.literal(arena.getStatusText()), false);
        return 1;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("BlockParty-kommandoer:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("/bp create <id> – opprett arena (lobby settes automatisk)"), false);
        source.sendSuccess(() -> Component.literal("/bp pos1 <id> og /bp pos2 <id> – velg gulvets motsatte hjørner"), false);
        source.sendSuccess(() -> Component.literal("/bp setlobby <id> – oppdater lobbyspawn"), false);
        source.sendSuccess(() -> Component.literal("/bp join <id> – bli med | /bp leave – forlat"), false);
        source.sendSuccess(() -> Component.literal("/bp start <id> – start manuelt | /bp stop <id> – stopp"), false);
        source.sendSuccess(() -> Component.literal("/bp list – vis arenaer | /bp status <id> – vis status"), false);
        return 1;
    }

    private static Arena getArena(CommandSourceStack source, String id) {
        Arena arena = ArenaManager.get().get(id);
        if (arena == null) {
            source.sendFailure(Component.literal("Fant ikke BlockParty-arenaen '" + id + "'. Bruk /bp list."));
        }
        return arena;
    }
}
