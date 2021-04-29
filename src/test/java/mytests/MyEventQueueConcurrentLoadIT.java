package mytests;

import org.apache.jcs.engine.CacheElement;
import org.apache.jcs.engine.CacheEventQueue;
import org.apache.jcs.engine.behavior.ICacheListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertTrue;

/**
 * @author Giacomo Lorenzo Rossi
 * Integration Test per provare failsafe (e' di integration perche' usa un mock della interfaccia ICacheListener)
 */
@RunWith(Parameterized.class)
public class MyEventQueueConcurrentLoadIT {
    private static CacheEventQueue queue;
    private static ICacheListener listen;
    private int putCount;
    private int count; // Conta il numero di chiamate di @Before setUp() per individuare eventuali errori

    /* Parametri del test */
    private int idleTime; // in millisecondi
    private int maxFailure;
    private int waitBeforeRetry;

    /**
     * Costruttore per poter usare parametri di test private (e non public)
     */
    public MyEventQueueConcurrentLoadIT(int idleTime, int maxFailure, int waitBeforeRetry) {
        this.idleTime = idleTime;
        this.maxFailure = maxFailure;
        this.waitBeforeRetry = waitBeforeRetry;
    }

    /**
     * @return Lista di parametri per i test
     */
    @Parameters(name = "{index}: idleTime={0} maxFailure={1} waitBeforeRetry={2}")
    public static Collection<Object[]> getTestParameters() {
        return Arrays.asList(new Object[][]{
                {2, 3, 100}, // a volte fallisce
                {50, 3, 100}, // ha successo quasi sempre
        });
    }

    /**
     * Metodo di configurazione richiesto. Viene richiamato nel metodo annotato con @Before
     * per essere eseguito a ogni diversa esecuzione del test.
     *
     * Utilizza un Mock per l'intergaccia ICacheListener che sostituisce la classe interna CacheListenerImpl
     */
    private void configure() throws IOException {
        putCount = 0;

        // Creo un mock per l'interfaccia invece di reimplementarla come classe interna
        listen = Mockito.mock(ICacheListener.class);

        // Sostituisco il codice per le put della classe interna CacheListenerImpl (quello della remove era inutilizzato)
        Mockito.doAnswer((invocation) -> {
            synchronized (this) {
                putCount++;
            }
            return null;
        }).when(listen).handlePut(Mockito.any());

        // Uso la stessa queue per tutti i metodi di cui e' composto il test
        queue = new CacheEventQueue(listen, 1L, "testCache1", maxFailure, waitBeforeRetry);
        queue.setWaitToDieMillis(idleTime);
    }

    /**
     * Test setup. Create the static queue to be used by all tests
     * <p>
     * Nota: ho utilizzato Before per poter avere nuove istanze di coda e listener a ogni
     * invocazione di test con parametri differenti.
     */
    @Before
    public void setUp() throws IOException {
        System.out.printf("Eseguita setUp per la %da volta\n", ++count);
        configure();
    }

    @After
    public void tearDown(){
        queue.destroy();
        queue = null;
        listen = null;
    }

    @Test(timeout = 8 * 1000)
    public void myMultiThreadTest() {
        Set<RunnableFuture<MyTestResult>> allTasks = new HashSet<>();
        List<MyFutureThread> allFutures = new ArrayList<>();
        List<MyTestResult> allResults = new ArrayList<>();

        // Creo i RunnableFuture con gli stessi test della ActiveSuite di JUnit 3 (la suite esegue i test in thread separati e aspetta che finiscono)
        allTasks.add(new FutureTask<>(() -> runPutTest(200, 200)));
        allTasks.add(new FutureTask<>(() -> runPutTest(1200, 1400)));
        allTasks.add(new FutureTask<>(() -> runRemoveTest(2200)));
        allTasks.add(new FutureTask<>(this::runStopProcessingTest));
        allTasks.add(new FutureTask<>(() -> runPutTest(5200, 6600)));
        allTasks.add(new FutureTask<>(() -> runRemoveTest(5200)));
        allTasks.add(new FutureTask<>(this::runStopProcessingTest));
        allTasks.add(new FutureTask<>(() -> runPutDelayTest(100, 6700)));

        // Assegno ogni runnable a un MyFutureThread
        int i = 0;
        for (RunnableFuture<MyTestResult> theTask : allTasks) {
            allFutures.add(new MyFutureThread(theTask, String.format("MyFutureThread %d", i++)));
        }

        allFutures.forEach(Thread::start);

        //  Aspetta finché non ha finito (Simile a thread.join(), ma restituisce un risultato)
        for (MyFutureThread futureThread : allFutures) {
            allResults.add(futureThread.waitAndGetResult());
        }

        for (MyTestResult result : allResults) {
            assertTrue(result.getTestName() + " ha fallito con questo messaggio: \n" + result.getMessage(), result.isSuccess());
        }
    }

