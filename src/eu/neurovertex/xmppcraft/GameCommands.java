package eu.neurovertex.xmppcraft;

import com.google.common.base.Joiner;
import com.sun.istack.internal.NotNull;
import eu.neurovertex.xmppcraft.nbtparser.NBTParser;
import eu.neurovertex.xmppcraft.nbtparser.NBTPath;
import eu.neurovertex.xmppcraft.nbtparser.PlayerData;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static eu.neurovertex.xmppcraft.Main.log;
import static eu.neurovertex.xmppcraft.UserRegistry.*;

/**
 * @author Neurovertex
 *         Date: 14/09/2014, 15:34
 */
public final class GameCommands {
	private static File worldDir = new File("world"), playerDataDir = new File(worldDir, "playerdata");
	private static NBTParser levelData = NBTParser.parseFile(new File(worldDir, "level.dat"));
	private static Map<String, PlayerData> playerData = new HashMap<>();

	private GameCommands() {
	}

	public static void init(ChatBot bot) {
		String category = "Game";

		bot.registerCommand(new AbstractBotCommand.PrefixBotCommand("$", category, OP, "", "$") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				List<String> output = bot.gameCommand(command.substring(1), true);
				if (output.size() > bot.getMaxLines()) {
					output = output.subList(0, bot.getMaxLines() - 1);
					output.add("... Command output exceeded max line count");
				}
				if (source == ChatBot.Source.GAME)
					return new ChatBot.CommandResponse("Successfully executed");
				else
					return new ChatBot.CommandResponse(Joiner.on("\n").join(output));
			}
		});

		bot.registerCommand(new AbstractBotCommand.PrefixBotCommand("say", category, USER + 2, "say <message>", "say") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				bot.gameMessage(command.substring(command.indexOf(" ") + 1));
				if (source != ChatBot.Source.GAME)
					return new ChatBot.CommandResponse("[server] " + command.substring(command.indexOf(" ") + 1));
				else
					return new ChatBot.CommandResponse();
			}
		});

		bot.registerCommand(new AbstractBotCommand("shutdown", category, 4, "stop|shutdown|poweroff") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {

				bot.gameMessage("Stopping the server");
				try {

					UserRegistry.REGISTRY.save();
					Main.getInstance().getSettings().save();
					ChatBot.language.save();
				} catch (IOException e) {
					bot.mucMessage("Error saving settings");
					e.printStackTrace();
				}
				try {
					Thread.sleep(500);
				} catch (InterruptedException ignore) {
				}
				bot.gameCommand("stop", false);
				return new ChatBot.CommandResponse();
			}

			@Override
			public boolean matches(String command) {
				for (String s : new String[]{"shut down", "shutdown", "poweroff", "stop"})
					if (command.startsWith(s))
						return true;
				return false;
			}
		});

		bot.registerCommand(new AbstractBotCommand.PrefixBotCommand("list", category, ANON, "list", "list") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				return new ChatBot.CommandResponse(Joiner.on("\n").join(bot.gameCommand("list", true)));
			}
		});

		bot.registerCommand(new AbstractBotCommand.PrefixBotCommand("getnbt", category, OP, "getnbt <level|#<user>>", "getnbt ") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				String parts[] = command.split(" ");
				NBTParser data;
				if (parts[1].equalsIgnoreCase("level"))
					data = levelData;
				else if (parts[1].startsWith("#")) {
					String name = parts[1].substring(1);
					UserRegistry.User u = REGISTRY.getByGamename(name);
					if (u == null)
						return new ChatBot.CommandResponse(String.format(ChatBot.language.getString("general.unknownuser", "Unknown user %s"), name));
					else if (u.getUuid() == null)
						return new ChatBot.CommandResponse(String.format(ChatBot.language.getString("general.unknownuser", "User %s has no UUID associated"), name));
					try {
						data = getPlayerData(u.getUuid()).getParser();
					} catch (Exception e) {
						throw new ChatBot.CommandException("Error while loading player data", e, Level.SEVERE);
					}
				} else
					throw new ChatBot.CommandSyntaxException("Unrecognized argument " + parts[1]);
				try {
					NBTPath path = NBTPath.parse(parts[2]);
					return new ChatBot.CommandResponse(path.getElement(data.getRootTag()).toString());
				} catch (NBTPath.NBTPathException e) {
					log.log(Level.INFO, "Invalid NBT Path", e);
					return new ChatBot.CommandResponse(ChatBot.language.getString("game.getnbt.error.path", "You could at least try to give me a valid path *sigh*"));
				}
			}
		});

		bot.registerCommand(new AbstractBotCommand.PrefixBotCommand("locate", category, OP, "locate <gamename>", "locate ") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				String parts[] = command.split(" ");
				UserRegistry.User u = REGISTRY.getByGamename(parts[1]);
				if (u == null)
					return new ChatBot.CommandResponse(String.format(ChatBot.language.getString("general.unknownuser", "Unknown user %s"), parts[1]));
				else if (u.getUuid() == null)
					return new ChatBot.CommandResponse(String.format(ChatBot.language.getString("general.unknownuser", "User %s has no UUID associated"), parts[1]));
				try {
					PlayerData data = getUpdatedPlayerData(u.getUuid());
					return new ChatBot.CommandResponse(String.format("Player is at %d:%d, %d (XZ, Y)", data.getX(), data.getZ(), data.getY()));
				} catch (Exception e) {
					throw new ChatBot.CommandException("Error while loading player data", e, Level.SEVERE);
				}
			}
		});

	}

	private static
	@NotNull
	PlayerData getPlayerData(String uuid) throws IOException {
		PlayerData data = playerData.get(uuid);
		if (data == null) {
			data = new PlayerData(new File(playerDataDir, uuid + ".dat"));
			playerData.put(uuid, data);
		}
		return data;
	}

	private static
	@NotNull
	PlayerData getUpdatedPlayerData(String uuid) throws IOException {
		PlayerData data = getPlayerData(uuid);
		if (data.getUpdateAge() > Main.getInstance().getSettings().getInteger("nbtparser.minage", 5)) {
			Main.getInstance().getXMPPManager().getBot().gameCommand("save-all", true); // Force player file write
			data.update();
		}
		return data;
	}
}
