package tools;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class ToolRegistration {
    private static final Map<String, AgcRuntimeTool> toolIdToTool = new LinkedHashMap<String, AgcRuntimeTool>();

    static {
        autoLoadFromPackage("tools.impl");
    }

    public static synchronized void registerTool(AgcRuntimeTool tool) {
        toolIdToTool.put(tool.toolId(), tool);
    }

    public static synchronized Collection<AgcRuntimeTool> getAll() {
        return Collections.unmodifiableCollection(toolIdToTool.values());
    }

    private static void autoLoadFromPackage(String basePackage) {
        String path = basePackage.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Enumeration<URL> urls = cl.getResources(path);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if (!"file".equals(url.getProtocol())) continue;
                loadFromDirectory(new File(url.getPath()), basePackage, cl);
            }
        } catch (IOException ignored) {}
    }

    private static void loadFromDirectory(File directory, String packageName, ClassLoader cl) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File f : files) {
            String n = f.getName();
            if (f.isDirectory()) {
                loadFromDirectory(f, packageName + '.' + n, cl);  // Recursively load from subdirectories
            } else if (f.isFile() && n.endsWith(".class") && n.indexOf('$') < 0) {
                loadIfTool(packageName + '.' + n.substring(0, n.length() - 6), cl);
            }
        }
    }

    private static void loadIfTool(String className, ClassLoader cl) {
        try {
            Class<?> c = Class.forName(className, true, cl);
            if (AgcRuntimeTool.class.isAssignableFrom(c)) {
                registerTool((AgcRuntimeTool) c.getDeclaredConstructor().newInstance());
            }
        } catch (Throwable ignored) {}
    }
}
