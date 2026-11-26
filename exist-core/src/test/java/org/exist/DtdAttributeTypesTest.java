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
package org.exist;

import org.exist.dom.persistent.AttrImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DtdAttributeTypesTest {

    @Test
    void everyDeclarationTypesTheAttributeButOnlyIdTypesAreWritten() {
        final DtdAttributeTypes types = new DtdAttributeTypes();
        types.declare("c", "id", "ID");
        types.declare("c", "ref", "IDREF");
        types.declare("c", "refs", "IDREFS");
        types.declare("c", "kind", "(a|b)");
        types.declare("c", "name", "CDATA");
        types.declare("c", "tok", "NMTOKEN");

        assertEquals(AttrImpl.ID, types.typeOf("c", "id"));
        assertEquals(AttrImpl.IDREF, types.typeOf("c", "ref"));
        assertEquals(AttrImpl.IDREFS, types.typeOf("c", "refs"));
        assertEquals(AttrImpl.CDATA, types.typeOf("c", "kind"));
        assertEquals(AttrImpl.CDATA, types.typeOf("c", "name"));
        assertEquals(AttrImpl.CDATA, types.typeOf("c", "tok"));
        assertEquals(DtdAttributeTypes.NOT_DECLARED, types.typeOf("c", "undeclared"));
        assertEquals("<!ATTLIST c id ID #IMPLIED><!ATTLIST c ref IDREF #IMPLIED><!ATTLIST c refs IDREFS #IMPLIED>", types.toInternalSubset(false));
    }

    @Test
    void otherTypesAreWrittenAsCdataWhenAnExternalSubsetCouldDeclareThemAsIds() {
        final DtdAttributeTypes types = new DtdAttributeTypes();
        types.declare("c", "id", "ID");
        types.declare("c", "kind", "(a|b)");
        types.declare("c", "tok", "NMTOKEN");

        assertEquals("<!ATTLIST c id ID #IMPLIED><!ATTLIST c kind CDATA #IMPLIED><!ATTLIST c tok CDATA #IMPLIED>", types.toInternalSubset(true));
    }

    @Test
    void theFirstDeclarationOfAnAttributeWins() {
        final DtdAttributeTypes types = new DtdAttributeTypes();
        types.declare("c", "a", "IDREF");
        types.declare("c", "a", "ID");
        types.declare("c", "a", "NMTOKEN");

        assertEquals(AttrImpl.IDREF, types.typeOf("c", "a"));
        assertEquals("<!ATTLIST c a IDREF #IMPLIED>", types.toInternalSubset(false));
    }

    @Test
    void anAttributeIsDeclaredPerElement() {
        final DtdAttributeTypes types = new DtdAttributeTypes();
        types.declare("c", "key", "ID");
        types.declare("d", "key", "IDREF");

        assertEquals(AttrImpl.ID, types.typeOf("c", "key"));
        assertEquals(AttrImpl.IDREF, types.typeOf("d", "key"));
        assertEquals(DtdAttributeTypes.NOT_DECLARED, types.typeOf("e", "key"));
    }

    @Test
    void theDeclarationsAreWrittenInTheOrderTheyWereMade() {
        final DtdAttributeTypes types = new DtdAttributeTypes();
        types.declare("c", "a", "ID");
        types.declare("d", "b", "IDREF");
        types.declare("c", "e", "IDREFS");

        assertEquals("<!ATTLIST c a ID #IMPLIED><!ATTLIST d b IDREF #IMPLIED><!ATTLIST c e IDREFS #IMPLIED>", types.toInternalSubset(false));
    }

    @Test
    void namesAreKeptAsWrittenWithTheirPrefix() {
        final DtdAttributeTypes types = new DtdAttributeTypes();
        types.declare("p:c", "p:id", "ID");

        assertEquals(AttrImpl.ID, types.typeOf("p:c", "p:id"));
        assertEquals("<!ATTLIST p:c p:id ID #IMPLIED>", types.toInternalSubset(false));
    }

    @Test
    void nothingToWrite() {
        final DtdAttributeTypes none = new DtdAttributeTypes();
        assertNull(none.toInternalSubset(false));
        assertNull(none.toInternalSubset(true));
        assertEquals(DtdAttributeTypes.NOT_DECLARED, none.typeOf("c", "tok"));

        final DtdAttributeTypes other = new DtdAttributeTypes();
        other.declare("c", "tok", "NMTOKEN");
        assertNull(other.toInternalSubset(false), "no ID, and no external DTD for it to override");
    }
}
