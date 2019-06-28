package org.exist.xquery.value;

import org.exist.xquery.XPathException;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class UnsignedIntTest {
    
    @Test(expected=XPathException.class)
    public void testOver() throws XPathException {
        new IntegerValue("4294967296", Type.UNSIGNED_INT);
    }
    
    @Test
    public void testPositiveLimit() throws XPathException {
        new IntegerValue("4294967295", Type.UNSIGNED_INT);
    }
    
    @Test
    public void testNegativeLimit() throws XPathException {
        new IntegerValue("0", Type.UNSIGNED_INT);
    }
    
    @Test(expected=XPathException.class)
    public void testUnder() throws XPathException {
        new IntegerValue("-1", Type.UNSIGNED_INT);
    }
}