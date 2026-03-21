package com.kurna.tsuki.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileReadUtilsTest {

    @Test
    public void testReadStrings() {
        String path = "/com/kurna/scan/sub1/sub2/sub3/sub3.txt";
        assertEquals("sub1.sub2.sub3", FileReadUtils.readString(path));
    }
}
