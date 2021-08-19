package mytests;

import org.apache.jcs.JCS;
import org.apache.jcs.access.exception.CacheException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertNull;

@RunWith(Parameterized.class)
public class MyZeroSizeCacheUnitTest {
    /* Parametri del test */
    private final int items;
    private final String itemToRemove;

    public MyZeroSizeCacheUnitTest(int items, String itemToRemove) {
        this.items = items;
        this.itemToRemove = itemToRemove;
    }

    /**
     * Metodo configure richiesto. Viene chiamato da @Before setUp()
     */
    private void configure() {
        JCS.setConfigFilename("/TestZeroSizeCache.ccf");
        try {
            JCS.getInstance("testCache1");
        } catch (CacheException e) {
            e.printStackTrace();
        }
    }

    @Before
    public void setUp() {
        configure();
    }

    @Parameters(name = "{index}: input1={0} input2={1}")
    public static Collection<Object[]> inputData() {
        Integer[] start = new Integer[]{0, 20000, 1000};
        // Ottengo i nomi a partire dall'array di interi:  key:0, key:20000, ecc.
        Object[] toRemove = Arrays.stream(start).map(i -> String.format("%d:key", i)).toArray();

        return Arrays.asList(new Object[][]{
                {start[0], toRemove[0]}, // [zero elementi] : 0, "0:key"
                {start[1], toRemove[1]}, // [ultimo elemento] : 20000, "20000:key"
                {start[2], toRemove[2]}, // [ultimo elemento, meno elementi] : 1000, "1000:key"
                {start[0], toRemove[1]}, // [zero elementi, eliminazione elemento inesistente] : 0, "1000:key"
                {start[1], toRemove[2]}, // [elemento in mezzo] : 20000, "1000:key"
                {start[2], toRemove[0]}, // [primo elemento] : 1000, "0:key"
                {-1, "-1:key"},       // [nessun put/get, eliminazione elemento inesistente]
                {1, "😀:key"},        // inserisco un carattere non ASCII
                {1, ""},               // stringha vuota
        });
    }

    @Test
    public void testPutGetRemove() throws Exception {
        JCS jcs = JCS.getInstance("testCache1");
        for (int i = 0; i <= items; i++) {
            jcs.put(i + ":key", "data" + i);
        }

        // all the gets should be null
        for (int i = items; i >= 0; i--) {
            String res = (String) jcs.get(i + ":key");
            if (res == null) {
                assertNull("[" + i + ":key] should be null", res);
            }
        }

        // test removal, should be no exceptions
        jcs.remove(itemToRemove);

        // allow the shrinker to run
        await().atMost(Duration.ofMillis(500));


        // do it again.
        for (int i = 0; i <= items; i++) {
            jcs.put(i + ":key", "data" + i);
        }

        for (int i = items; i >= 0; i--) {
            String res = (String) jcs.get(i + ":key");
            if (res == null) {
                assertNull("[" + i + ":key] should be null", res);
            }
        }

        System.out.println(jcs.getStats());
    }
}
