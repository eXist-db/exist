package org.exist.util.hashtable;

import org.exist.dom.QName;
import org.exist.storage.ElementValue;
import org.junit.Test;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class NamePoolTest {

    @Test
    public void getSharedCommon() throws Exception {
        NamePool pool = new NamePool();
        QName q1 = new QName("n1", "http://exist-db.org", "x");
        QName q2 = new QName("n1", "http://exist-db.org", "x");
        pool.getSharedName(q1);
        QName qr = pool.getSharedName(q2);
        assertSame(q1, qr);
    }

    @Test
    public void getSharedType() throws Exception {
        NamePool pool = new NamePool();
        QName q1 = new QName("n1", "http://exist-db.org", "x", ElementValue.ELEMENT);
        QName q2 = new QName("n1", "http://exist-db.org", "x", ElementValue.ATTRIBUTE);
        pool.getSharedName(q1);
        QName qr = pool.getSharedName(q2);
        assertNotSame(q1, qr);

        QName q3 = new QName("n1", "http://exist-db.org", "x", ElementValue.ELEMENT);
        qr = pool.getSharedName(q3);
        assertSame(q1, qr);
    }
}