package org.exist.xquery.value;

import org.exist.xquery.XPathException;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class ShortTest {
    
    @Test(expected=XPathException.class)
    public void testOver() throws XPathException {
        new IntegerValue("32768", Type.SHORT);
    }
    
    @Test
    public void testPositiveLimit() throws XPathException {
        new IntegerValue("32767", Type.SHORT);
    }
    
    @Test
    public void testNegativeLimit() throws XPathException {
        new IntegerValue("-32768", Type.SHORT);
    }
    
    @Test(expected=XPathException.class)
    public void testUnder() throws XPathException {
        new IntegerValue("-32769", Type.SHORT);
    }   
}
