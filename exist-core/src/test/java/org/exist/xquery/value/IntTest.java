package org.exist.xquery.value;

import org.exist.xquery.XPathException;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class IntTest {
    
    @Test(expected=XPathException.class)
    public void testOver() throws XPathException {
        new IntegerValue("2147483648", Type.INT);
    }
    
    @Test
    public void testPositiveLimit() throws XPathException {
        new IntegerValue("2147483647", Type.INT);
    }
    
    @Test
    public void testNegativeLimit() throws XPathException {
        new IntegerValue("-2147483648", Type.INT);
    }
    
    @Test(expected=XPathException.class)
    public void testUnder() throws XPathException {
        new IntegerValue("-2147483649", Type.INT);
    }   
}
