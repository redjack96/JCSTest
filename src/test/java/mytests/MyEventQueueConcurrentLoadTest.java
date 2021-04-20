package mytests;

import org.apache.jcs.engine.CacheElement;
import org.apache.jcs.engine.CacheEventQueue;
import org.apache.jcs.engine.behavior.ICacheElement;
import org.apache.jcs.engine.behavior.ICacheListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 @author Giacomo Lorenzo Rossi
 */
@RunWith(Parameterized.class)
public class MyEventQueueConcurrentLoadTest {
    private static CacheEventQueue queue = null; // Coda globale, acceduta in concorrenza
    private static MyEventQueueConcurrentLoadTest.CacheListenerImpl listen = null;
    private static final int maxFailure = 3;
    private static final int waitBeforeRetry = 100;
    private static final int idleTime = 2; // in millisecondi
    private static int count = 0;

    /* Parametro del test */
    private int end;

    /**
     * Metodo di utilita' per stampare i parametri da tenere sotto osservazione.
     * @param params parametri del metodo chiamante
     * @param message messaggio che vuole stampare il metodo chiamante
     */
    private void printCacheInfo(String params, String message) {
        // Ricavo il metodo chiamante dalla stacktrace del thread corrente.
        String callingMethod = Thread.currentThread().getStackTrace()[2].getMethodName();
        System.out.printf("[putCount] = %d [removeCount] = %d [queueSize] = %d [isAlive=%b] - %s(%s): %s\n",
                listen.putCount, listen.removeCount, queue.size(), queue.isAlive(), callingMethod, params, message);
    }


    /**
     * Costruttore per poter usare parametri di test private (e non public)
     *
     * @param end inizializzato da JUnit
     */
    public MyEventQueueConcurrentLoadTest(int end) {
        this.end = end;
        // this.expectedPutCount = expectedPutCount;
    }

    /**
     * @return Lista di parametri per i test
     */
    @Parameters(name = "{index}: end={0} expectedPutCount={1}")
    public static Collection<Object[]> getTestParameters() {
        return Arrays.asList(new Object[][]{
                {0},
                {100},
                {200},
                {300}, // il test iniziale
        });
    }

    /**
     * Test setup. Create the static queue to be used by all tests
     * <p>
     * Nota: ho utilizzato Before per poter avere nuove istanze di coda e listener a ogni
     * invocazione di test con parametri differenti.
     */
    @Before
    public void setUp() {
        System.out.printf("Eseguita setUp per la %da volta\n", ++count);
        listen = new CacheListenerImpl();
        queue = new CacheEventQueue(listen, 1L, "testCache1", maxFailure, waitBeforeRetry);
        queue.setWaitToDieMillis(idleTime);
    }

    @Test
    public void myFixedOrderTest() throws Exception {
        int CONSTANT = 1000;

        List<MyTestResult> results = new ArrayList<>();
        System.out.printf("queue size: %d, putCount: %d\n", queue.size(), listen.putCount);
        int expectedPutCount = end; // es. 200
        results.add(runPutTest(end, expectedPutCount));
        end += CONSTANT; // 1200
        expectedPutCount += end; // es. 1400
        results.add(runPutTest(end, expectedPutCount));
        end += CONSTANT; // 2200
        results.add(runRemoveTest(end));
        // results.add(runStopProcessingTest());
        end += 3 * CONSTANT; // 5200
        expectedPutCount += end; // 1400 + 5200 = 6600
        results.add(runPutTest(end, expectedPutCount));
        results.add(runRemoveTest(end)); // 5200
        // results.add(runStopProcessingTest());
        end = 100;
        expectedPutCount += end; // es. 6700
        results.add(runPutDelayTest(end, expectedPutCount));

        for (MyTestResult result : results) {
            assertTrue(result.getTestName() + ": " + result.getMessage(), result.isSuccess());
        }

    }

