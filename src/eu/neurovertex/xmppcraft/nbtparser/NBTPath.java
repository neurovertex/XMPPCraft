package eu.neurovertex.xmppcraft.nbtparser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static eu.neurovertex.xmppcraft.nbtparser.NBTParser.*;

/**
 * @author Neurovertex
 *         Date: 17/09/2014, 13:12
 */
public class NBTPath<E extends Tag> {
	private static final Pattern listPattern = Pattern.compile("(.*)\\[(\\d+)\\]"), keyPattern = Pattern.compile("(.*)\\.(\\w+)");
	private PathNode node;
	private NBTPath parent;

	private NBTPath() {}

	private NBTPath(NBTPath parent, PathNode node) {
		this.parent = parent;
		this.node = node;
	}

	public E getElement(Tag root) {
		try {
			// I know it can fail you butt that's why there's in a try catch
			//noinspection unchecked
			return (E) findElement(root);
			// Compilers I swear ...
		} catch (ClassCastException e) {
			throw new NBTPathException("Wrong tag type in path", e);
		} catch (IndexOutOfBoundsException e) {
			throw new NBTPathException("Index out of bound in path", e);
		} catch (NullPointerException e) {
			throw new NBTPathException("Unknown key in path", e);
		}
	}

	private Tag findElement(Tag root) {
		if (parent == null)
			return root;
		return node.findElement(parent.findElement(root));
	}

	public static NBTPath<CompoundTag> root() { // Root is always compound
		return new NBTPath<>();
	}

	public <F extends Tag> NBTPath<F> get(int index) {
		return new NBTPath<>(this, new ListIndex(index));
	}

	public <F extends Tag> NBTPath<F> get(String key) {
		return new NBTPath<>(this, new CompoundKey(key));
	}

	public static <F extends Tag> NBTPath<F> parse(String path) throws NBTPathException {
		// Yes, unchecked, deal with it
		//noinspection unchecked
		return parsePath(path);
		// I mean it literally, deal with it. Be careful. Catch ClassCastException's
	}

	private static NBTPath parsePath(String path) {
		Matcher matcher;
		String parentPath;
		PathNode node;
		if ((matcher = listPattern.matcher(path)).matches()) {
			parentPath = matcher.group(1);
			node = new ListIndex(Integer.parseInt(matcher.group(2)));
		} else if ((matcher = keyPattern.matcher(path)).matches()) {
			parentPath = matcher.group(1);
			node = new CompoundKey(matcher.group(2));
		} else
			throw new IllegalArgumentException("Parse error : "+ path +" is not a valid NBT Path");
		NBTPath parent = (parentPath.length() == 0 || parentPath.equalsIgnoreCase("root")) ? NBTPath.root() : parsePath(parentPath);
		return new NBTPath(parent, node);
	}

	@Override
	public String toString() {
		return (parent == null) ? "root" : parent.toString() + node.toString();
	}

	private static class ListIndex extends PathNode {

		private final int index;

		private ListIndex(int index) {
			this.index = index;
		}

		@Override
		protected Tag findElement(Tag base) {
			return ((ListTag)base).getList().get(index);
		}

		@Override
		public String toString() {
			return "["+ index +"]";
		}
	}


	private static abstract class PathNode {
		protected abstract Tag findElement(Tag base);
		@Override
		public abstract String toString();
	}

	private static class CompoundKey extends PathNode {
		private String key;

		private CompoundKey(String key) {
			this.key = key;
		}

		@Override
		protected Tag findElement(Tag base) {
			return ((CompoundTag)base).get(key);
		}

		@Override
		public String toString() {
			return "."+ key;
		}
	}

	public static class NBTPathException extends RuntimeException {
		public NBTPathException(String message) {
			super(message);
		}

		public NBTPathException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
