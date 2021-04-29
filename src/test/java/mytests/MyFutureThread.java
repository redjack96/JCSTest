package mytests;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;

public class MyFutureThread extends Thread {
    private final RunnableFuture<MyTestResult> futureTask;
    private final String testName;

    public MyFutureThread(RunnableFuture<MyTestResult> futureTask, String threadName) {
        super(futureTask);
        this.futureTask = futureTask;
        this.testName = threadName;
    }

    public String getTestName() {
        return testName;
    }

    public MyTestResult waitAndGetResult() {
        try {
            MyTestResult myTestResult = futureTask.get();
            System.out.println(getTestName() + " ha finito!!!");
            return myTestResult;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return new MyTestResult(false, testName);
        }
    }

}
