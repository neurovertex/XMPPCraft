package eu.neurovertex.xmppcraft;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * @author Neurovertex
 *         Date: 14/09/2014, 01:03
 */
public class Updater {
	private String version;
	private URL baseURL;
	/**
	 * Classes that should not be updated (cross-update interface(s))
	 */
	private static final List<String> globalBlacklist = new ArrayList<>(); /*Arrays.asList(Startable.class.getName())// for future update*/

	/**
	 * Creates a new Updater. Current version is automatically retreived from the manifest loaded with the classes.
	 * @throws IOException I don't know when that could happen but if it does you're probably fucked
	 */
	public Updater() throws IOException {
		String resLoc = getClass().getName().replace('.', '/').concat(".class");
		URL mainURL = Main.class.getClassLoader().getResource(resLoc);
		if (mainURL == null)
			throw new FileNotFoundException("Updater class file");
		String base = mainURL.toString().substring(0, mainURL.toString().indexOf("eu/neurovertex"));
		baseURL = new URL(base);
		System.out.println("Base path : " + baseURL.getPath());
		URLClassLoader loader = new URLClassLoader(new URL[]{baseURL});
		URL manifUrl = loader.findResource("META-INF/MANIFEST.MF");
		Manifest man = new Manifest(manifUrl.openStream());
		version = getVersion(man);
	}

	public String getVersion() {
		return version;
	}

	/**
	 * Reads the version from a specific manifest file (Used to get the version a yet-to-load files.
	 * @param man    Manifest file
	 * @return	The version of the given manifest.
	 */
	private String getVersion(Manifest man) {
		Attributes att = man.getMainAttributes();
		return att.getValue(Attributes.Name.SPECIFICATION_VERSION) + "." + att.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
	}

	/**
	 * Reads the version of the manifest currently on disk.
	 * @return	The version included in the files
	 * @throws IOException If files can't be read or something I guess
	 */
	public String checkVersion() throws IOException {
		URL url = getManifestURL();
		if (url != null) {
			Manifest manifest = new Manifest(getManifestURL().openStream());
			return getVersion(manifest);
		} else
			return "0.0 error";
	}

	/**
	 * Returns the URL of the manifest file
	 * @return the URL of the manifest
	 */
	private URL getManifestURL() {
		URLClassLoader loader = new URLClassLoader(new URL[]{baseURL});
		return loader.findResource("META-INF/MANIFEST.MF");
	}

	/**
	 * Creates a new Parent-Last classloader, set it as the Thread's classloader, and return it. The new classloader will
	 * reload any class from disk, allowing to update said classes.
	 * @param classes    If any, the new loader will be restricted to those classes, otherwise, no filtering will be done.
	 * @return	The new ClassLoader
	 */
	public ClassLoader update(Class<?>... classes) {
		List<String> classNames = new ArrayList<>();
		for (Class<?> c : classes)
			classNames.add(c.getName());
		ClassLoader parent = Thread.currentThread().getContextClassLoader();
		/*
		If the current thread classloader is a ParentLast, then there already was an update. Unless this one has
		blacklisted classes (which might be covered by the current loader), the new one will completely hide the old one.
		Thus, rather than making an ever-lenghtening hierarchy of classloaders, I connect any new one to the original.
		*/
		if (classes.length == 0)
			while (parent instanceof ParentLastURLClassLoader)
				parent = parent.getParent();
		ClassLoader loader = new ParentLastURLClassLoader(parent, Arrays.asList(baseURL), classNames, classNames.size() > 0);
		Thread.currentThread().setContextClassLoader(loader);
		return loader;
	}

	/**
	 * This provides a way to update classes. Java's classloaders are parent-first, they will ask their parent loader if
	 * they have the class before trying to load it themselves, thus avoiding to load several times the same class (which
	 * would result in the different instances being considered different classes). But if you want to update classes,
	 * you need to force the application to load new ones, thus you need a Parent-Last classloader, that will load classes
	 * it has first, and if it can't, fall back to its parent.<br />
	 *
	 * <br />Found on <a href="http://goo.gl/mPj8p1">StackOverflow</a> (may this website exist forever as it is a blessing
	 * for programmers) , white/blacklist implementation and package filtering by me.
	 * @author karoberts
	 */
	private static class ParentLastURLClassLoader extends ClassLoader {
		private ChildURLClassLoader childClassLoader;

		public ParentLastURLClassLoader(ClassLoader parent, List<URL> classpath, Collection<String> blacklist, boolean whitelist) {
			super(parent);
			URL[] urls = classpath.toArray(new URL[classpath.size()]);

			childClassLoader = new ChildURLClassLoader(urls, new FindClassClassLoader(this.getParent()), blacklist, whitelist);
		}

		/**
		 * This class allows me to call findClass on a classloader
		 */
		private static class FindClassClassLoader extends ClassLoader {
			public FindClassClassLoader(ClassLoader parent) {
				super(parent);
			}

			@Override
			public Class<?> findClass(String name) throws ClassNotFoundException {
				return super.findClass(name);
			}
		}

		/**
		 * This class delegates (child then parent) for the findClass method for a URLClassLoader.
		 * We need this because findClass is protected in URLClassLoader
		 */
		private static class ChildURLClassLoader extends URLClassLoader {
			private FindClassClassLoader realParent;
			private Set<String> blacklist;
			private boolean whitelist; // 'Inverts' the blacklist

			public ChildURLClassLoader(URL[] urls, FindClassClassLoader realParent, Collection<String> blacklist, boolean whitelist) {
				super(urls, null);

				this.realParent = realParent;
				this.blacklist = new HashSet<>(blacklist);
				this.whitelist = whitelist;
			}

			@Override
			public Class<?> findClass(String name) throws ClassNotFoundException {
				try {
					// first try to use the URLClassLoader findClass
					// Ignore blacklist/non-whitelist. Do not reload classes from other packages.
					if ((blacklist.contains(name) ^ whitelist) || globalBlacklist.contains(name) || !name.startsWith(Main.class.getPackage().getName()))
						throw new ClassNotFoundException();
					return super.findClass(name);
				} catch (ClassNotFoundException e) {
					// if that fails, we ask our real parent classloader to load the class (we give up)
					return realParent.loadClass(name);
				}
			}
		}

		@Override
		protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			try {
				// first we try to find a class inside the child classloader
				return childClassLoader.findClass(name);
			} catch (ClassNotFoundException e) {
				// didn't find it, try the parent
				return super.loadClass(name, resolve);
			}
		}
	}
}
