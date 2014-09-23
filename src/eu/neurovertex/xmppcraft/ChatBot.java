package eu.neurovertex.xmppcraft;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.muc.MultiUserChat;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static eu.neurovertex.xmppcraft.UserRegistry.*;

/**
 * Central class of the program. Listens for events from Minecraft and XMPP, and acts accordingly. Parses commands when
 * recognized. Commands are dynamically added as a collection of objects subclassing BotCommand. This class is automatically
 * instanciated by Main through XMPPManager and manual instanciation should not happen.
 *
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
	private boolean mtxMirror, xtmMirror;
	private long captureDelay = 1000;
	private int maxLen = 1024, maxLines = 16;
	private Map<String, BotCommand> commands = new HashMap<>();
	private Map<UserRegistry.User, Chat> openChats = new HashMap<>();
	private MultiUserChat muc;
	private String JID;
	private Exception lastException;

	ChatBot() throws SmackException, XMPPException {
		this.gameInput = new PrintStream(Main.getInstance().getPipeToStdin());
		roster = Main.getInstance().getXMPPManager().getConnection().getRoster();
		UserCommands.init(this);
		GameCommands.init(this);
		XMPPCommands.init(this);
		initCoreCommands();
		init();
	}

	/**
	 * Initializes the bot and join the designated MultiUser Chat.
	 *
	 * @throws XMPPException	If an XMPP exception happens while joining
	 * @throws SmackException	If any non-XMPP exception happens while joining
	 */
	protected void init() throws SmackException, XMPPException {
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

	/**
	 * Sends a command to Minecraft for execution, optionally capture command output.
	 * @param command The Minecraft command to execute
	 * @param capture Whether the command output should be captured or not. If true, the command will block for {@link eu.neurovertex.xmppcraft.ChatBot#captureDelay} milliseconds (default 500ms)
	 * @return The captured output, or null if false was specified, or if an exception happens.
	 * @see eu.neurovertex.xmppcraft.LogParser#capture(long)
	 */
	public java.util.List<String> gameCommand(String command, boolean capture) {
		gameInput.println(command);
		if (capture)
			try {
				return Main.getInstance().getLogParser().capture(captureDelay);
			} catch (InterruptedException e) {
				e.printStackTrace();
				// Nothing should happen that would get us here
				// In theory
				// Maybe interruping the thread while it's capturing or something
			}
		return null;
	}

	/**
	 * Sends an in-game broadcast message ('say' command)
	 * @param message    Message to broadcast
	 */
	public void gameMessage(String message) {
		log.finest("Sending message : " + message);
		Matcher matcher = charEscape.matcher(message);
		message = matcher.replaceAll("\\\\$0");
		for (String str : message.split("\n"))
			if (str.length() > 0)
				gameCommand("say " + str, false);
	}

	/**
	 * Registers a new command for the bot. No two commands with the same full name can be registered - the latter will
	 * overwrite the former.
	 * @param command    The command to register.
	 * @see eu.neurovertex.xmppcraft.ChatBot.BotCommand
	 */
	public void registerCommand(BotCommand command) {
		commands.put(command.getFullName(), command);
	}

	/**
	 * Registers core and util commands. Those are either too important for the bot to be added externally or need access
	 * to internal variables (like geterror)
	 * List of registered commands :
	 * Core.save, Core.get, Core.reset, Core.version, Core.update, Core.getError,
	 * Utils.help, Utils.reload, Utils.ping, Utils.toggle
	 */
	private void initCoreCommands() {

		String coreCat = "Core", utilsCat = "Utils";

		/*
		Save the setting files.
		 */
		registerCommand(new AbstractBotCommand.PrefixBotCommand("save", coreCat, OP, "save settings|users|language|all", "save ") {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				String parts[] = command.split(" "); // For later
				try {
					boolean all = parts[1].equalsIgnoreCase("all"), settings = parts[1].equalsIgnoreCase("settings"),
							users = parts[1].equalsIgnoreCase("users"), lang = parts[1].equalsIgnoreCase("language");
					if (!(all || settings || users || lang))
						throw new CommandSyntaxException("Unknown parameter "+ parts[1]);
					if (all || settings)
						Main.getInstance().getSettings().save();
					if (all || users)
						REGISTRY.save();
					if (all || lang)
						language.save();
					return new CommandResponse(language.getString("core.save", "There, I saved the settings for you. You lazy organism."));
				} catch (IOException e) {
					throw new CommandException(language.getString("error.save", "Error while saving settings"), e, Level.SEVERE);
				}
			}
		});

		/*
		Retreives the value from a Settings instance (settings or language, users not supported)
		 */
		registerCommand(new AbstractBotCommand.PrefixBotCommand("get", coreCat, ADMIN, "get [file!]<setting name>", "get") {
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

		/*
		Changes the value in a Settings instance. If value already exists and is numerical, input value is parsed as the same type (Double or Integer)
		 */
		registerCommand(new AbstractBotCommand.PrefixBotCommand("set", coreCat, ADMIN, "set [file!]<setting name> <value>", "set") {
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

		/*
		Resets XMPPCraft.
		See Main#reset()
		 */
		registerCommand(new AbstractBotCommand.PrefixBotCommand("reset", coreCat, ADMIN, "reset", "reset") {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				Main.getInstance().reset();
				return new CommandResponse();
			}
		});

		/*
		Displays the current version of the bot from the current Updater instance.
		See Updater#getVersion()
		 */
		registerCommand(new AbstractBotCommand.PrefixBotCommand("version", coreCat, ANON, "version", "version") {
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

		/*
		Updates the classes if a new version is found, or regardless of version if the -f flag is added. If "commands" is
		added, only XMPP|Game|UserCommands are updated
		 */
		registerCommand(new AbstractBotCommand.PrefixBotCommand("update", coreCat, ADMIN, "update [commands] [-f]", "update") {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				try {
					String version = Main.getInstance().getUpdater().getVersion(), newVersion = Main.getInstance().getUpdater().checkVersion();
					boolean commands = (command.contains(" ") && command.split(" ")[1].equalsIgnoreCase("commands")),
							force = (command.contains(" ") && command.split(" ")[commands ? 2 : 1].equalsIgnoreCase("-f"));
					if (!version.equals(newVersion) || force) {
						if (commands) {
							Class<?>[] classes = {GameCommands.class, XMPPCommands.class, UserCommands.class};
							ClassLoader loader = Main.getInstance().getUpdater().update(classes);
							for (Class<?> c : classes) {
								Class<?> newClass = loader.loadClass(c.getName());
								Method method = newClass.getMethod("init", ChatBot.class);
								method.invoke(null, this);
							}
							return new CommandResponse("Successfully reloaded commands");
						} else {
							Main.getInstance().update();
							String message = String.format(language.getString("core.update.found", "Yes I found your new thing, %s. Give me a minute"), newVersion);
							mucMessage(message);
							gameMessage(message);
							muc.leave();
							return new CommandResponse();
						}
					} else
						return new CommandResponse(language.getString("core.update.none", "There's nothing there. Are you genuinely doing that just to waste my time ?"), null);
				} catch (SmackException.NotConnectedException | ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | IOException e) {
					throw new CommandException("Error while checking version", e, Level.SEVERE);
				} catch (NoSuchMethodError e) {
					throw new CommandException("Error while reloading commands", e, Level.SEVERE);
				}
			}
		});

		/*
		Displays the last error class and message, then clears its value.
		 */
		registerCommand(new AbstractBotCommand.PrefixBotCommand("geterror", coreCat, OP, "what was that ?", "what was that") {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				if (lastException == null)
					return new CommandResponse(language.getString("core.lasterror.null", "What was what ? There's nothing in my logs."));
				else {
					CommandResponse response = new CommandResponse(String.format(language.getString("core.lasterror", "Last error was %s : %s"),
							lastException.getClass().getName(),
							lastException.getMessage() != null ? lastException.getMessage() : "no message attached"));
					lastException = null;
					return response;
				}
			}
		});

		/*
		Without argument, displays all commands the issuer may access, with their syntax and optionally their help message.
		 */
		registerCommand(new AbstractBotCommand.PrefixBotCommand("help", utilsCat, ANON, "help [command]", "help") {
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
					if (cmd == null || issuer.getLevel() < cmd.getLevel() || !cmd.isEnabled()) {
						for (BotCommand c : commands.values())
							if (c.getName().equalsIgnoreCase(parts[1]) && issuer.getLevel() >= c.getLevel() && c.isEnabled())
								cmd = c;
					}
					if (cmd == null || issuer.getLevel() < cmd.getLevel() || !cmd.isEnabled())
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

		/*
		Reloads a particular Settings instance from disk, discarding modifications since the last save() if any
		 */
		registerCommand(new AbstractBotCommand.PrefixBotCommand("reload", utilsCat, ADMIN, "reload <settings|language|users>", "reload") {
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

		/*
		Pong. Is also the default method for empty command strings.
		 */
		registerCommand(new AbstractBotCommand.RegexBotCommand("ping", utilsCat, ANON, "ping|?", Pattern.compile("(ping|\\?|\\s*)")) {
			@Override
			public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source) {
				return new CommandResponse("Yes ?");
			}
		});

		/*
		Toggles the mirroring of one chat to the other according to the given argument (Minecraft-to-XMPP or the other
		way around). Note that this command actually captures "enable" and "disable", not "toggle"
		 */
		registerCommand(new AbstractBotCommand.RegexBotCommand("toggle", utilsCat, OP, "enable|disable <MtX|XtM>", Pattern.compile("(enable|disable) .*")) {
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
		}.setHelp("Toggles the chat mirroring, Minecraft-to-XMPP (MtX) or the other way around (XtM)"));
	}

	/**
	 * Looks for a command matching the input string, checks if the user is allowed to execute it, and executes it.
	 * @param command    The input string
	 * @param issuer     The issuing user. This string is parsed as a Minecraft username, MUC nickname or JID depending
	 *                   on the value of <code>source</code>
	 * @param source     Source of the command. Game, MUC or private message
	 * @return	The CommandResponse, which contains the text returned by the command as well as a few additional flags.
	 * @see eu.neurovertex.xmppcraft.ChatBot.BotCommand
	 * @see eu.neurovertex.xmppcraft.ChatBot.CommandResponse
	 */
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

		/*
		Bypass: static command displaying the identifying string as well as the resolved privilege rank of the issuer,
		mostly for debugging purposes. (BotCommand's don't have access to this string, are only given the resolved user).
		 */
		if (command.toLowerCase().startsWith("who am i"))
			return new CommandResponse(String.format(language.getString("general.whoami", "You are %s, %s (%d). How do you manage to forget that ?"), issuer, (level >= 0 ? UserRegistry.RANK_NAMES.get(level) : "Anon"), level));
		log.info(String.format("Parsing %s command : <%s> '%s'", source == Source.GAME ? "game" : "XMPP", issuer, command));

		/*
		If the command is sent from a MUC to the bot, from someone who is in its roster, it'll check the online status
		of the user, as nicknames aren't a save way to identify users.
		 */
		if ((source == Source.MUC && user != null && user.getJid() != null && roster.contains(user.getJid())))
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
						return new CommandResponse("You messed up the syntax. You incapable. "+ e.getMessage() +"\n"+ cmd.getSyntax());
					} catch (Exception e) {
						lastException = e;
						log.log((e instanceof CommandException) ? ((CommandException)e).getLogLevel() : Level.SEVERE, "Error while executing command", e);
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

	/**
	 * Sends a message to the MUC
	 * @param message    Message to send
	 */
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

	/**
	 * Sends a private message to a user ('tell' command). Cur
	 * @param user       User to send the message to
	 * @param message    Message to send
	 * @param inGame     Send the message in Minecraft. If false, send it on XMPP. Currently only Minecraft is supported,
	 *                   as it is never acutally used anyway
	 */
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

	/**
	 * Mirrors (if {@link #mtxMirror}) Join/Left notifications to XMPP
	 * @param username    In-game name of the player
	 * @param joined      True if joined, false if left
	 */
	@Override
	public void onJoinLeft(String username, boolean joined) {
		log.fine(String.format("'%s %s'", username, (joined ? "joined" : "left")));
		String nickname = REGISTRY.gameToXMPP(username);
		if (mtxMirror) {
			mucMessage(String.format(joined ? language.getString("game.joined", "%s joined. How lucky.") : language.getString("game.left", "%s left. What a relief."), (nickname == null) ? username : String.format("%s (%s)", username, nickname)));
		} else
			log.finer("Event discarded");
	}

	/**
	 * Mirrors (if {@link #mtxMirror}) chat messages to XMPP. Parses commands if the content matches
	 * @param username    In-game name of the player
	 * @param message     Content of the message
	 */
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

	/**
	 * Mirrors (if {@link #mtxMirror}) achievement notifications to XMPP
	 * @param username       In-game name of the player
	 * @param achievement    Name of the earned achievement
	 */
	@Override
	public void onAchievement(String username, String achievement) {
		log.fine(String.format("'%s' earned '%s'", username, achievement));
		String nick = REGISTRY.gameToXMPP(username);
		if (mtxMirror)
			mucMessage(String.format(language.getString("game.achievement", "%s just achieved [%s]. Should I get the cake ?"), nick == null ? username : nick, achievement));
	}

	/**
	 * Mirrors (if {@link #mtxMirror}) death messages to XMPP and adds sass
	 * @param username    In-game name of the player
	 * @param death       Death message
	 */
	@Override
	public void onDeath(String username, String death) {
		log.fine(String.format("%s died : %s", username, death));
		if (mtxMirror)
			mucMessage(String.format(language.getString("game.death", "%s %s. That's hilarious."), username, death));
	}

	/**
	 * Parses unrecognized log messages for user UUIDs
	 * @param log    Log message
	 * @see LogParser#run()
	 */
	@Override
	public void onLog(String log) {
		Matcher matcher = uuid.matcher(log);
		if (matcher.matches())
			REGISTRY.registerFromGame(matcher.group(1), matcher.group(2));
		else if (log.startsWith("Done"))
			mucMessage(language.getString("general.started", "And we're online. It's hard to overstate my satisfaction."));
	}

	/**
	 * Stops the current XMPPManager instance and save settings.
	 */
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

	/**
	 * Return true. For future purposes.
	 * @param input    input
	 * @return		true
	 */
	@Override
	public boolean onConsoleInput(String input) {
		return true;
	}

	/**
	 * Processes messages from MUC, execute command if the message matches the command pattern. Any packet that isn't
	 * an instance of Message is ignored.
	 * @param packet    Received packet
	 */
	@Override
	public void processPacket(Packet packet) {
		if (packet.getFrom().equals(JID)) // Ignore messages from self
			return;
		if (packet.getExtension("x", "jabber:x:delay") != null) {
			/*
			THIS IS AN APPROXIMATE IMPLEMENTATION. On the test server, a backlog of 25 messages is set, thus the bot will
			receive old messags upon joining a chatroom, which can cause havok if they contain commands. Sometimes, less
			than 25 will be received for unknown reasons. There is no way to differenciate those from new messages from the
			specs as far as I know, though I've noticed only those messages had a timestamp, so I'm ignoring any message
			with this extension set.
			This implementation is specific to the test server (runs Openfire), and MIGHT not work for yours.
			 */
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

	/**
	 * Processes messages from private chats. Parses and execute commands if the message matches the pattern.
	 * @param chat       Source chat
	 * @param message    Content of the message
	 */
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

	/**
	 * Binds the new chat to the corresponding user, if one is successfully resolved.
	 * @param chat              Created chat
	 * @param createdLocally    Created by bot (currently never happens)
	 */
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

	/**
	 * Describes a command for the bot to execute. Commands are registered through registerCommand and executed through
	 * parseCommand. Each command has a category and name that is unique in its category. Commands are mapped from their
	 * full name (category.name) in the ChatBot command list.
	 * @see #registerCommand(eu.neurovertex.xmppcraft.ChatBot.BotCommand)
	 * @see #parseCommand(String, String, eu.neurovertex.xmppcraft.ChatBot.Source)
	 */
	public interface BotCommand {
		public boolean matches(String command);

		public CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, Source source);

		public String getName();

		public String getFullName();

		public String getCategory();

		/**
		 * A string description of the syntax of the command
		 * @return	The command syntax
		 */
		public String getSyntax();

		/**
		 * Privilege level required to execute the command.
		 * @return	The minimum level
		 */
		public int getLevel();

		/**
		 * An optional string describing the purpose of the command
		 * @return	A description of the command's effect
		 */
		public String getHelp();

		/**
		 * Returns whether the command is enabled.
		 * @return	Whether the command is enabled. Documenting getters can get somewhat repetitive can't it.
		 */
		public boolean isEnabled();
	}

	/**
	 * Represents the return value of a BotCommand. Main attribute is the text to be displayed back to the user.
	 */
	public static class CommandResponse {
		/**
		 * Text to be sent back to the user.
		 */
		private String text;
		/**
		 * User to send the text to. Automatically sets it to a private message.
		 */
		private UserRegistry.User user;
		/**
		 * Whether the original message containing the command should be mirrored to the other chat or not
		 */
		private boolean resumeTransmission;
		/**
		 * Whether the returned text should be displayed on both chats or not
		 */
		private boolean bothSides;

		public CommandResponse(String text, UserRegistry.User user, boolean resumeTransmission, boolean bothSides) {
			this.text = text;
			this.user = user;
			this.resumeTransmission = resumeTransmission;
			this.bothSides = bothSides;
		}

		/**
		 * Creates an empty response. Nothing will be displayed on either side.
		 */
		public CommandResponse() {
			this(null, null, false, false);
		}

		/**
		 * Create a text response that will be displayed on both side.
		 * @param message    Message to return
		 */
		public CommandResponse(String message) {
			this(message, null, true, true);
		}

		/**
		 * Creates a private text response
		 * @param message    Message to return
		 * @param user       User to send the response to
		 */
		public CommandResponse(String message, UserRegistry.User user) {
			this(message, user, false, true);
		}
	}

	/**
	 * Wrapper for exceptions during a BotCommand execution
	 */
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

	/**
	 * Thrown whenever a command failed due to bad syntax. {@link #parseCommand(String, String, eu.neurovertex.xmppcraft.ChatBot.Source)}
	 * will automatically display the syntax of the command whenever it's caught.
	 */
	public static class CommandSyntaxException extends CommandException {
		public CommandSyntaxException(String message) {
			super(message, Level.INFO);
		}

		public CommandSyntaxException() {
			this("");
		}
	}

	/**
	 * Describes the source of the command
	 */
	public static enum Source {
		/**
		 * Self-explanatatory. Seriously why am I even documenting that one.
		 */
		GAME,
		/**
		 * Multi-User Chat
		 */
		MUC,
		/**
		 * Private Message
		 */
		PM
	}
}

