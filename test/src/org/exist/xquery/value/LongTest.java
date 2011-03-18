package org.exist.xquery.value;

import org.exist.xquery.XPathException;
import org.junit.Test;

/**
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class LongTest {
    
    @Test(expected=XPathException.class)
    public void testOver() throws XPathException {
        new IntegerValue("9223372036854775808", Type.LONG);
    }
    
    @Test
    public void testPositiveLimit() throws XPathException {
        new IntegerValue("9223372036854775807", Type.LONG);
    }
    
    @Test
    public void testNegativeLimit() throws XPathException {
        new IntegerValue("-9223372036854775808", Type.LONG);
    }
    
    @Test(expected=XPathException.class)
    public void testUnder() throws XPathException {
        new IntegerValue("-9223372036854775809", Type.LONG);
    }   
}
