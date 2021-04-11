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

@RunWith(Parameterized.class)
public class MyEventQueueConcurrentLoadTest {
    private static CacheEventQueue queue = null; // Coda globale, acceduta in concorrenza
    private static MyEventQueueConcurrentLoadTest.CacheListenerImpl listen = null;
    private static final int maxFailure = 3;
    private static final int waitBeforeRetry = 100;
    private static final int idleTime = 2; // in millisecondi

    // Test Parameters !!
    private int end;
    private int expectedPutCount;

    /**
     * Costruttore per poter usare parametri di test private (e non public)
     * @param end inizializzato da JUnit
     * @param expectedPutCount inizializzato da JUnit
     */
    public MyEventQueueConcurrentLoadTest(int end, int expectedPutCount) {
        this.end = end;
        this.expectedPutCount = expectedPutCount;
    }

    /**
     * @return Lista di parametri per i test
     */
    @Parameters(name = "{index}: end={0} expectedPutCount={1}")
    public static Collection<Object[]> inputData() {
        return Arrays.asList(new Object[][]{
                {-100, -100},
                {-1,-1},
                {0,0},
                {1,1},
                {200, 200}, // il test iniziale
                {500, 200},
                {200,500}
        });
    }

    /**
     * Test setup. Create the static queue to be used by all tests
     *
     * Nota: ho utilizzato Before per poter avere una coda e un listener nuovi a ogni
     * invocazione di test con parametri differenti.
     */
    @Before
    public void setUp() {
        listen = new CacheListenerImpl();
        queue = new CacheEventQueue(listen, 1L, "testCache1", maxFailure, waitBeforeRetry);
        queue.setWaitToDieMillis(idleTime);
    }

    @Test
    public void myCompletableFutureTest() throws InterruptedException {
        ExecutorService executorService = Executors.newCachedThreadPool();
        Set<Callable<Boolean>> allTasks = new HashSet<>();

        final int CONSTANT = 1000;
        // con i valori end = 200, expectedPutCount = 200, si hanno gli stessi test iniziali
        allTasks.add(() -> runPutTest(end, expectedPutCount));
        allTasks.add(() -> runPutTest(end + CONSTANT, expectedPutCount + end + CONSTANT));
        allTasks.add(() -> runRemoveTest(end + 2 * CONSTANT));
        allTasks.add(this::runStopProcessingTest);
        allTasks.add(() -> runPutTest(end + 5 * CONSTANT, expectedPutCount + 6 * CONSTANT + 2 * end));
        allTasks.add(() -> runRemoveTest(end + 5 * CONSTANT));
        allTasks.add(this::runStopProcessingTest);
        allTasks.add(() -> runPutDelayTest(200, (expectedPutCount + end) * 2 + 6 * CONSTANT));

        executorService.invokeAll(allTasks); // in questo modo sono chiamati in ordine sparso!

        executorService.shutdown();
        // [runPutTest] this becomes less accurate with each test. It should never fail. If
        // it does things are very off.
        assertTrue("Alcuni test hanno fallito.", executorService.awaitTermination(30, TimeUnit.SECONDS)); //  Aspetta finché non ha finito
        List<Runnable> runnable = executorService.shutdownNow();
        if(runnable.size()>0){
            fail();
        }
    }

    /**
     * Adds put events to the queue.
     */
    public boolean runPutTest(int end, int expectedPutCount) throws Exception {
        for (int i = 0; i <= end; i++) {
            CacheElement elem = new CacheElement("testCache1", i + ":key", i + "data");
            queue.addPutEvent(elem);
        }

        while (!queue.isEmpty()) {
            synchronized (this) {
                System.out.println("queue is still busy, waiting 250 millis");
                this.wait(250);
            }
        }
        System.out.println("queue is empty, comparing putCount");

        // this becomes less accurate with each test. It should never fail. If
        // it does things are very off.
        assertTrue("The put count [" + listen.putCount + "] is below the expected minimum threshold ["
                + expectedPutCount + "]", listen.putCount >= (expectedPutCount - 1));

        return listen.putCount >= (expectedPutCount - 1);
    }

    /**
     * Add remove events to the event queue.
     *
     * @return true se ha successo
     */
    public boolean runRemoveTest(int end) {
        for (int i = 0; i <= end; i++) {
            try {
                queue.addRemoveEvent(i + ":key");
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Stops processing the cache event queue
     */
    public boolean runStopProcessingTest() {
        queue.stopProcessing();
        return true;
    }

    /**
     * Test putting and a delay. Waits until queue is empty to start.
     *
     * @throws Exception
     */
    public boolean runPutDelayTest(int end, int expectedPutCount) throws Exception {

        while (!queue.isEmpty()) {
            synchronized (this) {
                System.out.printf("queue is still busy (%d), waiting 250 millis", queue.size());
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

        final Thread self = Thread.currentThread();
        // A volte si blocca...
        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                if(self.isAlive()){
                    self.interrupt();
                    t.cancel();

                }
            }
        },10000L);

        try {
            while (!queue.isEmpty()) { // FIXME: ogni tanto, esegue infinitamente questo while
                synchronized (this) {
                    System.out.printf("queue is still busy (%d), waiting 250 millis", queue.size());
                    this.wait(250);
                }
            }
        } catch(InterruptedException interr){
            System.out.println("Thread interrotto dopo timeout, perche' bloccato. end = " + end + ", expectedPutCount = " + expectedPutCount);
            fail();
            return false;
        }

        await().atMost(1000, TimeUnit.MILLISECONDS);
        System.out.println("queue is empty, comparing putCount");

        // this becomes less accurate with each test. It should never fail. If
        // it does things are very off.
        assertTrue("The put count [" + listen.putCount + "] is below the expected minimum threshold ["
                + expectedPutCount + "]", listen.putCount >= (expectedPutCount - 1));
        return listen.putCount >= (expectedPutCount - 1);
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
}
