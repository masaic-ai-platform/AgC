package tools;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class ToolRegistration {
    private static final Map<String, ClientSideTool> toolIdToTool = new LinkedHashMap<String, ClientSideTool>();

    static {
        autoLoadFromPackage("tools.impl");
    }

    public static synchronized void registerTool(ClientSideTool tool) {
        toolIdToTool.put(tool.toolId(), tool);
    }

    public static synchronized Collection<ClientSideTool> getAll() {
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
                File[] files = new File(url.getPath()).listFiles();
                if (files == null) continue;
                for (File f : files) {
                    String n = f.getName();
                    if (!f.isFile() || !n.endsWith(".class") || n.indexOf('$') >= 0) continue;
                    loadIfTool(basePackage + '.' + n.substring(0, n.length() - 6), cl);
                }
            }
        } catch (IOException ignored) {}
    }

    private static void loadIfTool(String className, ClassLoader cl) {
        try {
            Class<?> c = Class.forName(className, true, cl);
            if (ClientSideTool.class.isAssignableFrom(c)) {
                registerTool((ClientSideTool) c.getDeclaredConstructor().newInstance());
            }
        } catch (Throwable ignored) {}
    }
}
