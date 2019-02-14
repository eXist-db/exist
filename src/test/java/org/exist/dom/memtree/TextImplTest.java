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

package org.exist.dom.memtree;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.dom.QName;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Text;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
public class TextImplTest {

    @Test
    public void setData() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("helloworld");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbyeworld");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("helloworld", text.getTextContent());

        text.setData("worldhello");
        assertEquals("worldhello", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbyeworld", text2.getTextContent());
    }

    @Test
    public void setData_empty() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("helloworld");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbyeworld");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("helloworld", text.getTextContent());

        text.setData("");
        assertEquals("", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbyeworld", text2.getTextContent());
    }

    @Test
    public void setData_shrink() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("helloworld");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbyeworld");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("helloworld", text.getTextContent());

        text.setData("goodbye");
        assertEquals("goodbye", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbyeworld", text2.getTextContent());
    }

    @Test
    public void setData_expand() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("helloworld");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbyeworld");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text)doc.getDocumentElement().getFirstChild();
        assertEquals("helloworld", text.getTextContent());

        text.setData("thanksandgoodbye");
        assertEquals("thanksandgoodbye", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbyeworld", text2.getTextContent());
    }

    @Test
    public void appendData() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("hello");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbye");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("hello", text.getTextContent());

        text.appendData("world");
        assertEquals("helloworld", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbye", text2.getTextContent());

        text2.appendData("world");
        assertEquals("goodbyeworld", text2.getTextContent());
    }

    @Test
    public void appendData_empty() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("hello");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbye");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("hello", text.getTextContent());

        text.appendData("");
        assertEquals("hello", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbye", text2.getTextContent());

        text2.appendData("");
        assertEquals("goodbye", text2.getTextContent());
    }

    @Test
    public void insertData_start() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("hello");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbye");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("hello", text.getTextContent());

        text.insertData(0, "world");
        assertEquals("worldhello", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbye", text2.getTextContent());

        text2.insertData(0, "world");
        assertEquals("worldgoodbye", text2.getTextContent());
    }

    @Test
    public void insertData_middle() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("hello");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbye");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("hello", text.getTextContent());

        text.insertData(3, "world");
        assertEquals("helworldlo", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbye", text2.getTextContent());

        text2.insertData(4, "world");
        assertEquals("goodworldbye", text2.getTextContent());
    }

    @Test
    public void insertData_end() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("hello");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbye");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("hello", text.getTextContent());

        text.insertData(5, "world");
        assertEquals("helloworld", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbye", text2.getTextContent());

        text2.insertData(7, "world");
        assertEquals("goodbyeworld", text2.getTextContent());
    }

    @Test(expected=DOMException.class)
    public void insertData_pastEnd() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("hello");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbye");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("hello", text.getTextContent());

        text.insertData(10, "world");
    }

    @Test
    public void insertData_empty() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("hello");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbye");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("hello", text.getTextContent());

        text.insertData(2,"");
        assertEquals("hello", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbye", text2.getTextContent());

        text2.insertData(2, "");
        assertEquals("goodbye", text2.getTextContent());
    }

    @Test
    public void replaceData_shrink() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("helloworld");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbyeworld");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("helloworld", text.getTextContent());

        text.replaceData(1,7,"ok");
        assertEquals("hokld", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbyeworld", text2.getTextContent());
    }

    @Test
    public void replaceData_start() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("hello");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbye");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("hello", text.getTextContent());

        text.replaceData(0, 1,"world");
        assertEquals("worldello", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbye", text2.getTextContent());

        text2.replaceData(0, 2, "world");
        assertEquals("worldodbye", text2.getTextContent());
    }

    @Test
    public void replaceData_middle() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("hello");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbye");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("hello", text.getTextContent());

        text.replaceData(3, 1, "world");
        assertEquals("helworldo", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbye", text2.getTextContent());

        text2.replaceData(4, 2, "world");
        assertEquals("goodworlde", text2.getTextContent());
    }

    @Test
    public void replaceData_end() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("hello");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbye");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("hello", text.getTextContent());

        text.replaceData(4, 1, "world");
        assertEquals("hellworld", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbye", text2.getTextContent());

        text2.replaceData(6, 1, "world");
        assertEquals("goodbyworld", text2.getTextContent());
    }

    @Test(expected=DOMException.class)
    public void replaceData_pastEnd() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("hello");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbye");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("hello", text.getTextContent());

        text.insertData(10, "world");
    }

    @Test
    public void replaceData_empty() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("p", null, null), null);
        builder.characters("hello");
        builder.startElement(new QName("span", null, null), null);
        builder.characters("goodbye");
        builder.endElement();
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("hello", text.getTextContent());

        text.replaceData(2,2,"");
        assertEquals("heo", text.getTextContent());

        final Text text2 = (Text) doc.getDocumentElement().getElementsByTagName("span").item(0).getFirstChild();
        assertEquals("goodbye", text2.getTextContent());

        text2.replaceData(3, 2, "");
        assertEquals("gooye", text2.getTextContent());
    }

    @Test
    public void replaceData_longArg() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("address", null, null), null);
        builder.characters("1230 North Ave. Dallas, Texas 98551");
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("1230 North Ave. Dallas, Texas 98551", text.getTextContent());

        text.replaceData(0, 4, "260030");
        assertEquals("260030 North Ave. Dallas, Texas 98551", text.getTextContent());
    }

    @Test
    public void replaceData_untilEnd() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("address", null, null), null);
        builder.characters("1230 North Ave. Dallas, Texas 98551");
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("1230 North Ave. Dallas, Texas 98551", text.getTextContent());

        text.replaceData(0, 50, "2600");
        assertEquals("2600", text.getTextContent());
    }

    @Test
    public void deleteData() {
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        builder.startElement(new QName("address", null, null), null);
        builder.characters("1230 North Ave. Dallas, Texas 98551");
        builder.endElement();
        builder.endDocument();

        final Document doc = builder.getDocument();
        final Text text = (Text) doc.getDocumentElement().getFirstChild();
        assertEquals("1230 North Ave. Dallas, Texas 98551", text.getTextContent());

        text.deleteData(0, 16);
        assertEquals("Dallas, Texas 98551", text.getTextContent());
    }
}
