package eu.neurovertex.xmppcraft.nbtparser;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;

import static eu.neurovertex.xmppcraft.Main.log;

/**
 * @author Neurovertex
 *         Date: 16/09/2014, 16:25
 */
public class NBTParser {
	private DataInputStream in;
	private CompoundTag rootTag;
	private int error = 0;

	public static NBTParser parseFile(File f) {
		try {
			NBTParser parser = new NBTParser(f);
			parser.parseFile();
			return parser;
		} catch (IOException e) {
			log.log(Level.SEVERE, "Error parsing NBT file", e);
		}
		return null;
	}

	private NBTParser(File file) throws IOException {
		in = new DataInputStream(new GZIPInputStream(new FileInputStream(file)));
	}

	private void parseFile() throws IOException {
		try {
			rootTag = (CompoundTag) parseNamedTag(null);
		} catch (ClassCastException e) {
			throw new ParseException("Non-compound root tag");
		}
	}

	private Tag parseNamedTag(Tag parent) {
		byte tagId;
		TagType type;
		StringTag name;
		Tag t;
		try {
			tagId = in.readByte();
			type = TagType.getType(tagId);
			if (type == null || type == TagType.END)
				return null;
			t = Tag.newInstance(parent, type);
			name = new StringTag(null);
			name.parse(this);
			t.name = name.getValue();
			t.parse(this);
			return t;
		} catch (Exception e) {
			Tag error = new ErrorTag(parent, e);
			error.name = "error_" + (this.error++);
			System.err.println("Error parsing " + error.getPath());
			e.printStackTrace();
			return error;
		}
	}

	public CompoundTag getRootTag() {
		return rootTag;
	}

	public static abstract class Tag {
		private Tag parent;
		private TagType type;
		private String name;

		protected Tag(Tag parent, TagType type) {
			this.parent = parent;
			this.type = type;
		}

		public Tag getParent() {
			return parent;
		}

		public TagType getType() {
			return type;
		}

		public boolean isNamed() {
			return name != null;
		}

		public static Tag newInstance(Tag parent, TagType type) {
			switch (type) {
				case END:
					return null;
				case BYTE:
				case SHORT:
				case INT:
				case LONG:
				case FLOAT:
				case DOUBLE:
					return new NumberTag(parent, type);
				case BYTE_ARRAY:
					return new ByteArrayTag(parent);
				case STRING:
					return new StringTag(parent);
				case LIST:
					return new ListTag(parent);
				case COMPOUND:
					return new CompoundTag(parent);
				default:
					throw new IllegalArgumentException("Unknown tag type");
			}
		}

		protected abstract Tag parse(NBTParser parser) throws IOException;

		protected void printStructure(PrintStream out, int indentLevel) {
			out.append(StringUtils.repeat('\t', indentLevel)).append("Tag_").append(type.name().toLowerCase());
			if (isNamed())
				out.append("(\"").append(name).append("\")");
			out.append(": ");
		}

		public String getName() {
			return name;
		}

		public void printStructure() {
			printStructure(System.out);
		}

		public void printStructure(PrintStream out) {
			printStructure(out, 0);
			out.append("\n");
		}

		public String getPath() {
			return buildPath(null).toString();
		}

		protected StringBuilder buildPath(Tag source) {
			if (parent == null)
				return new StringBuilder("root:").append(name).append("(").append(type.name()).append(")/");
			else if (isNamed())
				return parent.buildPath(this).append(name).append("(").append(type.name()).append(")/");
			else
				return parent.buildPath(this).append("(").append(type.name()).append(")/");
		}

		public Tag findTag(String name) {
			return (name.equalsIgnoreCase(this.name)) ? this : null;
		}

		@Override
		public abstract String toString();
	}

	public static class CompoundTag extends Tag implements Map<String, Tag> {
		private Map<String, Tag> map;

		private CompoundTag(Tag parent) {
			super(parent, TagType.COMPOUND);
		}

		@Override
		protected CompoundTag parse(NBTParser parser) throws IOException {
			Map<String, Tag> tags = new HashMap<>();
			Tag t;
			while ((t = parser.parseNamedTag(this)) != null) {
				tags.put(t.getName().toLowerCase(), t);
				if (t instanceof ErrorTag)
					break;
			}
			this.map = Collections.unmodifiableMap(tags);
			return this;
		}

		@Override
		public String toString() {
			return String.format("{CompoundTag,size=#%d}", map.size());
		}

