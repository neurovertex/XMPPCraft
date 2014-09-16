package eu.neurovertex.xmppcraft;

import eu.neurovertex.io.PipeInputStream;
import eu.neurovertex.io.PipeOutputStream;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.*;

public class Main {
	private static final String LOGFILE = "logs/XMPPCraft";
	private static Logger log;
	//private static ThreadGroup threadGroup;
	private static Main INSTANCE;
	private Settings settings = new Settings("settings.json");
	private Updater updater;
	private XMPPChatManager manager;
	private LogParser parser;
	private StreamCopier copier;
	private InputStream oldStdin;
	private PipeInputStream stdoutPipe;
	private PipeOutputStream stdinPipe;
	private List<Closeable> closeables = new ArrayList<>();

	public Main() {
		INSTANCE = this;
	}

	public static void main(String[] args) {
		log = Logger.getLogger(Main.class.getName());
		try {
			// Setting up logging

			Logger packageLog = Logger.getLogger(ChatBot.class.getPackage().getName());
			File logFile = new File(LOGFILE + ".log");
			if (logFile.exists()) {
				Calendar cal = Calendar.getInstance();
				File backup = new File(String.format("%s_%d_%d_%d.log", LOGFILE, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)));
				if (backup.exists())
					try (BufferedReader reader = new BufferedReader(new FileReader(logFile));
						 PrintWriter writer = new PrintWriter(new FileWriter(backup, true))) {
						char buff[] = new char[1024];
						int n;
						while ((n = reader.read(buff)) > 0) {
							writer.write(buff, 0, n);
						}
						log.info("Appened log to backup " + backup.getName());
					} catch (IOException e) {
						log.log(Level.SEVERE, "Couldn't append log to " + backup.getName(), e);
					}
				else {
					try {
						FileUtils.moveFile(logFile, backup);
						log.info("Moved log to backup " + backup.getName());
					} catch (IOException e) {
						log.log(Level.SEVERE, "Couldn't move log to " + backup.getName(), e);
					}
				}
			}

			//noinspection ResultOfMethodCallIgnored
			//logFile.delete();
			Handler handler = new FileHandler(LOGFILE + ".log");
			handler.setFormatter(new SimpleFormatter());
			packageLog.setLevel(Level.FINE);
			packageLog.addHandler(handler);

			// Starting up XMPPCraft
			Main main = new Main();
			PipeInputStream stdinPipe = new PipeInputStream(1048576);
			PipeOutputStream stdoutPipe = new PipeOutputStream();
			main.init(System.in, new PipeOutputStream(stdinPipe), new PipeInputStream(stdoutPipe, 1048576));
			System.setIn(stdinPipe);
			System.setOut(new PrintStream(new TeeOutputStream(System.out, stdoutPipe)));
			main.start();
			// Starting up Minecraft
			MinecraftServer.main(args);
			//DummyMinecraftServer.main(args);
		} catch (KeyManagementException | NoSuchAlgorithmException | SmackException | XMPPException | IOException e) {
			String filename = String.format("crashreport_%s_%s.log", e.getClass().getSimpleName(),
					new SimpleDateFormat("MM_dd.HH_mm").format(new Date()));
			File f = new File(filename);
			try {
				if (f.createNewFile()) {
					PrintWriter out = new PrintWriter(f);
					out.println("Error on startup :");
					out.printf("JVM: %s %s on %s\n", System.getProperty("java.vm.name"),
							System.getProperty("java.runtime.version"),
							System.getProperty("os.name"));
					e.printStackTrace(out);
					out.flush();
					out.close();
				}
			} catch (IOException ignore) {} // lol what can you do
			log.severe("Crash detected. Generating report.");
		}
	}

	public static Main getInstance() {
		return INSTANCE;
	}


	public void init(InputStream oldStdin, PipeOutputStream stdin, PipeInputStream stdout) throws IOException {
		this.stdoutPipe = stdout;
		this.stdinPipe = stdin;
		this.oldStdin = oldStdin;
		settings.load();
		updater = new Updater();
		UserRegistry.REGISTRY.load();
		manager = new XMPPChatManager();
		copier = new StreamCopier(oldStdin, stdinPipe);
		parser = new LogParser(stdoutPipe);
	}

	public void start() throws IOException, XMPPException, NoSuchAlgorithmException, SmackException, KeyManagementException {
		manager.start();
		copier.start();
		parser.start();

		closeables.add(manager);
		closeables.add(copier);
		closeables.add(parser);
		//parser.addGameChatListener(new LogChatListener());
		parser.addGameChatListener(manager.createBot());
		copier.addConsoleListener(manager.getBot());
	}

	public void stop() {
		for (Closeable c : closeables)
			try {
				c.close();
			} catch (IOException ignore) {
			}

		ChatHandler.removeAll();
	}

	public boolean update() throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		ClassLoader newLoader = updater.update();
		if (newLoader != null) {
			Class<?> cl = newLoader.loadClass(Main.class.getName());

			Object newMain;
			Method init = cl.getMethod("init", InputStream.class, PipeOutputStream.class, PipeInputStream.class), start = cl.getMethod("start");
			try {
				newMain = cl.newInstance();
				init.invoke(newMain, oldStdin, stdinPipe, stdoutPipe);
				stop();
				start.invoke(newMain);
				return true;
			} catch (InstantiationException ignored) {
			}
		}
		return false;
	}

	public Settings getSettings() {
		return settings;
	}

	public Updater getUpdater() {
		return updater;
	}

	public XMPPChatManager getXMPPManager() {
		return manager;
	}

	public LogParser getLogParser() {
		return parser;
	}

	public void reset() {
		Main main = new Main();
		try {
			main.init(oldStdin, stdinPipe, stdoutPipe);
			stop();
			try {
				main.start();
			} catch (XMPPException | NoSuchAlgorithmException | SmackException | KeyManagementException e) {
				e.printStackTrace();
				System.err.println("Failed to start up new main. Trying to revert previous one.");
				try {
					start();
				} catch (XMPPException | NoSuchAlgorithmException | SmackException | KeyManagementException e1) {
					e1.printStackTrace();
					System.err.println("Failed to revert. Shutting down.");
				}
			}
		} catch (IOException ignore) {
		}
	}

	public PipeOutputStream getPipeToStdin() {
		return stdinPipe;
	}
}
