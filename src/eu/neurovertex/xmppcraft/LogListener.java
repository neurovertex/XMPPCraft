package eu.neurovertex.xmppcraft;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Neurovertex
 *         Date: 13/09/2014, 13:17
 */
public class LogListener implements GameListener {
	private final Logger log = Logger.getLogger(LogListener.class.getName());
	private final Pattern uuid = Pattern.compile("UUID of player ([^ ]+) is (.+)");

	public LogListener() {

	}

	@Override
	public void onJoinLeft(String username, boolean joined) {
		log.info("LogChatListener: \"" + username + "\" " + (joined ? "joined" : "left"));
	}

	@Override
	public void onMessage(String username, String message) {
		log.fine("LogChatListener: <" + username + "> " + message);
	}

	@Override
	public void onAchievement(String username, String achievement) {
		log.info("LogChatListener: \"" + username + "\" earned the achievement : [" + achievement + "]");
	}

	@Override
	public void onDeath(String username, String death) {
		log.info("LogChatListener: \"" + username + "\" died : " + death);
	}

	@Override
	public void onLog(String msg) {
		Matcher matcher = uuid.matcher(msg);
		if (matcher.matches()) {
			log.info(matcher.group(1) +" -> "+ matcher.group(2));
		} else
			log.finer("LogChatListener: " + msg);
	}

	@Override
	public void onExit() {

	}

	@Override
	public boolean onConsoleInput(String input) {
		return true;
	}


}