    @Test
    public void myCompletableFutureTest() throws InterruptedException {
        ExecutorService executorService = Executors.newCachedThreadPool();
        List<Callable<MyTestResult>> allTasks = new ArrayList<>();

        // con end = 200, si hanno gli stessi test iniziali

        allTasks.add(() -> runPutTest(end, end));
        allTasks.add(() -> runPutDelayTest(end, 2 * end));
        allTasks.add(() -> runRemoveTest(end * 2));
        allTasks.add(this::runStopProcessingTest);


        // ottiene la lista di Futures, ovvero dei task di cui si puo' ricavare il risultato quando terminano l'esecuzione.
        List<Future<MyTestResult>> futures = executorService.invokeAll(allTasks);// in questo modo sono chiamati in ordine sparso!

        executorService.shutdown(); // non accetto più altri task da invocare. Aspetto che i task correnti finiscano l'esecuzione.

        boolean terminated = executorService.awaitTermination(30, TimeUnit.SECONDS);

        for (Future<MyTestResult> task : futures) {
            try {
                assertTrue(task.get().getTestName() + " ha fallito con questo messaggio: \n" + task.get().getMessage(), task.get().isSuccess());
            } catch (ExecutionException e) {
                fail(e.getMessage());
            }
        }

        assertTrue("Timeout: Alcuni test hanno fallito.", terminated); //  Aspetta finché non ha finito

        List<Runnable> runnable = executorService.shutdownNow();
        if (runnable.size() > 0) {
            fail("Alcuni test sono ancora in esecuzione");
        }
    }
    /**
     * Adds put events to the queue.
     */
    public MyTestResult runPutTest(int end, int expectedPutCount) throws Exception {
        String params = String.format("%d,%d", end, expectedPutCount);
        for (int i = 0; i < end; i++) { // Ho rimosso <= e sostituito con <
            CacheElement elem = new CacheElement("testCache1", i + ":key", i + "data");
            queue.addPutEvent(elem);
        }

        while (!queue.isEmpty()) {
            synchronized (this) {
                printCacheInfo(params, "queue is still busy, waiting 250 millis");
                this.wait(250);
            }
        }

        printCacheInfo(params, String.format("queue is empty, comparing putCount[must be %d >= %d]", listen.putCount, expectedPutCount - 1));

        // this becomes less accurate with each test. It should never fail. If it does things are very off.
        boolean isOk = listen.putCount >= expectedPutCount - 1;
        MyTestResult myTestResult = new MyTestResult(isOk, String.format("runPutTest(%d,%d)", end, expectedPutCount));
        if (!isOk) myTestResult.setMessage("runPutTest: The put count [" + listen.putCount + "] is below the expected minimum threshold [" + expectedPutCount + "]");
        else printCacheInfo(params, String.format("[OK] putCount=%d maggiore del minimo=%d [OK]", listen.putCount, expectedPutCount - 1));
        return myTestResult;
    }

    /**
     * Add remove events to the event queue.
     * <p>
     * Non incrementa il listener.putCount, ma incrementa listener.RemoveCount
     *
     * @return true se ha successo
     */
    public MyTestResult runRemoveTest(int end) {
        String params = String.format("%d", end);
        printCacheInfo(params, "BEFORE");
        for (int i = 0; i < end; i++) {  // Ho rimosso <= e sostituito con <
            try {
                queue.addRemoveEvent(i + ":key");
            } catch (IOException e) {
                return new MyTestResult(false, String.format("runRemoveTest(%d)", end), e.getMessage());
            }
        }
        printCacheInfo(params, "AFTER");
        return new MyTestResult(true, String.format("runRemoveTest(%d)", end));
    }

    /**
     * Stops processing the cache event queue.
     * Questo lascerà invariato il numero di elementi nella coda. Perciò se qualche altro thread sta aspettando
     * che si svuota, rimarra' per sempre in attesa.
     * <p>
     * Per far diminuire nel tempo gli elemnti in coda, chiamare setAlive(true)
     */
    public MyTestResult runStopProcessingTest() {
        printCacheInfo("", "BEFORE");
        queue.stopProcessing();
        printCacheInfo("", "AFTER");
        return new MyTestResult(true, "runStopProcessingTest");
    }

