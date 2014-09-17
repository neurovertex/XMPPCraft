package eu.neurovertex.xmppcraft;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.muc.MultiUserChat;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static eu.neurovertex.xmppcraft.UserRegistry.*;

/**
 * @author Neurovertex
 *         Date: 13/09/2014, 13:45
 */
public class ChatBot implements GameListener, PacketListener, ChatManagerListener, MessageListener {
	private final PrintStream gameInput;
	public static final Settings language = new Settings("lang.json", true);

	private static final Logger log = Logger.getLogger(ChatBot.class.getName());
	private final Roster roster;
	private static Pattern commandPattern,
			uuid = Pattern.compile("UUID of player ([^ ]+) is (.+)"),
			charEscape = Pattern.compile("(?<!\\\\)[@§]");
	private Map<String, BotCommand> commands = new HashMap<>();
	private boolean mtxMirror, xtmMirror;
	private long captureDelay = 1000;
	private int maxLen = 1024, maxLines = 16;
	private Map<UserRegistry.User, Chat> openChats = new HashMap<>();
	private MultiUserChat muc;
	private String JID;
	private Exception lastException;

	ChatBot() throws SmackException.NotConnectedException, XMPPException, SmackException.NoResponseException {
		this.gameInput = new PrintStream(Main.getInstance().getPipeToStdin());
		roster = Main.getInstance().getXMPPManager().getConnection().getRoster();
		UserCommands.init(this);
		GameCommands.init(this);
		XMPPCommands.init(this);
		initCoreCommands(this);
		init();
	}

	protected void init() throws SmackException.NotConnectedException, XMPPException, SmackException.NoResponseException {
		Settings settings = Main.getInstance().getSettings();
		mtxMirror = "true".equalsIgnoreCase(settings.getString("chatbot.mirror.gametoxmpp"));
		xtmMirror = "true".equalsIgnoreCase(settings.getString("chatbot.mirror.xmpptogame"));
		maxLen = settings.getInteger("chatbot.maxlen");
		maxLines = settings.getInteger("chatbot.maxlines");
		captureDelay = settings.getInteger("chatbot.capturedelay");
		this.muc = new MultiUserChat(Main.getInstance().getXMPPManager().getConnection(), settings.getString("xmpp.muc.jid"));
		muc.addMessageListener(this);
		muc.join(settings.getString("xmpp.muc.nick"));
		muc.sendMessage(language.getString("general.greeting", "Oh look, it's humans, my favourite people to talk to."));
		log.info(String.format("Joined room as %s", JID = muc.getRoom() + "/" + muc.getNickname()));
		commandPattern = Pattern.compile(muc.getNickname() + "(?:[,: ] ?)?(.+)", Pattern.CASE_INSENSITIVE);
	}

	public java.util.List<String> gameCommand(String command, boolean capture) {
		gameInput.println(command);
		if (capture)
			try {
				return Main.getInstance().getLogParser().capture(captureDelay);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		return null;
	}

	public void gameMessage(String message) {
		log.finest("Sending message : " + message);
		Matcher matcher = charEscape.matcher(message);
		message = matcher.replaceAll("\\\\$0");
		for (String str : message.split("\n"))
			if (str.length() > 0)
				gameCommand("say " + str, false);
	}

	public void registerCommand(BotCommand command) {
		commands.put(command.getName(), command);
	}

	private void initCoreCommands(ChatBot bot) {

		String coreCat = "Core", utilsCat = "Utils";

		bot.registerCommand(new PrefixBotCommand("save", coreCat, OP, "save settings|database|all", "save ") {

			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				//String parts[] = command.split(" "); // For later
				try {
					Main.getInstance().getSettings().save();
					return new CommandResponse(language.getString("core.save", "There, I saved the settings for you. You lazy organism."));
				} catch (IOException e) {
					throw new CommandException(language.getString("error.save", "Error while saving settings"), e, Level.SEVERE);
				}
			}
		});

		bot.registerCommand(new PrefixBotCommand("get", coreCat, ADMIN, "get [file!]<setting name>", "get") {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				String parts[] = command.split(" ");
				Settings settings = Main.getInstance().getSettings();
				String name;
				if (parts.length != 2)
					throw new CommandSyntaxException();
				name = parts[1];
				if (name.contains("!")) {
					parts = parts[1].split("!");
					if (parts[0].equalsIgnoreCase("language"))
						settings = language;
					else if (!parts[0].equalsIgnoreCase("settings"))
						throw new CommandException("Unknown settings file " + parts[0], Level.INFO);
					name = parts[1];
				}
				Object val = settings.get(name);
				return new CommandResponse(String.format("(%s): %s", (val != null) ? val.getClass().getSimpleName() : "null", String.valueOf(val)), issuer);
			}
		});

