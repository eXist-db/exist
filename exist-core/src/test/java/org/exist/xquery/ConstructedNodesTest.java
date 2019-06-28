/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2007-2009 The eXist Project
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
package org.exist.xquery;

import javax.xml.transform.OutputKeys;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

/**
 * Tests for various constructed node operations (in-memory nodes)
 * @author <a href="mailto:adam.retter@devon.gov.uk">Adam Retter</a>
 * @author ljo
 */
@RunWith(ParallelRunner.class)
public class ConstructedNodesTest {

	@ClassRule
	public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

	/**
	 * Iteratively constructs some nodes
	 */
    @Test
	public void iterateConstructNodes() throws XPathException, XMLDBException {
		String xquery =
				"declare variable $categories := \n" +
				"	<categories>\n" +
				"		<category uid=\"1\">Fruit</category>\n" +
				"		<category uid=\"2\">Vegetable</category>\n" +
				"		<category uid=\"3\">Meat</category>\n" +
				"		<category uid=\"4\">Dairy</category>\n" +
				"	</categories>\n" +
				";\n\n" + 
				
				"for $category in $categories/category return\n" +
				"	element option {\n" +
				"		attribute value {\n" +
				"			$category/@uid\n" +
				"		},\n" +
				"		text { $category }\n" +
				"	}";
		
		String expectedResults [] = { 
			"<option value=\"1\">Fruit</option>",
			"<option value=\"2\">Vegetable</option>",
			"<option value=\"3\">Meat</option>",
			"<option value=\"4\">Dairy</option>"
		};
		
		ResourceSet result = existEmbeddedServer.executeQuery(xquery);
			
        assertEquals(expectedResults.length, result.getSize());

        for(int i = 0; i < result.getSize(); i++)
        {
            assertEquals(expectedResults[i], result.getResource(i).getContent());
        }
	}
	
	/***
	 * Test sorting of constructed nodes
	 */
    @Test
	public void constructedNodesSort() throws XMLDBException {
		String xquery =
			"declare variable $categories := \n" +
			"	<categories>\n" +
			"		<category uid=\"1\">Fruit</category>\n" +
			"		<category uid=\"2\">Vegetable</category>\n" +
			"		<category uid=\"3\">Meat</category>\n" +
			"		<category uid=\"4\">Dairy</category>\n" +
			"	</categories>\n" +
			";\n\n" + 
			
			"for $category in $categories/category order by $category/@uid descending return $category";
		
		String expectedResults [] = { 
				"<category uid=\"4\">Dairy</category>",
				"<category uid=\"3\">Meat</category>",
				"<category uid=\"2\">Vegetable</category>",
				"<category uid=\"1\">Fruit</category>"
		};
		
		ResourceSet result = existEmbeddedServer.executeQuery(xquery);
			
        assertEquals(expectedResults.length, result.getSize());

        for(int i = 0; i < result.getSize(); i++)
        {
            assertEquals(expectedResults[i], result.getResource(i).getContent());
        }
	}
	
	/**
	 * Test retrieving sorted nodes by position
	 */
    @Test
	public void constructedNodesPosition() throws XMLDBException {
		String xquery =
			"declare variable $categories := \n" +
			"	<categories>\n" +
			"		<category uid=\"1\">Fruit</category>\n" +
			"		<category uid=\"2\">Vegetable</category>\n" +
			"		<category uid=\"3\">Meat</category>\n" +
			"		<category uid=\"4\">Dairy</category>\n" +
			"	</categories>\n" +
			";\n\n" + 
			
			"$categories/category[1],\n" +
			"$categories/category[position() eq 1]";
		
		String expectedResults [] = { 
				"<category uid=\"1\">Fruit</category>",
				"<category uid=\"1\">Fruit</category>"
		};
		
		ResourceSet result = existEmbeddedServer.executeQuery(xquery);
			
        assertEquals(expectedResults.length, result.getSize());

        for(int i = 0; i < result.getSize(); i++)
        {
            assertEquals(expectedResults[i], result.getResource(i).getContent());
        }
	}
	
	/**
	 * Test storing constructed (text) nodes
	 * Tests absence of bug #2646744 which gave err:XPTY0018 for $hello-text-first
	 * cf org.exist.xquery.XQueryTest.testXPTY0018_mixedsequences_2429093()
	 */
    @Test
	public void constructedTextNodes() throws XMLDBException {
		String xquery =
			"declare variable $hello-text-first := <a>{ \"hello\" }<b>world</b></a>;\n" +
			"declare variable $hello-text-last := <a><b>world</b>{ \"hello\" }</a>;\n" +
			"($hello-text-first, $hello-text-last)";
		
		String expectedResults [] = { 
				"<a>hello<b>world</b></a>",
				"<a><b>world</b>hello</a>"
				};

		final XQueryService xpathQueryService = (XQueryService) existEmbeddedServer.getRoot().getService("XQueryService", "1.0");

        String oki = xpathQueryService.getProperty(OutputKeys.INDENT);
		xpathQueryService.setProperty(OutputKeys.INDENT, "no");

        ResourceSet result = xpathQueryService.query(xquery);

        assertEquals(expectedResults.length, result.getSize());

        for(int i = 0; i < result.getSize(); i++)
        {
            assertEquals(expectedResults[i], result.getResource(i).getContent());
        }

        // Restore indent property to ensure test atomicity
		xpathQueryService.setProperty(OutputKeys.INDENT, oki);
	}
}