    /**
     * Test putting and a delay. Waits until queue is empty to start.
     * Aggiunge end elementi con ritardo
     *
     * @throws Exception
     */
    public MyTestResult runPutDelayTest(int end, int expectedPutCount) throws Exception {
        String params = String.format("%d,%d", end, expectedPutCount);
        while (!queue.isEmpty()) {
            synchronized (this) {
                printCacheInfo(params, "[1] queue is still busy, waiting 250 millis");
                this.wait(250);
            }
        }

        printCacheInfo(params, "queue is empty, begin");

        // get it going
        CacheElement elem = new CacheElement("testCache1", "a:key", "adata");
        queue.addPutEvent(elem);

        for (int i = 0; i < end; i++) { // Ho rimosso <= e sostituito con <
            synchronized (this) {
                if (i % 2 == 0) {
                    this.wait(idleTime);
                } else {
                    this.wait(idleTime / 2);
                }
            }
            CacheElement elem2 = new CacheElement("testCache1", i + ":key", i + "data");
            queue.addPutEvent(elem2);
        }

        try {
            while (!queue.isEmpty()) {
                synchronized (this) {
                    printCacheInfo(params, "[2]: queue is still busy, waiting 250 millis");
                    this.wait(250);
                }
            }
        } catch (InterruptedException interr) {
            return new MyTestResult(false, String.format("runPutDelayTest(%d,%d)", end, expectedPutCount), "Test interrotto da timeout");
        }

        await().atMost(1000, TimeUnit.MILLISECONDS);
        printCacheInfo(params, String.format("queue is empty, comparing putCount [must be %d >= %d]", listen.putCount, expectedPutCount));

        // this becomes less accurate with each test. It should never fail. If it does things are very off.
        boolean isOk = listen.putCount >= expectedPutCount - 1;
        MyTestResult result = new MyTestResult(isOk, String.format("runPutDelayTest(%d,%d)", end, expectedPutCount), "message");

        if (!isOk) result.setMessage("runPutDelayTest(): The put count [" + listen.putCount + "] is below the expected minimum threshold [" + expectedPutCount + "]");
        else printCacheInfo(params, String.format("[OK] putCount=%d maggiore del minimo=%d [OK]", listen.putCount, expectedPutCount));
        return result;
    }

    /**
     * This is a dummy cache listener to use when testing the event queue.
     */
    private static class CacheListenerImpl implements ICacheListener {

        protected int putCount = 0;
        protected int removeCount = 0;

        /*
         * (non-Javadoc)
         *
         * @see org.apache.jcs.engine.behavior.ICacheListener#handlePut(org.apache.jcs.engine.behavior.ICacheElement)
         */
        public void handlePut(ICacheElement item) throws IOException {
            synchronized (this) {
                putCount++;
            }
        }

        /*
         * (non-Javadoc)
         *
         * @see org.apache.jcs.engine.behavior.ICacheListener#handleRemove(java.lang.String,
         *      java.io.Serializable)
         */
        public void handleRemove(String cacheName, Serializable key) throws IOException {
            synchronized (this) {
                removeCount++;
            }

        }

        /*
         * (non-Javadoc)
         *
         * @see org.apache.jcs.engine.behavior.ICacheListener#handleRemoveAll(java.lang.String)
         */
        public void handleRemoveAll(String cacheName) throws IOException {
            // TODO Auto-generated method stub

        }

        /*
         * (non-Javadoc)
         *
         * @see org.apache.jcs.engine.behavior.ICacheListener#handleDispose(java.lang.String)
         */
        public void handleDispose(String cacheName) throws IOException {
            // TODO Auto-generated method stub

        }

        /*
         * (non-Javadoc)
         *
         * @see org.apache.jcs.engine.behavior.ICacheListener#setListenerId(long)
         */
        public void setListenerId(long id) throws IOException {
            // TODO Auto-generated method stub

        }

        /*
         * (non-Javadoc)
         *
         * @see org.apache.jcs.engine.behavior.ICacheListener#getListenerId()
         */
        public long getListenerId() throws IOException {
            // TODO Auto-generated method stub
            return 0;
        }

    }

    private static class MyTestResult {
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

        public boolean isSuccess() {
            return success;
        }

        public String getTestName() {
            return testName;
        }


        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
