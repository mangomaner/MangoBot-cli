package org.mango.mangobot.plugin;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class PluginClassLoader extends URLClassLoader {
    public PluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public static PluginClassLoader create(File jarFile, ClassLoader parent) throws MalformedURLException {
        return new PluginClassLoader(new URL[]{jarFile.toURI().toURL()}, parent);
    }
}