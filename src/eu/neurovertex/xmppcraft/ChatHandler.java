package eu.neurovertex.xmppcraft;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.*;

/**
 * Adds a chat as a logging handler, for live debugging purpose.
 * @author Neurovertex
 *         Date: 15/09/2014, 02:28
 */
public class ChatHandler extends Handler {
	private static final String packageName = Main.class.getPackage().getName();
	private static final Map<Chat, ChatHandler> map = new HashMap<>();

	private Chat chat;

	public ChatHandler(Chat chat) {
		this.chat = chat;
		setFormatter(new Formatter() {
			@Override
			public String format(LogRecord record) {
				String className = record.getSourceClassName();
				if (className == null)
					className = "Unkown Class";
				else if (className.startsWith(packageName))
					className = className.substring(packageName.length());
				return String.format("%s#%s %s: %s", className, record.getSourceMethodName(), record.getLevel(), record.getMessage());
			}
		});
		map.put(chat, this);
	}

	public static ChatHandler getHandler(Chat c) {
		return map.get(c);
	}

	public static void removeAll() {
		Logger logger = Logger.getLogger(Main.class.getPackage().getName());
		for (ChatHandler handler : new ArrayList<>(map.values())) {
			handler.close();
			logger.removeHandler(handler);
		}
	}

	@Override
	public void publish(LogRecord record) {
		if (!isLoggable(record))
			return;
		String msg;
		try {
			msg = getFormatter().format(record);
		} catch (Exception e) {
			reportError(null, e, ErrorManager.FORMAT_FAILURE);
			return;
		}

		try {
			chat.sendMessage(msg.trim());
		} catch (Exception e) {
			reportError(null, e, ErrorManager.WRITE_FAILURE);
		}
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() throws SecurityException {
		if (Main.getInstance().getXMPPManager().getConnection().isConnected())
			try {
				chat.sendMessage("Closing ChatHandler");
			} catch (XMPPException | SmackException.NotConnectedException e) {
				reportError(null, e, ErrorManager.WRITE_FAILURE);
			}
		map.remove(chat);
	}
}
