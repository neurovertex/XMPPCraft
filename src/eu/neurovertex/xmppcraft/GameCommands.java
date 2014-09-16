package eu.neurovertex.xmppcraft;

import com.google.common.base.Joiner;

import java.io.IOException;
import java.util.List;

import static eu.neurovertex.xmppcraft.UserRegistry.*;

/**
 * @author Neurovertex
 *         Date: 14/09/2014, 15:34
 */
public final class GameCommands {
	private GameCommands() {
	}

	public static void init(ChatBot bot) {
		String category = "Game";

		bot.registerCommand(new PrefixBotCommand("$", category, OP, "", "$") {
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

		bot.registerCommand(new PrefixBotCommand("say", category, USER+2, "say <message>", "say") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				bot.gameMessage(command.substring(command.indexOf(" ")+1));
				if (source != ChatBot.Source.GAME)
					return new ChatBot.CommandResponse("[server] "+ command.substring(command.indexOf(" ")+1));
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

		bot.registerCommand(new PrefixBotCommand("list", category, ANON, "list", "list") {
			@Override
			public ChatBot.CommandResponse execute(ChatBot bot, UserRegistry.User issuer, String command, ChatBot.Source source) {
				return new ChatBot.CommandResponse(Joiner.on("\n").join(bot.gameCommand("list", true)));
			}
		});
	}
}
