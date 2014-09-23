package eu.neurovertex.xmppcraft;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static eu.neurovertex.xmppcraft.UserRegistry.ADMIN;
import static eu.neurovertex.xmppcraft.UserRegistry.OP;

/**
 * @author Neurovertex
 *         Date: 15/09/2014, 02:20
 */
public final class XMPPCommands {
	private XMPPCommands(){}

	public static void init(ChatBot bot) {
		String category = "XMPP";

		bot.registerCommand(new AbstractBotCommand.PrefixBotCommand("leave", category, OP, "leave", "leave") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				try {
					bot.mucMessage("Leaving");
					bot.getMUC().leave();
					bot.gameMessage("MUC left");
				} catch (SmackException.NotConnectedException e) {
					throw new ChatBot.CommandException(e, Level.WARNING);
				}
				return new ChatBot.CommandResponse();
			}
		});

		bot.registerCommand(new AbstractBotCommand.PrefixBotCommand("rejoin", category, OP, "rejoin", "rejoin") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				bot.mucMessage("Updating MUC settings");
				try {
					XMPPConnection connection = Main.getInstance().getXMPPManager().getConnection();
					if (!connection.isConnected())
						connection.connect();
					if (bot.getMUC().isJoined())
						bot.getMUC().leave();
					bot.init();
					if (bot.getMUC().isJoined()) {
						bot.gameMessage("MUC joined");
						return new ChatBot.CommandResponse();
					} else
						throw new ChatBot.CommandException("MUC not joined", Level.WARNING);
				} catch (XMPPException | SmackException.NoResponseException | SmackException.NotConnectedException e) {
					throw new ChatBot.CommandException("Error while rejoining", e, Level.SEVERE);
				} catch (SmackException | IOException e) {
					throw new ChatBot.CommandException("Error while connecting", e, Level.SEVERE);
				}
			}
		});

		bot.registerCommand(new AbstractBotCommand.PrefixBotCommand("log", category, ADMIN, "log [level]", "log ") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				if (source != ChatBot.Source.PM)
					return new ChatBot.CommandResponse("Can only run this command from PM");
				String parts[] = command.split(" ");
				Level level = Level.INFO;
				try {
					if (parts.length > 1 && parts[1].length() > 0)
						level = Level.parse(parts[1].toUpperCase());
				} catch (IllegalArgumentException e) {
					throw new ChatBot.CommandException("Level unrecognized : "+ parts[1], Level.WARNING);
				}
				Chat chat = bot.getChat(issuer);
				if (chat == null)
					return new ChatBot.CommandResponse("Can't find chat");
				ChatHandler handler = ChatHandler.getHandler(chat);
				if (handler == null) {
					handler = new ChatHandler(chat);
					Logger.getLogger(Main.class.getPackage().getName()).addHandler(handler);
				}
				handler.setLevel(level);
				return new ChatBot.CommandResponse("Set logging to "+ level.getName());
			}
		}.setHelp("Mirrors log to private chat"));

		bot.registerCommand(new AbstractBotCommand.PrefixBotCommand("logoff", category, ADMIN, "logoff", "logoff") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				Chat chat = bot.getChat(issuer);
				if (chat == null)
					return new ChatBot.CommandResponse("Can't find chat");
				ChatHandler handler = ChatHandler.getHandler(chat);
				if (handler == null)
					return new ChatBot.CommandResponse("No registered handler");
				Logger.getLogger(Main.class.getPackage().getName()).removeHandler(handler);
				handler.close();
				return new ChatBot.CommandResponse("Disabled logging");
			}
		}.setHelp("Disables log mirroring"));
	}
}
