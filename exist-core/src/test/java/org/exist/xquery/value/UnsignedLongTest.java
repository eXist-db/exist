package org.exist.xquery.value;

import org.exist.xquery.XPathException;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class UnsignedLongTest {
    
    @Test(expected=XPathException.class)
    public void testOver() throws XPathException {
        new IntegerValue("18446744073709551616", Type.UNSIGNED_LONG);
    }
    
    @Test
    public void testPositiveLimit() throws XPathException {
        new IntegerValue("18446744073709551615", Type.UNSIGNED_LONG);
    }
    
    @Test
    public void testNegativeLimit() throws XPathException {
        new IntegerValue("0", Type.UNSIGNED_LONG);
    }
    
    @Test(expected=XPathException.class)
    public void testUnder() throws XPathException {
        new IntegerValue("-1", Type.UNSIGNED_LONG);
    }
}