		@Override
		public void printStructure(PrintStream out, int indentLevel) {
			super.printStructure(out, indentLevel);
			out.append(String.valueOf(map.size())).append(" entries\n");
			out.append(StringUtils.repeat('\t', indentLevel)).append("{\n");
			for (Tag t : map.values()) {
				t.printStructure(out, indentLevel + 1);
				out.append('\n');
			}
			out.append(StringUtils.repeat('\t', indentLevel)).append("}");
		}

		@Override
		protected StringBuilder buildPath(Tag source) {

			if (getParent() == null)
				return new StringBuilder("root:").append(getName()).append("(").append(getType().name()).append(")[").append(source.getName()).append("]/");
			else  {
				StringBuilder output = (isNamed()) ?
						getParent().buildPath(this).append(getName()).append("(").append(getType().name()).append(")") :
						getParent().buildPath(this).append("(").append(getType().name()).append(")");
				if (source != null)
					output.append("[").append(source.getName().toLowerCase()).append("]/");
				return output;
			}
		}

		@Override
		public Tag findTag(String name) {
			if (name.equalsIgnoreCase(getName()))
				return this;
			Tag t = null;
			for (String n : map.keySet())
				if ((t = map.get(n).findTag(name)) != null)
					break;
			return t;
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
		public Tag get(Object key) {
			if (key instanceof String)
				key = ((String)key).toLowerCase();
			return map.get(key);
		}

		@Override
		public boolean containsKey(Object key) {
			if (key instanceof String)
				key = ((String)key).toLowerCase();
			return map.containsKey(key);
		}

		@Override
		public Tag put(String key, Tag value) {
			return map.put(key.toLowerCase(), value);
		}

		@Override
		public void putAll(Map<? extends String, ? extends Tag> m) {
			map.putAll(m);
		}

		@Override
		public Tag remove(Object key) {
			if (key instanceof String)
				key = ((String)key).toLowerCase();
			return map.remove(key);
		}

		@Override
		public void clear() {
			map.clear();
		}

		@Override
		public boolean containsValue(Object value) {
			return map.containsValue(value);
		}

		@Override
		public Set<String> keySet() {
			return map.keySet();
		}

		@Override
		public Collection<Tag> values() {
			return map.values();
		}

		@Override
		public Set<Entry<String, Tag>> entrySet() {
			return map.entrySet();
		}
	}

	public static class StringTag extends Tag {
		private String value;

		private StringTag(Tag parent) {
			super(parent, TagType.STRING);
		}

		@Override
		protected StringTag parse(NBTParser parser) throws IOException {
			short length = parser.in.readShort();
			byte chars[] = new byte[length];

			for (int i = 0; i < length; i++) chars[i] = parser.in.readByte();

			value = new String(chars);
			return this;
		}

		public String getValue() {
			return value;
		}

		@Override
		public void printStructure(PrintStream out, int indentLevel) {
			super.printStructure(out, indentLevel);
			out.append(value);
		}

		@Override
		public String toString() {
			return String.format("\"%s\"", value);
		}
	}

	public static class NumberTag extends Tag {
		private Number value;

		private NumberTag(Tag parent, TagType type) {
			super(parent, type);
		}

		@Override
		protected NumberTag parse(NBTParser parser) throws IOException {
			switch (getType()) {
				case BYTE:
					value = parser.in.readByte();
					break;
				case SHORT:
					value = parser.in.readShort();
					break;
				case INT:
					value = parser.in.readInt();
					break;
				case LONG:
					value = parser.in.readLong();
					break;
				case FLOAT:
					value = parser.in.readFloat();
					break;
				case DOUBLE:
					value = parser.in.readDouble();
					break;
			}
			return this;
		}

		@Override
		public void printStructure(PrintStream out, int indentLevel) {
			super.printStructure(out, indentLevel);
			out.print(value.toString());
		}

		@Override
		public String toString() {
			return value.toString();
		}

		public Number getValue() {
			return value;
		}
	}

	public static class ListTag extends Tag {
		private TagType listType;
		private List<Tag> list = new ArrayList<>();

		public ListTag(Tag parent) {
			super(parent, TagType.LIST);
		}

		@Override
		protected ListTag parse(NBTParser parser) throws IOException {
			byte id = new NumberTag(null, TagType.BYTE).parse(parser).getValue().byteValue();
			listType = TagType.getType(id);
			int length = new NumberTag(null, TagType.INT).parse(parser).getValue().intValue();
			Tag last = null;
			for (int i = 0; i < length && !(last instanceof ErrorTag); i++)
				list.add(last = Tag.newInstance(this, listType).parse(parser));
			return this;
		}

