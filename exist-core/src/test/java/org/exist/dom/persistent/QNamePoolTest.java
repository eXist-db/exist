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
package org.exist.dom.persistent;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.storage.ElementValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link QNamePool#add} must hand back a QName with exactly the name type and prefix asked for.
 * {@link QName#equals(QName)} compares only namespace and local name, so a pool that used it to
 * detect duplicates could return an attribute QName for an element of the same name, or a QName
 * with another prefix, whenever the probe sequence met that entry.
 */
public class QNamePoolTest {

    /**
     * Names added to one pool. A pair of names differing only in type or prefix never hashes to the
     * same slot, so the mistake shows only once the pool is full enough for a probe sequence to run
     * through the other name's slot, as it is on a running server. With these names the first
     * wrong answer comes at 214 pairs for the prefix, 341 for an element and 379 for an attribute;
     * 380 pairs fill 760 of the default pool's 769 slots without overflowing it.
     */
    private static final int NAMES = 380;

    @Test
    public void addKeepsNameTypeWhenAttributeOfSameNameIsPooled() {
        final QNamePool pool = new QNamePool();
        for (int i = 0; i < NAMES; i++) {
            pool.add(ElementValue.ATTRIBUTE, "", "name" + i, null);
        }
        for (int i = 0; i < NAMES; i++) {
            final QName element = pool.add(ElementValue.ELEMENT, "", "name" + i, null);
            assertEquals(ElementValue.ELEMENT, element.getNameType(), "name type of element 'name" + i + "'");
        }
    }

    @Test
    public void addKeepsNameTypeWhenElementOfSameNameIsPooled() {
        final QNamePool pool = new QNamePool();
        for (int i = 0; i < NAMES; i++) {
            pool.add(ElementValue.ELEMENT, "", "name" + i, null);
        }
        for (int i = 0; i < NAMES; i++) {
            final QName attribute = pool.add(ElementValue.ATTRIBUTE, "", "name" + i, null);
            assertEquals(ElementValue.ATTRIBUTE, attribute.getNameType(), "name type of attribute 'name" + i + "'");
        }
    }

    @Test
    public void addKeepsPrefixWhenAnotherPrefixIsPooled() {
        final QNamePool pool = new QNamePool();
        for (int i = 0; i < NAMES; i++) {
            pool.add(ElementValue.ATTRIBUTE, Namespaces.XML_NS, "name" + i, "xm");
        }
        for (int i = 0; i < NAMES; i++) {
            final QName qname = pool.add(ElementValue.ATTRIBUTE, Namespaces.XML_NS, "name" + i, "xml");
            assertEquals("xml", qname.getPrefix(), "prefix of attribute 'name" + i + "'");
        }
    }

    @Test
    public void addReturnsThePooledInstanceForAnIdenticalName() {
        final QNamePool pool = new QNamePool();
        final QName first = pool.add(ElementValue.ELEMENT, Namespaces.XML_NS, "id", "xml");

        assertSame(first, pool.add(ElementValue.ELEMENT, Namespaces.XML_NS, "id", "xml"));
        assertSame(first, pool.get(ElementValue.ELEMENT, Namespaces.XML_NS, "id", "xml"));
    }
}
