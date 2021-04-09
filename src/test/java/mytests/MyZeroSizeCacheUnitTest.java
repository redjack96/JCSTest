package mytests;

import org.apache.jcs.JCS;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertNull;

@RunWith(Parameterized.class)
public class MyZeroSizeCacheUnitTest {

    // private static int items = 20000;

    @Parameter(0)
    public int items;
    @Parameter(1)
    public String itemToRemove;

    @Before
    public void setUp() throws Exception {
        JCS.setConfigFilename("/TestZeroSizeCache.ccf");
        JCS.getInstance("testCache1");
    }

    @Parameters(name = "{index}: input1={0} input2={1}")
    public static Collection<Object[]> inputData() {
        int[] start = new int[]{0, 20000, 1000};
        String postfix = ":key";
        String[] name = new String[]{start[0]+postfix, start[1]+postfix, start[2]+postfix};

        return Arrays.asList(new Object[][]{
                {start[0], name[0]},
                {start[1], name[1]},
                {start[2], name[2]},
                {start[0], name[1]}, // ok
                {start[1], name[2]}, // ok
                {start[2], name[0]}, // ok
                {-1, "-1:key"}, // ok
                {-2, "-2:key"}, // ok
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
        Thread.sleep(500);


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
