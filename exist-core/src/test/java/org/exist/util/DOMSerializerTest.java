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
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;

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
import static org.exist.samples.Samples.SAMPLES;

/**
 * @author wolf
 *
 */
public class DOMSerializerTest {

	@Test
	public void serialize() throws ParserConfigurationException, IOException, SAXException, TransformerException, URISyntaxException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		assertNotNull(factory);
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		assertNotNull(builder);
		try (final InputStream is = SAMPLES.getBiblioSample()) {
			Document doc = builder.parse(new InputSource(is));
			assertNotNull(doc);
			try (final StringWriter writer = new StringWriter()) {
				DOMSerializer serializer = new DOMSerializer(writer, null);
				serializer.serialize(doc.getDocumentElement());
			}
		}
	}
}
