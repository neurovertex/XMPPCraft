package eu.neurovertex.xmppcraft;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Neurovertex
 *         Date: 12/09/2014, 15:57
 */
public class StreamCopier implements Runnable, Closeable {
	private static final Logger log = Logger.getLogger(StreamCopier.class.getName());
	private final InputStream in;
	private final OutputStream out;
	private List<GameListener> listeners = new ArrayList<>();
	private Thread thread;

	public StreamCopier(InputStream in, OutputStream out) throws IOException {
		this.in = in;
		this.out = out;
	}

	public void addConsoleListener(GameListener listener) {
		listeners.add(listener);
	}

	@Override
	public void run() {
		String line;
		BufferedReader in = new BufferedReader(new InputStreamReader(this.in));
		PrintStream out = new PrintStream(this.out);
		try {
			while ((line = in.readLine()) != null) {
				boolean copy = true;
				for (GameListener listener : listeners)
					copy &= listener.onConsoleInput(line);
				if (copy)
					synchronized (StreamCopier.this) {
						out.println(line);
					}
			}
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error in the StreamCopy thread", e);
		}
		log.fine("Exitting StreamCopier thread");
	}

	public void start() {
		if (thread != null)
			throw new IllegalStateException("Already started");
		thread = new Thread(this);
		thread.setDaemon(true);
		thread.setName("InputStream copy");
		thread.start();
	}

	@Override
	public void close() {
		thread.interrupt();
	}

}
