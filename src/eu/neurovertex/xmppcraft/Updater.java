package eu.neurovertex.xmppcraft;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * @author Neurovertex
 *         Date: 14/09/2014, 01:03
 */
public class Updater {
	private String version;
	private URL baseURL, updateURL;

	public Updater() throws IOException {
		String resLoc = getClass().getName().replace('.', '/').concat(".class");
		URL mainURL = Main.class.getClassLoader().getResource(resLoc);
		if (mainURL == null)
			throw new FileNotFoundException("Updater class file");
		String base = mainURL.toString().substring(0, mainURL.toString().indexOf("eu/neurovertex"));
		baseURL = new URL(base);
		System.out.println("Base path : " + baseURL.getPath());
		/*if (base.endsWith(".jar!/")) {
			String updatePath = baseURL.getPath().substring(0, baseURL.getPath().lastIndexOf('/', baseURL.getPath().length() - 3)) + "/update/";
			System.out.println("Update jar path : " + updatePath);
			this.updateURL = new URL(updatePath);
		}*/
		URLClassLoader loader = new URLClassLoader(new URL[]{updateURL != null && new File(updateURL.getFile()).exists() ? updateURL : baseURL});
		URL manifUrl = loader.findResource("META-INF/MANIFEST.MF");
		Manifest man = new Manifest(manifUrl.openStream());
		version = getVersion(man);
	}

	public String getVersion() {
		return version;
	}

	private String getVersion(Manifest man) {
		Attributes att = man.getMainAttributes();
		return att.getValue(Attributes.Name.SPECIFICATION_VERSION) + "." + att.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
	}

	public String checkVersion() throws IOException {
		URL url = getManifestURL();
		if (url != null) {
			Manifest manifest = new Manifest(getManifestURL().openStream());
			return getVersion(manifest);
		} else
			return "0.0 no update found";
	}

	public URL getManifestURL() {
		URLClassLoader loader = new URLClassLoader(new URL[]{updateURL != null && new File(updateURL.getFile()).exists() ? updateURL : baseURL});
		return loader.findResource("META-INF/MANIFEST.MF");
	}

	public Manifest getManifest() throws IOException {
		if (getClass().getClassLoader() instanceof URLClassLoader) {
			URLClassLoader loader = (URLClassLoader) getClass().getClassLoader();
			URL url = loader.findResource("META-INF/MANIFEST.MF");
			return new Manifest(url.openStream());
		} else
			System.err.println("Not URLClassLoader");
		return null;
	}

	public ClassLoader update() throws IOException, ClassNotFoundException {
		return updateClasses();
	}

	public ClassLoader updateClasses(Class<?>... classes) throws IOException {
		String nv = checkVersion();
		if (version.compareTo(nv) < 0) {
			List<String> classNames = new ArrayList<>();
			for (Class<?> c : classes)
				classNames.add(c.getName());
			ClassLoader parent = Thread.currentThread().getContextClassLoader();
			if (classes.length == 0)
				while (parent instanceof ParentLastURLClassLoader)
					parent = parent.getParent();
			ClassLoader loader = new ParentLastURLClassLoader(parent, Arrays.asList(updateURL != null ? updateURL : baseURL), classNames, classNames.size() > 0);
			Thread.currentThread().setContextClassLoader(loader);
			return loader;
		}
		return null;
	}

	/**
	 * @author karoberts
	 *         Found on http://goo.gl/mPj8p1 , added white/blacklist implementation and package filtering
	 */
	private static class ParentLastURLClassLoader extends ClassLoader {
		private ChildURLClassLoader childClassLoader;

		public ParentLastURLClassLoader(ClassLoader parent, List<URL> classpath, List<String> blacklist, boolean whitelist) {
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
			private List<String> blacklist;
			private boolean whitelist; // 'Inverts' the blacklist

			public ChildURLClassLoader(URL[] urls, FindClassClassLoader realParent, List<String> blacklist, boolean whitelist) {
				super(urls, null);

				this.realParent = realParent;
				this.blacklist = new ArrayList<>(blacklist);
				this.whitelist = whitelist;
			}

			@Override
			public Class<?> findClass(String name) throws ClassNotFoundException {
				try {
					// first try to use the URLClassLoader findClass
					// Ignore blacklist/non-whitelist. Do not reload classes from other packages.
					if ((blacklist.contains(name) ^ whitelist) || !name.startsWith(Main.class.getPackage().getName()))
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
