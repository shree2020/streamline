package com.hortonworks.iotas.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ReflectionHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ReflectionHelper.class);

    private static final Class[] parameters = new Class[]{URL.class};

    public static void loadJarAndAllItsClasses(String jarFilePath) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        LOG.debug("loading jar {} and all its classses.", jarFilePath);
        URL u = (new File(jarFilePath).toURI().toURL());
        URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Class sysclass = URLClassLoader.class;

        Method method = sysclass.getDeclaredMethod("addURL", parameters);
        boolean isAccesible = method.isAccessible();
        if (!isAccesible) {
            method.setAccessible(true);
        }
        method.invoke(sysloader, new Object[]{u});

        if (!isAccesible) {
            method.setAccessible(isAccesible);
        }

        loadAllClassesFromJar(jarFilePath);
    }

    /**
     * This will only load .class files, no META-INF no resources.
     * @param jarFilePath
     * @throws ClassNotFoundException
     * @throws IOException
     */
    private static void loadAllClassesFromJar(String jarFilePath) throws ClassNotFoundException, IOException {
        LOG.debug("loading all classes from jar {}.", jarFilePath);

        JarFile jarFile = new JarFile(jarFilePath);
        URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Enumeration e = jarFile.entries();
        while (e.hasMoreElements()) {
            JarEntry je = (JarEntry) e.nextElement();
            if (je.isDirectory() || !je.getName().endsWith(".class")) {
                continue;
            }
            // -6 because of .class
            String className = je.getName().substring(0, je.getName().length() - 6);
            className = className.replace('/', '.');
            Class c = sysloader.loadClass(className);
        }
    }

    /**
     * THIS WILL ONLY TELL IF A JAR IS IN CLASSPATH, JARS LOADED DYNAMICALLY USING loadJarAndAllItsClasses WILL STILL RETURN FALSE.
     * @param jarFieName
     * @return
     */
    public synchronized static boolean isJarInClassPath(String jarFieName) {
        LOG.debug("checking if jar {} is in classPath.", jarFieName);
        String classpath = System.getProperty("java.class.path");
        //TODO: need to do better than this.
        return classpath.contains(jarFieName);
    }

    public synchronized static boolean isClassLoaded(String className) {
        try {
            Class.forName(className);
            LOG.trace("class {} is loaded", className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static <T> T newInstance(String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return (T) Class.forName(className).newInstance();
    }

    public static <T> T invokeGetter(String propertyName, Object object) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String methodName = "get" + StringUtils.capitalize(propertyName);
        Method method = object.getClass().getMethod(methodName);
        return (T) method.invoke(object);
    }

    public static <T> T invokeSetter(String propertyName, Object object, Object valueToSet) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String methodName = "set" + StringUtils.capitalize(propertyName);
        Method method = object.getClass().getMethod(methodName, valueToSet.getClass());
        return (T) method.invoke(object, valueToSet);
    }

    /**
     * Given a class, this method returns a map of names of all the instance (non static) fields -> type.
     * if the class has any super class it also includes those fields.
     * @param clazz , not null
     * @return
     */
    public static Map<String, Class> getFieldNamesToTypes(Class clazz) {
        Field[] declaredFields = clazz.getDeclaredFields();
        Map<String, Class> instanceVariableNamesToTypes = new HashMap<>();
        for(Field field : declaredFields) {
            if(!Modifier.isStatic(field.getModifiers())) {
                LOG.trace("clazz {} has field {} with type {}", clazz.getName(), field.getName(), field.getType().getName());
                instanceVariableNamesToTypes.put(field.getName(), field.getType());
            } else {
                LOG.trace("clazz {} has field {} with type {}, which is static so ignoring", clazz.getName(), field.getName(), field.getType().getName());
            }
        }

        if(!clazz.getSuperclass().equals(Object.class)) {
            instanceVariableNamesToTypes.putAll(getFieldNamesToTypes(clazz.getSuperclass()));
        }
        return instanceVariableNamesToTypes;
    }
}