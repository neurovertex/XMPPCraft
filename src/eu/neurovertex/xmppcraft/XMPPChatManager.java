package eu.neurovertex.xmppcraft;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.spark.util.DummyTrustManager;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.Closeable;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * @author Neurovertex
 *         Date: 13/09/2014, 13:22
 */
public class XMPPChatManager implements ConnectionListener, Closeable {
	private XMPPConnection connection;
	private ChatBot bot;
	private final String host, login, password, resource, status;
	private final int port, priority;

	public XMPPChatManager() {
		Settings settings = Main.getInstance().getSettings();
		host = settings.getString("xmpp.server.host");
		port = settings.getInteger("xmpp.server.port", 5222);
		login = settings.getString("xmpp.user");
		password = settings.getString("xmpp.password");
		resource = settings.getString("xmpp.resource", "XMPPCraft");
		status = settings.getString("xmpp.status", "Online");
		priority = settings.getInteger("xmpp.priority", 10);
	}

	public void start() throws KeyManagementException, NoSuchAlgorithmException, IOException, XMPPException, SmackException {
		ConnectionConfiguration configuration = new ConnectionConfiguration(host, port, host);
		configuration.setSecurityMode(ConnectionConfiguration.SecurityMode.required);
		SSLContext context = SSLContext.getInstance("TLS");
		context.init(null, new TrustManager[]{new DummyTrustManager()}, new SecureRandom());
		configuration.setCustomSSLContext(context);
		configuration.setSendPresence(false);
		connection = new XMPPTCPConnection(configuration);
		connection.addConnectionListener(this);
		connection.connect();
		connection.login(login, password, resource);
		connection.sendPacket(new Presence(Presence.Type.available, status, priority, Presence.Mode.available));
	}

	public ChatBot createBot() throws SmackException.NotConnectedException, XMPPException, SmackException.NoResponseException {
		bot = new ChatBot();
		ChatManager.getInstanceFor(connection).addChatListener(bot);
		return bot;
	}

	public ChatBot getBot() {
		return bot;
	}

	public XMPPConnection getConnection() {
		return connection;
	}

	@Override
	public void connected(XMPPConnection connection) {
		System.out.println("Successfully connected to the server");
	}

	@Override
	public void authenticated(XMPPConnection connection) {
	}

	@Override
	public void connectionClosed() {
		bot.gameMessage("Disconnected from XMPP server");
	}

	@Override
	public void connectionClosedOnError(Exception e) {
		e.printStackTrace();
	}

	@Override
	public void reconnectingIn(int seconds) {
	}

	@Override
	public void reconnectionSuccessful() {
		System.err.println("XMPP reconnected");
	}

	@Override
	public void reconnectionFailed(Exception e) {
		e.printStackTrace();
	}

	@Override
	public void close() {
		try {
			connection.disconnect();
		} catch (SmackException.NotConnectedException ignore) {}
	}
}
