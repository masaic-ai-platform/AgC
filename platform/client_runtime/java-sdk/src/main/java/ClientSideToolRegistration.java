import tools.ClientSideTool;

import java.util.ArrayList;
import java.util.List;

public class ClientSideToolRegistration {
    private static List<ClientSideTool> tools = new ArrayList<ClientSideTool>();

    public static void registerTool(ClientSideTool tool) {
        tools.add(tool);
    }
}
