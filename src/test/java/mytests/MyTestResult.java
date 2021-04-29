package mytests;

import lombok.Data;

/**
 * Classe di utilità per restituire il risultato dei test, con nome, messaggio e esito del test.
 * Data: annotazione di Lombok che genera a run-time getter e setter
 */
@Data
class MyTestResult {
    private final boolean success;

    private final String testName;

    private String message = "ok";

    public MyTestResult(boolean success, String testName) {
        this.success = success;
        this.testName = testName;
    }

    public MyTestResult(boolean success, String testName, String message) {
        this.success = success;
        this.testName = testName;
        this.message = message;
    }
}
