package org.exist.xquery.value;

import org.exist.xquery.XPathException;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class ByteTest {
    
    @Test(expected=XPathException.class)
    public void testOver() throws XPathException {
        new IntegerValue("128", Type.BYTE);
    }
    
    @Test
    public void testPositiveLimit() throws XPathException {
        new IntegerValue("127", Type.BYTE);
    }
    
    @Test
    public void testNegativeLimit() throws XPathException {
        new IntegerValue("-128", Type.BYTE);
    }
    
    @Test(expected=XPathException.class)
    public void testUnder() throws XPathException {
        new IntegerValue("-129", Type.BYTE);
    }
}