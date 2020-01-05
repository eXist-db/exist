package org.exist.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class FastStringBufferTest {

    @Test
    public void insertCharAt_begin(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.insertCharAt(0, '#');
        assertEquals("#12345", fsb.toString());
    }

    @Test
    public void insertCharAt_middle(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.insertCharAt(3, '#');
        assertEquals("123#45", fsb.toString());
    }

    @Test
    public void insertCharAt_end(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.insertCharAt(5, '#');
        assertEquals("12345#", fsb.toString());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void insertCharAt_IOOB_front(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.insertCharAt(-1, '#');
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void insertCharAt_IOOB_end(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.insertCharAt(6, '#');
    }

    @Test
    public void removeCharAt_front(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.removeCharAt(0);
        assertEquals("2345", fsb.toString());
    }

    @Test
    public void removeCharAt_middle(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.removeCharAt(2);
        assertEquals("1245", fsb.toString());
    }

    @Test
    public void removeCharAt_end(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.removeCharAt(4);
        assertEquals("1234", fsb.toString());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void removeCharAt_IOOB_front(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.removeCharAt(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void removeCharAt_IOOB_end(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.removeCharAt(5);
    }

    @Test
    public void insertCharAt_capacity(){
        FastStringBuffer fsb = new FastStringBuffer(5);
        assertEquals(0, fsb.length());
    }

}