    /**
     * Adds put events to the queue.
     */
    public MyTestResult runPutTest(int end, int expectedPutCount) throws Exception {
        String params = String.format("%d,%d", end, expectedPutCount);
        for (int i = 0; i <= end; i++) {
            CacheElement elem = new CacheElement("testCache1", i + ":key", i + "data");
            queue.addPutEvent(elem);
        }

        while (!queue.isEmpty()) {
            synchronized (this) {
                System.out.printf("runPutTest(" + params + ")queue is still busy %d, waiting 250 millis%n", queue.size());
                this.wait(250);
            }
        }

        System.out.println("queue is empty, comparing putCount");

        // this becomes less accurate with each test. It should never fail. If it does things are very off.
        System.out.println("putCount = " + putCount);
        boolean condition = putCount >= expectedPutCount - 1;

        return new MyTestResult(condition, String.format("runPutTest(%d,%d)", end, expectedPutCount), condition ? "OK" : "The put count [" + putCount + "] is below the expected minimum threshold [" + expectedPutCount + "]");
    }

    /**
     * Add remove events to the event queue.
     * <p>
     * Non incrementa il listener.putCount, ma incrementa listener.RemoveCount
     *
     * @return true se ha successo
     */
    public MyTestResult runRemoveTest(int end) {
        System.out.println("BEFORE runRemoveTest");
        for (int i = 0; i <= end; i++) {
            try {
                queue.addRemoveEvent(i + ":key");
            } catch (IOException e) {
                return new MyTestResult(false, String.format("runRemoveTest(%d)", end), e.getMessage());
            }
        }
        System.out.println("AFTER runRemoveTest");
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
        System.out.println("BEFORE stop processing");
        queue.stopProcessing();
        System.out.printf("AFTER stop processing %d%n", queue.size());

        return new MyTestResult(true, "runStopProcessingTest");
    }

    /**
     * Test putting and a delay. Waits until queue is empty to start.
     * Aggiunge end elementi con ritardo
     *
     * @throws InterruptedException nel wait
     * @throws IOException nella putEvent
     */
    public MyTestResult runPutDelayTest(int end, int expectedPutCount) throws InterruptedException, IOException {
        String params = String.format("%d,%d", end, expectedPutCount);
        while (!queue.isEmpty()) {
            synchronized (this) {
                System.out.printf("(1) runPutDelayTest(" + params + ")queue is still busy %d, waiting 250 millis%n", queue.size());
                this.wait(250);
            }
        }

        System.out.println("queue is empty, begin");

        // get it going
        CacheElement elem = new CacheElement("testCache1", "a:key", "adata");
        queue.addPutEvent(elem);

        for (int i = 0; i <= end; i++) {
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
                    System.out.printf("(2) runPutTest(" + params + ")queue is still busy %d, waiting 250 millis%n", queue.size());

                    this.wait(250);
                }
            }
        } catch (InterruptedException interr) {
            return new MyTestResult(false, String.format("runPutDelayTest(%d,%d)", end, expectedPutCount), "Test interrotto da timeout");
        }

        await().atMost(1000, TimeUnit.MILLISECONDS);
        System.out.println("queue is empty, comparing putCount ");

        // this becomes less accurate with each test. It should never fail. If it does things are very off.
        boolean condition = putCount >= expectedPutCount - 1;

        return new MyTestResult(condition, String.format("runPutDelayTest(%d,%d)", end, expectedPutCount), condition ? "OK" : "runPutDelayTest(): The put count [" + putCount + "] is below the expected minimum threshold [" + expectedPutCount + "]");
    }
}
