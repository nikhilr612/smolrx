package smolrx;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Function;

/**
 * Class to deal with loading and executing Jars from files at Runtime.
 */
public class JarLoader {

    /**
     * Instantiate an object of the specified class from the Jar, cast it to Function<Object,Object> and invoke apply.
     * @param file The jar flie to laod the class from.
     * @param className The Function implementor class to instantiate.
     * @param t The argument to apply
     * @return The return value.
     * @throws MalformedURLException If file URI was incorrect.
     * @throws ClassNotFoundException If the specified class was not found in the Jar.
     * @throws InstantiationException If the instance could not be created.
     * @throws IllegalAccessException No public constructor
     * @throws InvocationTargetException If the instance could not be created.
     * @throws NoSuchMethodException If the Function implementor has no "default" or "empty" constructors.
     * @throws SecurityException
     * @throws ClassCastException If the specified class does not implement Function<Object,Object>
     */
    public static Object runJar(File file, String className, Object t) throws MalformedURLException, ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
        URL[] urls = new URL[]{file.toURI().toURL()};
        URLClassLoader urlClassLoader = new URLClassLoader(urls, JarLoader.class.getClassLoader());
        var entryClass = Class.forName(className, true, urlClassLoader);
        var obj = entryClass.getConstructor().newInstance();
        var func = (Function<Object,Object>)obj; // Throws class cast exception.
        return func.apply(t);
    }

}
