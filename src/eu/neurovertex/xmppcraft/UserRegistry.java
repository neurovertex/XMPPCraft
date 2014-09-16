package eu.neurovertex.xmppcraft;


import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Neurovertex
 *         Date: 13/09/2014, 18:03
 */
public enum UserRegistry {
	REGISTRY; // Singleton
	public static final int ANON = -1, USER = 0, ADMIN = 5, OP = 3;
	public static final List<String> RANK_NAMES = Collections.unmodifiableList(Arrays.asList("User", "User+", "User++", "Operator", "Operator+", "Admin"));

	private List<User> users = new ArrayList<>();
	private Settings database = new Settings("users.json", true);
	private final Logger log = Logger.getLogger(UserRegistry.class.getName());


	public User registerFromGame(String username, String uuid) {
		User u = getByGamename(username);
		log.finer(String.valueOf(u));
		if (u != null) {
			u.uuid = uuid;
		} else {
			u = new User(username, uuid);
			u.gamename = username;
		}
		save();
		return u;
	}

	public User registerFromXMPP(String nickname) {
		User u = getByNickname(nickname);
		if (u == null)
			u = new User(nickname);
		save();
		return u;
	}

	public void deleteUser(User user) {
		user.unregister();
		save();
	}

	public List<User> getUsers() {
		return Collections.unmodifiableList(users);
	}

	public void setJID(User user, String jid) {
		user.jid = jid;
		save();
	}

	public void link(String gamename, String nickname) {
		User xmpp = getByNickname(nickname), game = getByGamename(gamename);
		if (xmpp == null && game == null) {
			game = new User(gamename, null);
			game.setNickname(nickname);
		} else if (game == null) {
			xmpp.setGamename(gamename);
		} else if (xmpp == null) {
			game.setNickname(nickname);
		} else {
			xmpp.unregister();
			xmpp.gamename = gamename; // Just in case the object is cached anywhere. Which it shouldn't be. But just to be sure.
			xmpp.uuid = game.uuid;
			game.setNickname(nickname);
			game.jid = xmpp.jid;
		}
		log.info("Linked player "+ gamename +" <-> user "+ nickname);
		save();
	}

	public User getByNickname(String nick) {
		if (nick.startsWith("§"))
			nick = nick.substring(1);
		for (User u : users)
			if (nick.equalsIgnoreCase(u.nickname))
				return u;
		return null;
	}

	public User getByGamename(String gamename) {
		if (gamename.startsWith("#"))
			gamename = gamename.substring(1);
		for (User u : users)
			if (gamename.equalsIgnoreCase(u.gamename))
				return u;
		return null;
	}

	public User getByJID(String jid) {
		if (jid.contains("/"))
			jid = jid.split("/")[0];
		for (User u : users)
			if (jid.equalsIgnoreCase(u.jid))
				return u;
		return null;
	}

	public User getUser(String name) {
		User u = null;
		if (!name.startsWith("#"))
			u = getByNickname(name);
		if (u == null && !name.startsWith("§"))
			u = getByGamename(name);
		return u;
	}

	/*public Settings getDatabase() {
		return database;
	}*/

	public String gameToXMPP(String gamename) {
		User u = getByGamename(gamename);
		return u == null ? null : u.nickname;
	}

	public void setLevel(User user, int level) {
		user.level = level;
		save();
	}



	public String XMPPToGame(String nickname) {
		User u = getByNickname(nickname);
		return u == null ? null : u.gamename;
	}

	public synchronized void load() throws IOException {
		users.clear();
		database.load();
		Object obj = database.get("users");
		if (obj != null && obj instanceof List) {
			List users = (List) obj;
			log.fine(users.size() + " users in JSON");
			for (Object o : users) {
				Map map = (Map) o;

				User u = new User();
				u.nickname = (String) map.get("nickname");
				u.gamename = (String) map.get("gamename");
				u.jid = (String) map.get("jid");
				u.uuid = (String) map.get("uuid");
				u.level = (int)(double) map.get("level");
				log.fine("Loaded user "+ u);
			}
		} else
			log.fine("Object is not a list : "+ String.valueOf(obj));
		log.info("Loaded "+ users.size() +" users");
	}

	public synchronized void save() {
		database.put("users", users);
		try {
			database.save();
		} catch (IOException e) {
			log.log(Level.SEVERE, "Error while saving database", e);
		}
	}

	public class User {
		private String nickname, gamename, jid, uuid;
		private int level = 0;

		private User() {
			users.add(this);
		}

		private User(String nickname) {
			this();
			setNickname(nickname);
		}

		private User(String gamename, String uuid) {
			this();
			this.uuid = uuid;
			setGamename(gamename);
		}

		public String getNickname() {
			return nickname;
		}

		private void setNickname(String nickname) {
			this.nickname = nickname;
			log.info("Registered nickname "+ nickname);
		}

		public String getGamename() {
			return gamename;
		}

		private void setGamename(String gamename) {
			this.gamename = gamename;
			log.info("Registered gamename " + gamename);
		}

		public String getJid() {
			return jid;
		}

		public String getUuid() {
			return uuid;
		}

		private void unregister() {
			users.remove(this);
		}

		@Override
		public String toString() {
			return String.format("<game:%s,xmpp:%s>", gamename, nickname);
		}

		public int getLevel() {
			return level;
		}
	}
}
