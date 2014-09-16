package eu.neurovertex.xmppcraft;

/**
 * @author Neurovertex
 *         Date: 12/09/2014, 13:49
 */
public interface GameListener {
	public void onJoinLeft(String username, boolean joined);
	public void onMessage(String username, String message);
	public void onAchievement(String username, String achievement);
	public void onDeath(String username, String death);
	public void onLog(String log);
	public void onExit();
	public boolean onConsoleInput(String input);
}