		public TagType getListType() {
			return listType;
		}

		public List<Tag> getList() {
			return Collections.unmodifiableList(list);
		}

		@Override
		public void printStructure(PrintStream out, int indentLevel) {
			super.printStructure(out, indentLevel);
			out.append(String.valueOf(list.size())).append(" entries of type ").append(listType.name()).append('\n');
			out.append(StringUtils.repeat('\t', indentLevel)).append("[\n");
			for (Tag t : list) {
				t.printStructure(out, indentLevel + 1);
				out.append('\n');
			}
			out.append(StringUtils.repeat('\t', indentLevel)).append("]");
		}

		public <E extends Tag> Iterator<E> iterator(Class<E> c) {
			final Iterator<Tag> iterator = list.iterator();
			if (list.size() > 0 && !c.isAssignableFrom(list.get(0).getClass()))
				throw new ClassCastException(String.format("Called iterator(%s) on ListTag of type %s", c.getSimpleName(), list.get(0).getClass().getSimpleName()));
			return new Iterator<E>() {
				@Override
				public boolean hasNext() {
					return iterator.hasNext();
				}

				@Override
				public E next() {
					//noinspection unchecked
					return (E) iterator.next();
				}

				@Override
				public void remove() {
					iterator.remove();
				}
			};
		}

		@Override
		protected StringBuilder buildPath(Tag source) {

			if (getParent() == null)
				return new StringBuilder("root:").append(getName()).append("(").append(getType().name()).append(")[").append(list.indexOf(source)).append("]/");
			else {
				StringBuilder output = (isNamed()) ?
					getParent().buildPath(this).append(getName()).append("(").append(getType().name()).append(")") :
					getParent().buildPath(this).append("(").append(getType().name()).append(")");
				if (source != null)
					output.append("[").append(list.indexOf(source)).append("]/");
				return output;
			}
		}

		@Override
		public Tag findTag(String name) {
			if (name.equalsIgnoreCase(getName()))
				return this;
			Tag t = null;
			for (Tag tag : list)
				if ((t = tag.findTag(name)) != null)
					break;
			return t;
		}

		@Override
		public String toString() {
			return String.format("{ListTag,type=%s,size=%d}", listType.name(), list.size());
		}
	}

	public static class ByteArrayTag extends Tag {
		private byte[] array;

		private ByteArrayTag(Tag parent) {
			super(parent, TagType.BYTE_ARRAY);
		}

		@Override
		protected ByteArrayTag parse(NBTParser parser) throws IOException {
			int length = new NumberTag(null, TagType.INT).parse(parser).getValue().intValue();
			array = new byte[length];
			parser.in.readFully(array);
			return this;
		}

		public byte[] getArray() {
			byte[] val = new byte[array.length];
			System.arraycopy(array, 0, val, 0, array.length);
			return val;
		}

		@Override
		public void printStructure(PrintStream out, int indentLevel) {
			super.printStructure(out, indentLevel);
			out.append(toString());
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder("[");
			for (int i = 0; i < array.length; i++)
				sb.append(Integer.toHexString(array[i] & 0xFF)).append((i < array.length - 1) ? ',' : ']');
			return sb.toString();
		}
	}

	public static class ErrorTag extends Tag {
		private Exception exception;

		protected ErrorTag(Tag parent, Exception exception) {
			super(parent, TagType.ERROR);
			this.exception = exception;
		}

		@Override
		protected Tag parse(NBTParser parser) throws IOException {
			return null;
		}

		@Override
		public void printStructure(PrintStream out, int indentLevel) {
			out.append("TAG_ERROR");
			if (isNamed())
				out.append('(').append(getName()).append(')');
			out.append(':').append(exception.getClass().getName()).append(" : ").append(exception.getMessage());
		}

		@Override
		public String toString() {
			return "{ErrorTag}";
		}
	}

	private static enum TagType {
		END, BYTE, SHORT,
		INT, LONG, FLOAT,
		DOUBLE, BYTE_ARRAY, STRING,
		LIST, COMPOUND, ERROR;

		private static TagType getType(byte id) {
			if (id < 0 || id > values().length)
				return null;
			return values()[id];
		}

	}

	public class ParseException extends IOException {
		public ParseException(String message) {
			super(message);
		}

		public ParseException(String message, Throwable cause) {
			super(message, cause);
		}

		public ParseException(Throwable cause) {
			super(cause);
		}
	}
}
