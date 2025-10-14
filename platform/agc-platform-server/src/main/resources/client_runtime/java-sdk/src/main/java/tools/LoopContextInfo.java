package tools;

public class LoopContextInfo {
    private String correlationId;

    public LoopContextInfo() {}

    public LoopContextInfo(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}


