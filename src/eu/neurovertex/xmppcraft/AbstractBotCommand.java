package eu.neurovertex.xmppcraft;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convenience skeletal implementation of BotCommand. Implements all of the interface's methods except execute and matches.
 * Commands that need a prefix or regex-based matches() can subclass PrefixBotCommand or RegexBotCommand.
 */
public abstract class AbstractBotCommand implements ChatBot.BotCommand {
	private int level;
	private String name, syntax, category, help;
	private boolean enabled = true;

	/**
	 * Creates a BotCommand
	 * @param name        Name of the command
	 * @param category    Category of the command
	 * @param level       Minimum privilege required
	 * @param syntax      Syntax of the command
	 */
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
	public String getFullName() {
		return String.format("%s.%s", category, name);
	}

	@Override
	public String getSyntax() {
		return syntax;
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


	/**
	 * Convenience skeletal implementation of BotCommand. Implements a command matching a given regex.
	 */
	public abstract static class RegexBotCommand extends AbstractBotCommand {
		private Pattern regex;
		private Matcher lastMatcher;

		/**
		 * Creates a RegexBotCommand matching the given regex
		 * @param name        Name of the command
		 * @param category    Category of the command
		 * @param level       Minimum privilege required
		 * @param syntax      Syntax of the command
		 * @param regex       Regex to match to recognize the command
		 */
		RegexBotCommand(String name, String category, int level, String syntax, Pattern regex) {
			super(name, category, level, syntax);
			this.regex = regex;
		}

		/**
		 * Allow implementing classes to obtain the last matcher, thus the one used by the current command execution.
		 * Allows for capture groups to be extracted.
		 * @return	The last matcher created
		 */
		protected Matcher getMatcher() {
			return lastMatcher;
		}

		@Override
		public boolean matches(String command) {
			return (lastMatcher = regex.matcher(command)).matches();
		}
	}

	/**
	 * Convenience skeletal implementation of BotCommand. Implements a command matching a given prefix.
	 */
	public abstract static class PrefixBotCommand extends AbstractBotCommand {
		private String prefix;

		/**
		 * Creates a PrefixBotCommand matching the given prefix
		 * @param name        Name of the command
		 * @param category    Category of the command
		 * @param level       Minimum privilege required
		 * @param syntax      Syntax of the command
		 * @param prefix      Substring commands have to start with to match this command.
		 */
		PrefixBotCommand(String name, String category, int level, String syntax, String prefix) {
			super(name, category, level, syntax);
			this.prefix = prefix.toLowerCase();
		}

		@Override
		public boolean matches(String command) {
			return command.toLowerCase().startsWith(prefix);
		}
	}
}
