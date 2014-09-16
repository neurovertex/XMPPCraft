package eu.neurovertex.xmppcraft;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Neurovertex
 *         Date: 12/09/2014, 13:49
 */
public class LogParser implements Runnable, Closeable {
	private static final Logger log = Logger.getLogger(LogParser.class.getName());
	private static final String logPrefix = "\\[.*\\] \\[.*/INFO\\]: ";
	private static final Pattern logMessage = Pattern.compile(logPrefix +"(.*)"),
								message = Pattern.compile(logPrefix+ "<(.+)> (.+)"),
								join = Pattern.compile(logPrefix+ "([^ ]+) (joined|left) the game"),
								achievement = Pattern.compile(logPrefix+ "([^ ]+) has just earned the achievement \\[(.+)\\]"),
								death = Pattern.compile(logPrefix+ "([^ ]+) ((was|walked|drowned|blew|hit|fell|went|burned|got|tried|died|starved|suffocated|withered).+)"),
								exit = Pattern.compile(logPrefix+ "Stopping the server");
	private final List<GameListener> listeners = new ArrayList<>();
	private List<String> captureBuffer;
	private InputStream in;
	private boolean stop = true;
	private Thread listenerThread, readThread;

	private Queue<String> fifo = new LinkedList<>();

	public LogParser(InputStream in) {
		this.in = in;
	}

	@Override
	public void run() {
		synchronized (this) {
			String line;

			try {
				do {
					while (fifo.size() == 0 && !stop)
						this.wait();
					if (stop)
						return;
					line = fifo.poll();
					if (line == null) {
						System.err.println("Empty queue");
						System.exit(0);
					}
					List<GameListener> listeners = new ArrayList<>(this.listeners); // Avoid side effects if it gets changed during the execution
					Matcher matcher;
					if ((matcher = message.matcher(line)).matches()) {
						String name = matcher.group(1), body = matcher.group(2);
						for (GameListener listener : listeners)
							listener.onMessage(name, body);
					} else if ((matcher = join.matcher(line)).matches()) {
						String name = matcher.group(1), body = matcher.group(2);
						for (GameListener listener : listeners)
							listener.onJoinLeft(name, body.equals("joined"));
					} else if ((matcher = achievement.matcher(line)).matches()) {
						String name = matcher.group(1), body = matcher.group(2);
						for (GameListener listener : listeners)
							listener.onAchievement(name, body);
					} else if ((matcher = death.matcher(line)).matches()) {
						String name = matcher.group(1), body = matcher.group(2);
						for (GameListener listener : listeners)
							listener.onDeath(name, body);
					} else if (exit.matcher(line).matches()) {
						for (GameListener listener : listeners)
							listener.onExit();
					} else {
						if ((matcher = logMessage.matcher(line)).matches()) {
							line = matcher.group(1);
							if (captureBuffer != null) {
								captureBuffer.add(line);
							}
							for (GameListener listener : listeners)
								listener.onLog(line);
						}
					}
				} while (true);
			} catch (InterruptedException ignored) {
			}
		}
	}

	public void start() {
		if (!stop)
			throw new IllegalStateException("Already started");
		stop = false;
		readThread = new Thread(new StreamReader());
		readThread.setDaemon(true);
		readThread.setName("LogParser Stream Reader");
		readThread.start();
		listenerThread = new Thread(this);
		listenerThread.setDaemon(true);
		listenerThread.setName("LogParser Listener Thread");
		listenerThread.start();
	}

	@Override
	public void close() throws IOException {
		stop = true;
		readThread.interrupt();
		listenerThread.interrupt();
	}

	public void addGameChatListener(GameListener l) {
		listeners.add(l);
	}

	public void clearGameChatListeners() {
		listeners.clear();
	}

	public List<String> capture(long millis) throws InterruptedException {
		synchronized (this) {
			captureBuffer = new ArrayList<>(32);
		}
		Thread.sleep(millis);
		List<String> result;
		synchronized (this) {
			result = captureBuffer;
			captureBuffer = null;
		}
		return result;
	}

	private class StreamReader extends Thread {
		@Override
		public void run() {
			String line;
			BufferedReader in = new BufferedReader(new InputStreamReader(LogParser.this.in));
			try {
				while ((line = in.readLine()) != null && !stop) {
					if (stop)
						return;
					synchronized (LogParser.this) {
						fifo.add(line);
						LogParser.this.notifyAll();
					}
				}
			} catch (Exception e) {
				log.log(Level.WARNING, "Exception in StreamReader", e);
			}
			log.fine("Exitting LogParser thread");
		}
	}
}
