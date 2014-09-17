package eu.neurovertex.xmppcraft.nbtparser;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Neurovertex
 *         Date: 16/09/2014, 20:01
 */
public class PlayerData {
	private long lastUpdate;
	private NBTParser parser;
	private String gamename;
	private File file;
	private int x, y, z;
	private static final Logger log = Logger.getLogger(PlayerData.class.getName());

	public PlayerData(File file) throws IOException {
		this.file = file;
		update();
	}

	public void update() throws IOException {
		lastUpdate = file.lastModified();
		parser = NBTParser.parseFile(file);
		NBTParser.CompoundTag root = parser.getRootTag();
		try {
			List<NBTParser.Tag> pos = ((NBTParser.ListTag) root.get("Pos")).getList();
			x = ((NBTParser.NumberTag)pos.get(0)).getValue().intValue();
			y = ((NBTParser.NumberTag)pos.get(1)).getValue().intValue();
			z = ((NBTParser.NumberTag)pos.get(2)).getValue().intValue();
		} catch (NullPointerException e) {
			log.log(Level.SEVERE, "Error while parsing player data file "+ gamename, e);
		}
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getZ() {
		return z;
	}

	public NBTParser getParser() {
		return parser;
	}

	public long getUpdateAge() {
		return (file.lastModified() - lastUpdate)/1000;
	}

	public void setGamename(String gamename) {
		this.gamename = gamename;
	}
}
