package smolrx;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Class to deal with loading and executing Jars from files at Runtime.
 */
public class JarLoader {

    // TODO: Re-implement with `doPrivileged`.

    /**
     * Instantiate an object of the specified class from the Jar, and cast it to Function<Object,Object>.
     * @param file The jar flie to laod the class from.
     * @param className The Function implementor class to instantiate.
     * @return The instances.
     * @throws MalformedURLException If file URI was incorrect.
     * @throws ClassNotFoundException If the specified class was not found in the Jar.
     * @throws InstantiationException If the instance could not be created.
     * @throws IllegalAccessException No public constructor
     * @throws InvocationTargetException If the instance could not be created.
     * @throws NoSuchMethodException If the Function implementor has no "default" or "empty" constructors.
     * @throws SecurityException
     * @throws ClassCastException If the specified class does not implement Function<Object,Object>
     */
    public static Function<Object,Object> loadJar(File file, String className) throws MalformedURLException, ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
        SimpleClient.LOGGER.log(Level.INFO, "Loading jar file: {0} with class: {1}", new Object[]{file.getAbsolutePath(), className});
        URL[] urls = new URL[]{file.toURI().toURL()};
        URLClassLoader urlClassLoader = new URLClassLoader(urls, JarLoader.class.getClassLoader());
        SimpleClient.LOGGER.log(Level.INFO, "Classloader: {0}", urlClassLoader.toString());
        var entryClass = Class.forName(className, true, urlClassLoader);
        var obj = entryClass.getConstructor().newInstance();
        @SuppressWarnings("unchecked")
        var func = (Function<Object,Object>)obj; // Throws class cast exception.
        return func;
    }
}
