package mytests;

import org.apache.jcs.engine.CacheElement;
import org.apache.jcs.engine.CacheEventQueue;
import org.apache.jcs.engine.behavior.ICacheElement;
import org.apache.jcs.engine.behavior.ICacheListener;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class MyEventQueueConcurrentLoadTest {
    private static CacheEventQueue queue = null; // Coda globale, acceduta in concorrenza

    private static MyEventQueueConcurrentLoadTest.CacheListenerImpl listen = null;

    private static final int maxFailure = 3;

    private static final int waitBeforeRetry = 100;

    // very small idle time
    private static final int idleTime = 2; // in millisecondi

    // Test Parameters !!
    private int end;
    private int expectedPutCount;

    /**
     * Costruttore per poter usare attributi private (e non public)
     *
     * @param end
     * @param expectedPutCount
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
                {-1, -1},
                {-1, 0},
                {0, 0},
                {200, 200},
                {200, 400},
                {400, 200},
                {3000, 1200}
        });
    }

    /**
     * Test setup. Create the static queue to be used by all tests
     */
    @BeforeClass
    public static void setUp() {
        listen = new CacheListenerImpl();
        queue = new CacheEventQueue(listen, 1L, "testCache1", maxFailure, waitBeforeRetry);
        queue.setWaitToDieMillis(idleTime);
    }

    /**
     * Adds put events to the queue.
     *
     * @throws Exception
     */
    @Test
    public void runPutTest() throws Exception {
        // TODO Rendere parametrici!!!
        int end = 200;
        int expectedPutCount = 200;

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

    }

    /**
     * Add remove events to the event queue.
     *
     * @throws Exception
     */
    @Test
    public void runRemoveTest() throws Exception {
        // TODO: rendere parametrici
        int end = 200;

        for (int i = 0; i <= end; i++) {
            queue.addRemoveEvent(i + ":key");
        }
    }

    /**
     * Stops processing the cache event queue
     */
    @Test
    public void runStopProcessingTest() {
        queue.stopProcessing();
    }

    /**
     * Test putting and a delay. Waits until queue is empty to start.
     *
     * @throws Exception
     */
    @Test
    public void runPutDelayTest() throws Exception {
        //TODO: Rendere parametrici
        int end = 100;
        int expectedPutCount = 100;

        while (!queue.isEmpty()) {
            synchronized (this) {
                System.out.println("queue is busy, waiting 250 millis to begin");
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

        while (!queue.isEmpty()) {
            synchronized (this) {
                System.out.println("queue is still busy, waiting 250 millis");
                this.wait(250);
            }
        }

        await().atMost(1000, TimeUnit.MILLISECONDS);
        System.out.println("queue is empty, comparing putCount");

        // this becomes less accurate with each test. It should never fail. If
        // it does things are very off.
        assertTrue("The put count [" + listen.putCount + "] is below the expected minimum threshold ["
                + expectedPutCount + "]", listen.putCount >= (expectedPutCount - 1));

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
