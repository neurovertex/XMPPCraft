package eu.neurovertex.xmppcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Neurovertex
 *         Date: 10/05/2014, 13:29
 */
public class Settings implements Map<String, Object> {
	private static final Type mapTypeToken = new TypeToken<Map<String, Object>>() {}.getType();
	private static final Logger log = Logger.getLogger(Settings.class.getName());
	private File file, backup;

	private Map<String, Object> map = new HashMap<>();

	public Settings(String filename, boolean load) {
		file = new File(filename);
		backup = new File(filename + ".bck");
		if (load)
			try {
				load();
			} catch (IOException e) {
				log.log(Level.SEVERE, "Error while loading settings "+ filename, e);
			}
	}

	public Settings(String filename) {
		file = new File(filename);
		backup = new File(filename + ".bck");
	}

	public void load() throws IOException {
		if (file.exists())
			try (FileReader fr = new FileReader(file); JsonReader reader = new JsonReader(fr)) {
				log.fine("Loading settings from "+ file.getName());
				Gson gson = new Gson();
				map = gson.fromJson(reader, mapTypeToken);
			}
		else
			log.info(file.getName() +" : non-existent file");
	}

	public void save() throws IOException {
		Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try (PrintWriter out = new PrintWriter(file)) {
			out.print(gson.toJson(map, mapTypeToken));
		} catch (Throwable e) {
			Files.copy(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			if (e instanceof IOException)
				throw e;
			else
				e.printStackTrace();
		}
	}

	@Override
	public Object get(Object key) {
		return map.get(key);
	}

	public Object get(String key, Object def) {
		Object o = map.get(key);
		if (o == null)
			map.put(key, o = def);
		return o;
	}

	public String getString(String key) {
		return map.containsKey(key) ? String.valueOf(map.get(key)) : null;
	}

	public String getString(String key, String def) {
		return (String) get(key, def);
	}

	public Number getNumber(String key) {
		try {
			return (Number)map.get(key);
		} catch (NullPointerException e) {
			throw new RuntimeException("getNumber("+ key +")", e);
		}
	}

	public Number getNumber(String key, Number def) {
		return (Number)get(key, def);
	}

	public Integer getInteger(String key) {
		return getNumber(key).intValue();
	}

	public Integer getInteger(String key, Integer def) {
		return getNumber(key, def).intValue();
	}

	public boolean containsKey(String key) {
		return map.containsKey(key);
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return map.containsValue(value);
	}

	@Override
	public Object put(String key, Object value) {
		return map.put(key, value);
	}

	@Override
	public Object remove(Object key) {
		return map.remove(key);
	}

	@Override
	public void putAll(Map<? extends String, ?> m) {
		map.putAll(m);
	}

	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public Set<String> keySet() {
		return map.keySet();
	}

	@Override
	public Collection<Object> values() {
		return map.values();
	}

	@Override
	public Set<Entry<String, Object>> entrySet() {
		return map.entrySet();
	}

	@Override
	public String toString() {
		return "{Settings:"+ file.getName() +"}";
	}
}
