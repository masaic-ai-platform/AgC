package reference;

public class AddNumbersTool implements ClientSideTool {

    AddNumbersTool() {
        ClientSideToolRunner.registerTool(new AddNumbersTool());
    }

    @Override
    public String toolId() {
        return ""; //can be autp populated .... because this is queueName
    }

    @Override
    public String executeTool(String argumentsJson) {
        //implement your code....
        return "";
    }
}
