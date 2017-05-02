/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.dom.persistent;

import org.easymock.EasyMock;
import org.exist.numbering.DLN;
import org.junit.Test;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by aretter on 25/04/2017.
 */
public class TextImplTest {

    @Test
    public void isSameNode_sameText() {
        final DocumentImpl doc = EasyMock.createMock(DocumentImpl.class);
        expect(doc.getDocId()).andReturn(21).times(2);

        replay(doc);

        final TextImpl text = new TextImpl("hello");
        text.setOwnerDocument(doc);
        text.setNodeId(new DLN("1.2.1"));

        assertTrue(text.isSameNode(text));

        verify(doc);
    }

    @Test
    public void isSameNode_differentText() {
        final DocumentImpl doc = EasyMock.createMock(DocumentImpl.class);

        replay(doc);

        final TextImpl text = new TextImpl("hello");
        text.setOwnerDocument(doc);
        text.setNodeId(new DLN("1.2.1"));

        final TextImpl text2 = new TextImpl("hello");
        text2.setOwnerDocument(doc);
        text2.setNodeId(new DLN("1.7.9"));

        assertFalse(text.isSameNode(text2));

        verify(doc);
    }

    @Test
    public void isSameNode_differentTextDifferentDoc() {
        final DocumentImpl doc = EasyMock.createMock(DocumentImpl.class);
        expect(doc.getDocId()).andReturn(21);

        final DocumentImpl doc2 = EasyMock.createMock(DocumentImpl.class);
        expect(doc2.getDocId()).andReturn(67);

        replay(doc, doc2);

        final TextImpl text = new TextImpl("hello");
        text.setOwnerDocument(doc);
        text.setNodeId(new DLN("1.2.1"));

        final TextImpl text2 = new TextImpl("hello");
        text2.setOwnerDocument(doc2);
        text2.setNodeId(new DLN("1.2.1"));

        assertFalse(text.isSameNode(text2));

        verify(doc, doc2);
    }

    @Test
    public void isSameNode_nonText() {
        final DocumentImpl doc = EasyMock.createMock(DocumentImpl.class);

        replay(doc);

        final TextImpl text = new TextImpl("hello");
        text.setOwnerDocument(doc);
        text.setNodeId(new DLN("1.2.1"));

        final ElementImpl elem = new ElementImpl();
        elem.setOwnerDocument(doc);
        elem.setNodeId(new DLN("1.2"));

        assertFalse(text.isSameNode(elem));

        verify(doc);
    }
}