		bot.registerCommand(new PrefixBotCommand("set", coreCat, ADMIN, "set [file!]<setting name> <value>", "set") {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				Settings settings = Main.getInstance().getSettings();
				String parts[] = command.split(" "), name = parts[1];
				Object curVal = settings.get(parts[1]), val = command.substring(parts[0].length() + parts[1].length() + 2);
				if (name.contains("!")) {
					parts = name.split("!");
					if (parts[0].equalsIgnoreCase("language"))
						settings = language;
					else if (!parts[0].equalsIgnoreCase("settings"))
						throw new CommandException("Unknown settings file " + parts[0], Level.INFO);
					name = parts[1];

				}
				if (curVal != null && curVal instanceof Number)
					if (curVal instanceof Integer) {
						try {
							settings.put(name, Integer.parseInt((String) val));
						} catch (NumberFormatException e) {
							throw new CommandException("Input: " + val, e, Level.WARNING);
						}
					} else {
						try {
							settings.put(name, Double.parseDouble((String) val));
						} catch (NumberFormatException e) {
							throw new CommandException("Input: " + val, e, Level.WARNING);
						}
					}
				settings.put(name, val);
				log.info("Setting " + name + " to " + val + " in " + settings);
				try {
					settings.save();
				} catch (IOException e) {
					throw new CommandException(language.getString("error.save", "Error while saving settings"), e, Level.WARNING);
				}
				return new CommandResponse("Value successfully changed");
			}
		});

		bot.registerCommand(new PrefixBotCommand("reset", coreCat, ADMIN, "reset", "reset") {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				Main.getInstance().reset();
				return new CommandResponse();
			}
		});

		bot.registerCommand(new PrefixBotCommand("version", coreCat, ANON, "version", "version") {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				String version = Main.getInstance().getUpdater().getVersion();
				StringBuilder output = new StringBuilder();
				try {
					String newVersion = Main.getInstance().getUpdater().checkVersion();
					if (!version.equals(newVersion) && issuer.getLevel() >= ADMIN)
						output.append(String.format(language.getString("core.version.new", "Current version : %s. Oh by the way, I found a new version lying around, %s. Lazy enough ?"), version, newVersion));
				} catch (IOException ignore) {
				}
				if (output.length() == 0)
					output.append("Current version : ").append(version);
				if (command.contains(" ") && command.split(" ")[1].equals("-l")) {
					output.append("\nJVM: ").append(System.getProperty("java.vm.name")).append(' ')
							.append(System.getProperty("java.runtime.version")).append(" on ")
							.append(System.getProperty("os.name"));
				}
				return new CommandResponse(output.toString());
			}
		});

		bot.registerCommand(new PrefixBotCommand("update", coreCat, ADMIN, "update", "update") {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				try {
					String version = Main.getInstance().getUpdater().getVersion(), newVersion = Main.getInstance().getUpdater().checkVersion();
					boolean force = (command.contains(" ") && command.split(" ")[1].equalsIgnoreCase("-f"));
					if (!version.equals(newVersion) || force) {
						String message = String.format(language.getString("core.update.found", "Yes I found your new thing, %s. Give me a minute"), newVersion);
						mucMessage(message);
						gameMessage(message);
						muc.leave();
						Main.getInstance().update();
						return new CommandResponse();
					} else
						return new CommandResponse(language.getString("core.update.none", "There's nothing there. Are you genuinely doing that just to waste my time ?"), null);
				} catch (SmackException.NotConnectedException | ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | IOException e) {
					throw new CommandException("Error while checking version", e, Level.SEVERE);
				}
			}
		});

		bot.registerCommand(new PrefixBotCommand("geterror", coreCat, OP, "what was that ?", "what was that") {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				if (lastException == null)
					return new CommandResponse(language.getString("core.lasterror.null", "What was what ? There's nothing in my logs."));
				else
					return new CommandResponse(String.format(language.getString("core.lasterror", "Last error was %s : %s"),
							lastException.getClass().getName(),
							lastException.getMessage() != null ? lastException.getMessage() : "no message attached"));
			}
		});

		bot.registerCommand(new PrefixBotCommand("help", utilsCat, ANON, "help [command]", "help") {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				String parts[] = command.split(" ");
				if (parts.length == 1) {
					StringBuilder output = new StringBuilder(100);
					for (BotCommand cmd : commands.values())
						if (issuer.getLevel() >= cmd.getLevel() && cmd.isEnabled())
							output.append(cmd.getName()).append(", ");
					output.delete(output.length() - 2, output.length());
					return new CommandResponse(output.toString());
				} else if (parts.length > 1 && parts[1].length() > 0) {
					BotCommand cmd = commands.get(parts[1]);
					if (cmd == null)
						return new CommandResponse("Command not found");
					else {
						String output = cmd.getSyntax();
						if (cmd.getHelp() != null)
							output += " : " + cmd.getHelp();
						return new CommandResponse(output);
					}
				} else
					throw new CommandSyntaxException("Too many arguments");
			}
		});

		bot.registerCommand(new PrefixBotCommand("reload", utilsCat, ADMIN, "reload <settings|language|users>", "reload") {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				String parts[] = command.split(" ");
				if (parts.length != 2)
					throw new CommandSyntaxException();
				try {
					switch (parts[1].toLowerCase()) {
						case "settings":
							Main.getInstance().getSettings().load();
							break;
						case "language":
							language.load();
							break;
						case "users":
							REGISTRY.load();
							break;
						default:
							return new CommandResponse("Unknown settings '" + parts[1] + "'");
					}
					return new CommandResponse(language.getString("utils.reload.done", "Can you really not do it yourselt ? Anyway, Done."));
				} catch (IOException e) {
					throw new CommandException(e, Level.SEVERE);
				}
			}
		});

		bot.registerCommand(new RegexBotCommand("ping", utilsCat, ANON, "ping|?", Pattern.compile("(ping|\\?|\\s*)")) {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				return new CommandResponse("Yes ?");
			}
		});

		bot.registerCommand(new RegexBotCommand("toggle", utilsCat, OP, "enable|disable <MtX|XtM>", Pattern.compile("(enable|disable) .*")) {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				String parts[] = command.split(" ");
				boolean val = command.startsWith("enable");
				if (parts[1].equalsIgnoreCase("xtg") || parts[1].equalsIgnoreCase("xtm")) {
					Main.getInstance().getSettings().put("chatbot.mirror.xmpptogame", val ? "true" : "false");
					xtmMirror = val;
					return new CommandResponse("XMPP to Minecraft mirrorring " + (val ? "enabled" : "disabled"));
				} else if (parts[1].equalsIgnoreCase("gtx") || parts[1].equalsIgnoreCase("mtx")) {
					Main.getInstance().getSettings().put("chatbot.mirror.gametoxmpp", val ? "true" : "false");
					mtxMirror = val;
					return new CommandResponse("Minecraft to XMPP mirrorring " + (val ? "enabled" : "disabled"));
				} else
					throw new CommandSyntaxException();
			}
		});
	}

	private CommandResponse parseCommand(String command, String issuer, Source source) {
		UserRegistry.User user = null;
		switch (source) {
			case GAME:
				user = REGISTRY.getByGamename(issuer);
				break;
			case MUC:
				user = REGISTRY.getByNickname(issuer);
				break;
			case PM:
				user = REGISTRY.getByJID(issuer);
				break;
		}
		int level = (user != null) ? user.getLevel() : ANON;
		if (command.toLowerCase().startsWith("who am i"))
			return new CommandResponse(String.format(language.getString("general.whoami", "You are %s, %s (%d). How do you manage to forget that ?"), issuer, (level >= 0 ? UserRegistry.RANK_NAMES.get(level) : "Anon"), level));
		log.info(String.format("Parsing %s command : <%s> '%s'", source == Source.GAME ? "game" : "XMPP", issuer, command));
		if (source == Source.MUC && user != null && user.getJid() != null && roster.contains(user.getJid()))
			if (!roster.getPresence(user.getJid()).isAvailable()) {
				log.severe("Attempted impersonation : " + issuer + " on >" + command);
				return new CommandResponse(language.getString("error.impersonation", "You really do think I'm stupider than you, don't you ?"));
			}

		for (BotCommand cmd : commands.values())
			if (cmd.matches(command) && cmd.isEnabled()) {
				if (level >= cmd.getLevel()) {
					try {
						return cmd.execute(this, user, command, source);
					} catch (CommandSyntaxException e) {
						log.log(Level.INFO, "Syntax error in command " + cmd.getName(), e);
						return new CommandResponse("You messed up the syntax. You incapable.\n" + cmd.getSyntax());
					} catch (Exception e) {
						lastException = e;
						log.log(Level.SEVERE, "Error while executing command", e);
						return new CommandResponse("It seems something went wrong. Oh well, too bad.");
					}
				} else {
					if (level >= 0)
						return new CommandResponse(language.getString("error.privilege", String.format("Oh would you look at you, trying to play %s. Hilarious.", cmd.getLevel() > OP ? "admin" : "operator")));
					else
						return new CommandResponse(language.getString("error.anon", "... Who even are you ? Actually, I don't care."));
				}

			}
		return new CommandResponse(language.getString("general.unknown", "Do I have to underclock my processor to human level to understand this command ? I got nothing in my registry"));
	}

	public void mucMessage(String message) {
		log.finest("Sending message : " + message);
		if (!muc.isJoined() || !Main.getInstance().getXMPPManager().getConnection().isConnected())
			return;
		if (message.length() > maxLen)
			message = message.substring(0, maxLen).concat(language.getString("error.maxlen", "... That's too long. I'm not gonna bother"));
		try {
			muc.sendMessage(message);
		} catch (XMPPException | SmackException.NotConnectedException e) {
			log.log(Level.SEVERE, "Error trying to send message", e);
		}
	}

	public void tell(UserRegistry.User user, String message, boolean inGame) {
		if (inGame) {
			if (user != null)
				gameCommand("tell " + user.getGamename() + " " + message, false);
			else
				gameMessage(message);
		} else {
			mucMessage(user.getNickname() + ": " + message);
		}
	}

	@Override
	public void onJoinLeft(String username, boolean joined) {
		log.fine(String.format("'%s %s'", username, (joined ? "joined" : "left")));
		String nickname = REGISTRY.gameToXMPP(username);
		if (mtxMirror) {
			mucMessage(String.format(joined ? language.getString("game.joined", "%s joined. How lucky.") : language.getString("game.left", "%s left. What a relief."), (nickname == null) ? username : String.format("%s (%s)", username, nickname)));
		} else
			log.finer("Event discarded");
	}

	@Override
	public void onMessage(String username, String message) {
		String nick = REGISTRY.gameToXMPP(username);
		Matcher matcher = commandPattern.matcher(message);
		log.fine(String.format("Message : '<%s> %s'", username, message));
		CommandResponse result;
		if (matcher.matches()) {
			result = parseCommand(matcher.group(1), username, Source.GAME);
			if (result.resumeTransmission && mtxMirror)
				mucMessage("<" + (nick == null ? username : nick) + "> " + message);
			if (result.text != null) {
				if (result.user != null)
					tell(result.user, result.text, true);
				else {
					if (result.bothSides)
						mucMessage(result.text);
					gameMessage(result.text);
				}
			}
		} else if (mtxMirror)
			mucMessage("<" + (nick == null ? username : nick) + "> " + message);
		else
			log.finer("Discarding message");
	}

	@Override
	public void onAchievement(String username, String achievement) {
		log.fine(String.format("'%s' earned '%s'", username, achievement));
		String nick = REGISTRY.gameToXMPP(username);
		if (mtxMirror)
			mucMessage(String.format(language.getString("game.achievement", "%s just achieved [%s]. Should I get the cake ?"), nick == null ? username : nick, achievement));
	}

	@Override
	public void onDeath(String username, String death) {
		log.fine(String.format("%s died : %s", username, death));
		if (mtxMirror)
			mucMessage(String.format(language.getString("game.death", "%s %s. That's hilarious."), username, death));
	}

	@Override
	public void onLog(String log) {
		Matcher matcher = uuid.matcher(log);
		if (matcher.matches())
			REGISTRY.registerFromGame(matcher.group(1), matcher.group(2));
		else if (log.startsWith("Done"))
			mucMessage(language.getString("general.started", "And we're online. It's hard to overstate my satisfaction."));
	}

	@Override
	public void onExit() {
		mucMessage(language.getString("general.leaving", "And believe me I am still alive."));
		try {
			Main.getInstance().getSettings().save();
			REGISTRY.save();
			language.save();
			Main.getInstance().getXMPPManager().close();
		} catch (IOException ignore) {
		}
	}

	@Override
	public boolean onConsoleInput(String input) {
		return true;
	}

	@Override
	public void processPacket(Packet packet) throws SmackException.NotConnectedException {
		if (packet.getFrom().equals(JID))
			return;
		if (packet.getExtension("x", "jabber:x:delay") != null) {
			log.fine("Ignored packet, assuming timestamp means backlog : " + packet);
		} else if (packet instanceof Message) {
			Message m = (Message) packet;
			String name = m.getFrom().split("/")[1];
			Matcher matcher = commandPattern.matcher(m.getBody());
			String gamename = REGISTRY.XMPPToGame(name);
			if (matcher.matches()) {
				CommandResponse result = parseCommand(matcher.group(1), name, Source.MUC);
				if (result.resumeTransmission && xtmMirror)
					gameMessage(String.format("<%s> %s", gamename == null ? name : gamename, m.getBody()));
				if (result.text != null)
					if (result.user != null)
						tell(result.user, result.text, false);
					else {
						if (result.bothSides)
							gameMessage(result.text);
						mucMessage(result.text);
					}
			} else if (xtmMirror)
				gameMessage(String.format("<%s> %s", gamename == null ? name : gamename, m.getBody()));
		}
	}

	@Override
	public void processMessage(Chat chat, Message message) {
		String command = message.getBody();
		if (command == null)
			return; // Not dealing with that shit
		Matcher matcher = commandPattern.matcher(command);
		if (matcher.matches())
			command = matcher.group(1);
		CommandResponse response = parseCommand(command, message.getFrom(), Source.PM);
		if (response != null && response.text != null)
			try {
				chat.sendMessage(response.text);
			} catch (XMPPException | SmackException.NotConnectedException ignore) {
			}
	}

	public int getMaxLines() {
		return maxLines;
	}

	@Override
	public void chatCreated(Chat chat, boolean createdLocally) {
		if (!createdLocally)
			chat.addMessageListener(this);
		log.info("Chat " + chat + " created " + (createdLocally ? "locally" : "by remote user"));
		UserRegistry.User u = REGISTRY.getByJID(chat.getParticipant());
		if (u != null)
			openChats.put(u, chat);
	}

	public MultiUserChat getMUC() {
		return muc;
	}

	public Chat getChat(UserRegistry.User u) {
		return openChats.get(u);
	}

	public interface BotCommand {
		public boolean matches(String command);

		public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source);

		public String getName();

		public String getCategory();

		public String getSyntax();

		public int getLevel();

		public boolean isCore();

		public String getHelp();

		public boolean isEnabled();
	}

	public static class CommandResponse {
		private String text;
		private UserRegistry.User user;
		private boolean resumeTransmission, bothSides;

		public CommandResponse(String text, UserRegistry.User user, boolean resumeTransmission, boolean bothSides) {
			this.text = text;
			this.user = user;
			this.resumeTransmission = resumeTransmission;
			this.bothSides = bothSides;
		}

		public CommandResponse() {
			this(null, null, false, false);
		}

		public CommandResponse(String message) {
			this(message, null, false, false);
		}

		public CommandResponse(String message, UserRegistry.User user) {
			this(message, user, false, true);
		}
	}

	public static class CommandException extends RuntimeException {
		private Level logLevel;

		public CommandException(String message, Level logLevel) {
			super(message);
			this.logLevel = logLevel;
		}

		public CommandException(String message, Throwable cause, Level logLevel) {
			super(message, cause);
			this.logLevel = logLevel;
		}

		public CommandException(Throwable cause, Level logLevel) {
			super(cause);
			this.logLevel = logLevel;
		}

		public Level getLogLevel() {
			return logLevel;
		}
	}

	public static class CommandSyntaxException extends CommandException {
		public CommandSyntaxException(String message) {
			super(message, Level.INFO);
		}

		public CommandSyntaxException() {
			this("");
		}
	}

	public static enum Source {
		GAME, MUC, PM
	}
}


