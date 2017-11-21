package com.dtheng.iotexample;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Daniel Thengvall <fender5289@gmail.com>
 */
public class UtilsTest {

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void testExchangeRateArray() throws Exception {
        List<Integer> array = Utils.buildExchangeRateArray(8888.88d, 5).toList().toBlocking().single();

        assertTrue(array.size() == 5);

        assertArrayEquals(array.toArray(new Integer[array.size()]), new Integer[] {0, 8, 8, 8, 9});
    }
}
