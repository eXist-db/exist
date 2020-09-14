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

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class XMLUtilTest {

	private static final String utf8TestFileName = "utf8.xml";
	private static final String utf16TestFileName =  "utf16.xml";
	
	@Test
	public void testGetXMLDeclWithUTF8() throws IOException, URISyntaxException {
		final URL testFileUrl = getClass().getResource(utf8TestFileName);
		final Path testFile = Paths.get(testFileUrl.toURI());

		final String expectedDecl = "<?xml version=\"1.0\"?>";
		final String decl = XMLUtil.getXMLDecl(Files.readAllBytes(testFile));
		assertEquals("XML Declaration for the UTF-8 encode example file wasn't resolved properly", expectedDecl, decl);
	}

	@Test
	public void testGetXMLDeclWithUTF16() throws IOException, URISyntaxException {
		final URL testFileUrl = getClass().getResource(utf16TestFileName);
		final Path testFile = Paths.get(testFileUrl.toURI());

		final String expectedDecl = "<?xml version=\"1.0\" encoding=\"UTF-16\" standalone=\"no\"?>";
		final String decl = XMLUtil.getXMLDecl(Files.readAllBytes(testFile));
		assertEquals("XML Declaration for the UTF-16 encode example file wasn't resolved properly", expectedDecl, decl);
	}
}
