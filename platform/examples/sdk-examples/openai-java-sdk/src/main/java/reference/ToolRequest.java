package reference;

public class ToolRequest {

    private String name;
    private String arguments;
    private LoopContextInfo loopContextInfo;

    public ToolRequest() {
        // Default constructor for Jackson or reflection
    }

    public ToolRequest(String name, String arguments, LoopContextInfo loopContextInfo) {
        this.name = name;
        this.arguments = arguments;
        this.loopContextInfo = loopContextInfo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public LoopContextInfo getLoopContextInfo() {
        return loopContextInfo;
    }

    public void setLoopContextInfo(LoopContextInfo loopContextInfo) {
        this.loopContextInfo = loopContextInfo;
    }

    @Override
    public String toString() {
        return "PluggedToolRequest{" +
                "name='" + name + '\'' +
                ", arguments='" + arguments + '\'' +
                ", loopContextInfo=" + loopContextInfo +
                '}';
    }
}
