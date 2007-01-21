/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.performance;

import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.AttrList;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.performance.actions.Action;
import org.exist.dom.QName;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.File;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.util.Properties;

public class TestResultWriter {

    private static final QName ROOT_ELEMENT = new QName("test-result", Namespaces.EXIST_NS, "");
    private static final QName ACTION_ELEMENT = new QName("action", Namespaces.EXIST_NS, "");
    private static final QName THREAD_ELEMENT = new QName("thread", Namespaces.EXIST_NS, "");
    private static final QName NAME_ATTRIB = new QName("name", "", "");
    private static final QName THREAD_ATTRIB = new QName("thread", "", "");
    private static final QName ELAPSED_ATTRIB = new QName("elapsed", "", "");
    private static final QName DESCRIPTION_ATTRIB = new QName("description", "", "");
    private static final QName ID_ATTRIB = new QName("id", "", "");
    private static final QName PARENT_ATTRIB = new QName("parent", "", "");
    private static final QName RESULT_ATTRIB = new QName("result", "", "");
    private static final QName GROUP_ELEMENT = new QName("group", Namespaces.EXIST_NS, "");

    private final static Properties defaultProperties = new Properties();

    static {
        defaultProperties.setProperty(OutputKeys.INDENT, "yes");
        defaultProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
    }

    private SAXSerializer serializer;
    private Writer writer;

    public TestResultWriter(String outFile) throws EXistException {
        File file = new File(outFile);
        try {
            writer = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(file)), "UTF-8");
            serializer = new SAXSerializer(writer, defaultProperties);
            serializer.startDocument();
            AttrList attribs = new AttrList();
            serializer.startElement(ROOT_ELEMENT, attribs);
        } catch (Exception e) {
            throw new EXistException("error while configuring test output file: " + file.getAbsolutePath(), e);
        }
    }

    public synchronized void report(Action action, String message, long elapsed) {
        AttrList attribs = new AttrList();
        attribs.addAttribute(THREAD_ATTRIB, Thread.currentThread().getName());
        attribs.addAttribute(NAME_ATTRIB, action.getClass().getName());
        attribs.addAttribute(ELAPSED_ATTRIB, Long.toString(elapsed));
        attribs.addAttribute(ID_ATTRIB, action.getId());
        if (action.getParent() != null && !(action.getParent() instanceof ActionThread))
            attribs.addAttribute(PARENT_ATTRIB, action.getParent().getId());
        if (action.getDescription() != null)
            attribs.addAttribute(DESCRIPTION_ATTRIB, action.getDescription());
        if (action.getLastResult() != null)
            attribs.addAttribute(RESULT_ATTRIB, action.getLastResult());
        try {
            serializer.startElement(ACTION_ELEMENT, attribs);
            if (message != null)
                serializer.characters(message);
            serializer.endElement(ACTION_ELEMENT);
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    public synchronized void threadStarted(ActionThread thread) {
        AttrList attribs = new AttrList();
        attribs.addAttribute(NAME_ATTRIB, thread.getName());
        try {
            serializer.startElement(THREAD_ELEMENT, attribs);
            serializer.endElement(ACTION_ELEMENT);
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    public synchronized void groupStart(Group group) {
        AttrList attribs = new AttrList();
        attribs.addAttribute(NAME_ATTRIB, group.getName());
        try {
            serializer.startElement(GROUP_ELEMENT, attribs);
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    public synchronized void groupEnd(Group group) {
        try {
            serializer.endElement(GROUP_ELEMENT);
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            serializer.endElement(ROOT_ELEMENT);
            writer.close();
        } catch (Exception e) {
        }
    }
}