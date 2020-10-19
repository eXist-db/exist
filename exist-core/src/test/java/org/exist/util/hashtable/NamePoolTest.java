/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util.hashtable;

import org.exist.dom.QName;
import org.exist.storage.ElementValue;
import org.junit.Test;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class NamePoolTest {

    @Test
    public void getSharedNameIdentical() throws Exception {
        NamePool pool = new NamePool();
        QName q1 = new QName("n1", "http://exist-db.org", "x");
        QName q2 = new QName("n1", "http://exist-db.org", "x");
        pool.getSharedName(q1);
        QName qr = pool.getSharedName(q2);
        assertSame(q1, qr);
    }

    @Test
    public void getSharedNameDifferentNamespace() throws Exception {
        NamePool pool = new NamePool();
        QName q1 = new QName("n1", "http://exist-db.COM", "x");
        QName q2 = new QName("n1", "http://exist-db.org", "x");
        pool.getSharedName(q1);
        QName qr = pool.getSharedName(q2);
        assertNotSame(q1, qr);
    }

    @Test
    public void getSharedNameDifferentPrefix() throws Exception {
        NamePool pool = new NamePool();
        QName q1 = new QName("n1", "http://exist-db.org", "x");
        QName q2 = new QName("n2", "http://exist-db.org", "x");
        pool.getSharedName(q1);
        QName qr = pool.getSharedName(q2);
        assertNotSame(q1, qr);
    }

    @Test
    public void getSharedNameDifferentType() throws Exception {
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