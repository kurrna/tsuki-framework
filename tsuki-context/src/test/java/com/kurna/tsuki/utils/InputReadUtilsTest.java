package com.kurna.tsuki.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InputReadUtilsTest {

    @Test
    public void testReadStrings() {
        String path = "/com/kurna/scan/sub1/sub2/sub3/sub3.txt";
        assertEquals("sub1.sub2.sub3", InputReadUtils.readString(path));
    }
}
