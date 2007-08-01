/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2003-2007 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.util;

import java.io.File;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.exist.util.serializer.DOMSerializer;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import junit.framework.TestCase;

/**
 * @author wolf
 *
 */
public class DOMSerializerTest extends TestCase {
    
    static File existDir;
    static {
        String existHome = System.getProperty("exist.home");
        existDir = existHome==null ? new File(".") : new File(existHome);
    }
    private final static String file = (new File(existDir,"samples/biblio.rdf")).getAbsolutePath();
	public static void main(String[] args) {
		junit.textui.TestRunner.run(DOMSerializerTest.class);
	}

	public DOMSerializerTest(String name) {
		super(name);
	}
	
	public void testSerialize() {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			assertNotNull(factory);
			DocumentBuilder builder = factory.newDocumentBuilder();
			assertNotNull(builder);
			Document doc = builder.parse(new InputSource(file));
			assertNotNull(doc);
			StringWriter writer = new StringWriter();
			DOMSerializer serializer = new DOMSerializer(writer, null);
			serializer.serialize(doc.getDocumentElement());
			System.out.println(writer.toString());
        } catch (Exception e) {
            fail(e.getMessage());
        }
	}

}
