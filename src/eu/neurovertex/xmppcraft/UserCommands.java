package eu.neurovertex.xmppcraft;

import java.util.logging.Level;
import java.util.regex.Pattern;

import static eu.neurovertex.xmppcraft.ChatBot.language;
import static eu.neurovertex.xmppcraft.UserRegistry.*;

/**
 * @author Neurovertex
 *         Date: 14/09/2014, 15:32
 */
public final class UserCommands {
	private UserCommands() {
	}

	public static void init(ChatBot bot) {
		String category = "User";
		bot.registerCommand(new PrefixBotCommand("useradd", category, OP, "useradd <gamename|nickname> <name>", "useradd") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				String parts[] = command.split(" ");
				if (parts[1].equalsIgnoreCase("nickname"))
					UserRegistry.REGISTRY.registerFromXMPP(parts[2]);
				else if (parts[1].equalsIgnoreCase("gamename") || parts[1].equalsIgnoreCase("username"))
					UserRegistry.REGISTRY.registerFromGame(parts[2], null);
				return new ChatBot.CommandResponse(language.getString("user.useradd.success", "User added to my database. I am watching you."));
			}
		});

		bot.registerCommand(new PrefixBotCommand("usermod", category, OP, "usermod <name> <JID|level> value", "usermod") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				String parts[] = command.split(" ");
				if (parts.length != 4)
					throw new ChatBot.CommandSyntaxException();
				UserRegistry.User u = UserRegistry.REGISTRY.getUser(parts[1]);
				if (u == null) {
					return new ChatBot.CommandResponse("Can't find user '" + parts[1] + "'");
				}
				if (parts[2].equalsIgnoreCase("level")) {
					int level = -1;
					if (parts[3].length() == 1) {
						try {
							level = Integer.parseInt(parts[3]);
						} catch (NumberFormatException e) {
							throw new ChatBot.CommandSyntaxException("Couldn't parse number " + parts[3]);
						}
					} else {
						int l = 0;
						for (String rank : UserRegistry.RANK_NAMES) {
							if (rank.equalsIgnoreCase(parts[3]))
								level = l;
							l++;
						}
					}
					if (level == -1) {
						throw new ChatBot.CommandSyntaxException("Couldn't resolve rank name " + parts[3]);
					} else {
						if (level <= issuer.getLevel()) {
							UserRegistry.REGISTRY.setLevel(u, level);
							return new ChatBot.CommandResponse("Successfully changed user level");
						} else
							return new ChatBot.CommandResponse("Can't set level higher than yours");
					}
				} else if (parts[2].equalsIgnoreCase("JID")) {
					UserRegistry.REGISTRY.setJID(u, parts[3]);
					return new ChatBot.CommandResponse("Successfully changed user's JID");
				} else {
					throw new ChatBot.CommandSyntaxException("Unknown usermod parameter : " + parts[2]);
				}
			}
		});

		bot.registerCommand(new PrefixBotCommand("userdel", category, OP, "userdel <name>", "userdel") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				String name = command.split(" ")[1];
				UserRegistry.User u = UserRegistry.REGISTRY.getUser(name);
				if (u == null) {
					return new ChatBot.CommandResponse("Can't find user '" + name + "'");
				}
				UserRegistry.REGISTRY.deleteUser(u);
				return new ChatBot.CommandResponse("Successfully deleted");

			}
		});

		bot.registerCommand(new PrefixBotCommand("lookup", category, OP, "lookup <username <username>|gamename <gamename>|everyone [level] [UUID] [JID]>", "lookup") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				command = command.substring(7);
				StringBuilder output = new StringBuilder(100);
				if (command.startsWith("player ") || command.startsWith("username ") || command.startsWith("gamename ") || (command.startsWith("user ") && source == ChatBot.Source.GAME)) {
					command = command.substring(command.indexOf(' ') + 1);
					String names[] = command.split(" ");
					for (String name : names)
						if (name.length() > 0)
							output.append(name).append(" > ").append(UserRegistry.REGISTRY.getByGamename(name)).append('\n');
				} else if (command.startsWith("nickname ") || (command.startsWith("user ") && source != ChatBot.Source.GAME)) {
					command = command.substring(command.indexOf(' ') + 1);
					String names[] = command.split(" ");
					for (String name : names)
						if (name.length() > 0)
							output.append(name).append(" > ").append(UserRegistry.REGISTRY.getByNickname(name)).append('\n');
				} else if (command.startsWith("everyone")) {
					output.append(UserRegistry.REGISTRY.getUsers().size()).append(" user in registry :\n");
					boolean level = command.contains("level"), jid = command.toLowerCase().contains("jid"), uuid = command.toLowerCase().contains("uuid");
					for (UserRegistry.User u : UserRegistry.REGISTRY.getUsers()) {
						output.append(u.toString());
						if (level)
							output.append(" (").append(u.getLevel()).append(")");
						if (jid)
							output.append(" (").append(u.getJid()).append(")");
						if (uuid)
							output.append(" (").append(u.getUuid()).append(")");
						output.append('\n');
					}
				} else
					throw new ChatBot.CommandSyntaxException();
				if (output.length() > 1)
					if (output.charAt(output.length() - 1) == '\n')
						output.deleteCharAt(output.length() - 1); // Remove trailing line feed if necessary
					else
						throw new ChatBot.CommandException("An error occured", Level.WARNING);
				return new ChatBot.CommandResponse(output.toString());
			}
		});

		bot.registerCommand(new RegexBotCommand("nickname", category, USER + 1, "<local name> is <remote name>", Pattern.compile("([^ ]+) is ([^ ]+)")) {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				String gamename = getMatcher().group(1), nickname = getMatcher().group(2), tmp;
				if (REGISTRY.getByGamename(nickname) != null || REGISTRY.getByNickname(gamename) != null) {
					if (REGISTRY.getByNickname(nickname) != null || REGISTRY.getByGamename(gamename) != null)
						throw new ChatBot.CommandException("Ambiguous references", Level.INFO);
					tmp = gamename;
					gamename = nickname;
					nickname = tmp;
				}

				ChatBot.CommandResponse response;
				UserRegistry.REGISTRY.link(gamename, nickname);
				response = new ChatBot.CommandResponse(String.format("Successfully linked player %s to %s", gamename, nickname), null, true, true);
				return response;
			}
		});
	}
}
