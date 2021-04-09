package mytests;

import engine.EventQueueConcurrentLoadTest;
import org.apache.jcs.engine.CacheEventQueue;
import org.apache.jcs.engine.behavior.ICacheElement;
import org.apache.jcs.engine.behavior.ICacheListener;
import org.junit.Before;

import java.io.IOException;
import java.io.Serializable;

public class MyEventQueueConcurrentLoadTest {
    private static CacheEventQueue queue = null;

    private static MyEventQueueConcurrentLoadTest.CacheListenerImpl listen = null;

    private int maxFailure = 3;

    private int waitBeforeRetry = 100;

    // very small idle time
    private int idleTime = 2;


    /**
     * Test setup. Create the static queue to be used by all tests
     */
    @Before
    public void setUp() {
        listen = new MyEventQueueConcurrentLoadTest.CacheListenerImpl();
        queue = new CacheEventQueue(listen, 1L, "testCache1", maxFailure, waitBeforeRetry);

        queue.setWaitToDieMillis(idleTime);
    }

    /**
     * This is a dummy cache listener to use when testing the event queue.
     */
    private class CacheListenerImpl implements ICacheListener {

        /**
         * <code>putCount</code>
         */
        protected int putCount = 0;

        /**
         * <code>removeCount</code>
         */
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