abstract class AbstractBotCommand implements ChatBot.BotCommand {
	private int level;
	private String name, syntax, category, help;
	private boolean enabled = true;

	protected AbstractBotCommand(String name, String category, int level, String syntax) {
		this.name = name;
		this.category = category;
		this.level = level;
		this.syntax = syntax;
		this.help = null;
	}

	@Override
	public int getLevel() {
		return level;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getSyntax() {
		return syntax;
	}

	@Override
	public boolean isCore() {
		return category.equalsIgnoreCase("core");
	}

	@Override
	public String getCategory() {
		return category;
	}

	@Override
	public String getHelp() {
		return help;
	}

	public ChatBot.BotCommand setHelp(String help) {
		this.help = help;
		return this; // For method chaining
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}

abstract class RegexBotCommand extends AbstractBotCommand {
	private Pattern regex;
	private Matcher lastMatcher;

	protected RegexBotCommand(String name, String category, int level, String syntax, Pattern regex) {
		super(name, category, level, syntax);
		this.regex = regex;
	}

	protected Matcher getMatcher() {
		return lastMatcher;
	}

	@Override
	public boolean matches(String command) {
		return (lastMatcher = regex.matcher(command)).matches();
	}
}

abstract class PrefixBotCommand extends AbstractBotCommand {
	private String prefix;

	protected PrefixBotCommand(String name, String category, int level, String syntax, String prefix) {
		super(name, category, level, syntax);
		this.prefix = prefix.toLowerCase();
	}

	@Override
	public boolean matches(String command) {
		return command.toLowerCase().startsWith(prefix);
	}
}