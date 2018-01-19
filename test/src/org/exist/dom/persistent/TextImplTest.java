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

import com.googlecode.junittoolbox.ParallelRunner;
import org.easymock.EasyMock;
import org.exist.numbering.DLN;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.DOMException;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
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

    @Test
    public void setData() {
        final TextImpl text = new TextImpl("helloworld");
        assertEquals("helloworld", text.getTextContent());

        text.setData("worldhello");
        assertEquals("worldhello", text.getTextContent());
    }

    @Test
    public void setData_empty() {
        final TextImpl text = new TextImpl("helloworld");
        assertEquals("helloworld", text.getTextContent());

        text.setData("");
        assertEquals("", text.getTextContent());
    }

    @Test
    public void setData_shrink() {
        final TextImpl text = new TextImpl("helloworld");
        assertEquals("helloworld", text.getTextContent());

        text.setData("goodbye");
        assertEquals("goodbye", text.getTextContent());
    }

    @Test
    public void setData_expand() {
        final TextImpl text = new TextImpl("helloworld");
        assertEquals("helloworld", text.getTextContent());

        text.setData("thanksandgoodbye");
        assertEquals("thanksandgoodbye", text.getTextContent());
    }

    @Test
    public void appendData() {
        final TextImpl text = new TextImpl("hello");
        assertEquals("hello", text.getTextContent());

        text.appendData("world");
        assertEquals("helloworld", text.getTextContent());
    }

    @Test
    public void appendData_empty() {
        final TextImpl text = new TextImpl("hello");
        assertEquals("hello", text.getTextContent());

        text.appendData("");
        assertEquals("hello", text.getTextContent());
    }

    @Test
    public void insertData_start() {
        final TextImpl text = new TextImpl("hello");
        assertEquals("hello", text.getTextContent());

        text.insertData(0, "world");
        assertEquals("worldhello", text.getTextContent());
    }

    @Test
    public void insertData_middle() {
        final TextImpl text = new TextImpl("hello");
        assertEquals("hello", text.getTextContent());

        text.insertData(3, "world");
        assertEquals("helworldlo", text.getTextContent());
    }

    @Test
    public void insertData_end() {
        final TextImpl text = new TextImpl("hello");
        assertEquals("hello", text.getTextContent());

        text.insertData(5, "world");
        assertEquals("helloworld", text.getTextContent());
    }

    @Test(expected=DOMException.class)
    public void insertData_pastEnd() {
        final TextImpl text = new TextImpl("hello");
        assertEquals("hello", text.getTextContent());

        text.insertData(10, "world");
    }

    @Test
    public void insertData_empty() {
        final TextImpl text = new TextImpl("hello");
        assertEquals("hello", text.getTextContent());

        text.insertData(2,"");
        assertEquals("hello", text.getTextContent());
    }

    @Test
    public void replaceData_shrink() {
        final TextImpl text = new TextImpl("helloworld");
        assertEquals("helloworld", text.getTextContent());

        text.replaceData(1,7,"ok");
        assertEquals("hokld", text.getTextContent());
    }

    @Test
    public void replaceData_start() {
        final TextImpl text = new TextImpl("hello");
        assertEquals("hello", text.getTextContent());

        text.replaceData(0, 1,"world");
        assertEquals("worldello", text.getTextContent());
    }

    @Test
    public void replaceData_middle() {
        final TextImpl text = new TextImpl("hello");
        assertEquals("hello", text.getTextContent());

        text.replaceData(3, 1, "world");
        assertEquals("helworldo", text.getTextContent());
    }

    @Test
    public void replaceData_end() {
        final TextImpl text = new TextImpl("hello");
        assertEquals("hello", text.getTextContent());

        text.replaceData(4, 1, "world");
        assertEquals("hellworld", text.getTextContent());
    }

    @Test(expected=DOMException.class)
    public void replaceData_pastEnd() {
        final TextImpl text = new TextImpl("hello");
        assertEquals("hello", text.getTextContent());

        text.insertData(10, "world");
    }

    @Test
    public void replaceData_empty() {
        final TextImpl text = new TextImpl("hello");
        assertEquals("hello", text.getTextContent());

        text.replaceData(2,2,"");
        assertEquals("heo", text.getTextContent());
    }

    @Test
    public void replaceData_longArg() {
        final TextImpl text = new TextImpl("1230 North Ave. Dallas, Texas 98551");
        assertEquals("1230 North Ave. Dallas, Texas 98551", text.getTextContent());

        text.replaceData(0, 4, "260030");
        assertEquals("260030 North Ave. Dallas, Texas 98551", text.getTextContent());
    }

    @Test
    public void replaceData_untilEnd() {
        final TextImpl text = new TextImpl("1230 North Ave. Dallas, Texas 98551");
        assertEquals("1230 North Ave. Dallas, Texas 98551", text.getTextContent());

        text.replaceData(0, 50, "2600");
        assertEquals("2600", text.getTextContent());
    }
}
