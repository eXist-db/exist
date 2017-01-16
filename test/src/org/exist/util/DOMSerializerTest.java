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

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.exist.util.serializer.DOMSerializer;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import static org.junit.Assert.assertNotNull;

/**
 * @author wolf
 *
 */
public class DOMSerializerTest {

    static Path existDir;
    static {
        String existHome = System.getProperty("exist.home");
		existDir = existHome == null ? Paths.get(".") : Paths.get(existHome);
		existDir = existDir.normalize();
    }
    private final static String file = existDir.resolve("samples/biblio.rdf").toAbsolutePath().toString();

	@Test
	public void serialize() throws ParserConfigurationException, IOException, SAXException, TransformerException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		assertNotNull(factory);
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		assertNotNull(builder);
		Document doc = builder.parse(new InputSource(file));
		assertNotNull(doc);
		StringWriter writer = new StringWriter();
		DOMSerializer serializer = new DOMSerializer(writer, null);
		serializer.serialize(doc.getDocumentElement());
	}
}
