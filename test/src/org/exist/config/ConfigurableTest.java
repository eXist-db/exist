/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2010 The eXist Project
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
package org.exist.config;

import static org.junit.Assert.*;

import java.io.InputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.util.ExistSAXParserFactory;
import org.exist.util.io.FastByteArrayInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@RunWith(ParallelRunner.class)
public class ConfigurableTest {

	String config1 = "<instance xmlns='http://exist-db.org/Configuration' " +
			"valueString=\"a\" " +
			"valueInt=\"5\" " +
			"valueboolean=\"true\" " +
			"valueBoolean=\"false\" " +
			">" +
			"<valueInteger>5</valueInteger> " +
			
			"<spice name='black pepper'/>" +
			"<spice name='berbere'/>" +
			
			"</instance>";

	String config2 = "<config xmlns='http://exist-db.org/Configuration' valueString=\"b\"><instance valueString=\"a\" valueInteger=\"5\"></instance></config>";

	String config3 = "<instance xmlns='http://exist-db.org/Configuration' " +
			"valueString=\"a\" " +
			"valueInt=\"5\" " +
			"valueboolean=\"true\" " +
			"valueBoolean=\"false\" " +
			">" +
			"<valueInteger>5</valueInteger> " +
			
			"<sp name='cool'/>" +

			"<spice name='black pepper'/>" +
			"<spice name='berbere'/>" +
			
			"</instance>";
	
	@Test
	public void simple() throws Exception {
		InputStream is = new FastByteArrayInputStream(config1.getBytes(UTF_8));
        
        Configuration config = Configurator.parse(is);
        
        ConfigurableObject object = new ConfigurableObject(config);
        
        assertEquals("a", object.some);
        
        assertEquals(Integer.valueOf(5), object.someInteger);
        assertTrue(object.simpleInteger == 5);
        assertTrue(object.defaultInteger == 3);

        assertTrue(object.someboolean);

        assertFalse(object.someBoolean);
        
        assertEquals(2, object.spices.size());
        
        assertEquals("black pepper", object.spices.get(0).name);
        assertEquals("berbere", object.spices.get(1).name);
	}

	@Test
	public void subelement() throws Exception {
		InputStream is = new FastByteArrayInputStream(config2.getBytes(UTF_8));
		
        // initialize xml parser
        // we use eXist's in-memory DOM implementation to work
        // around a bug in Xerces
        SAXParserFactory factory = ExistSAXParserFactory.getSAXParserFactory();
        factory.setNamespaceAware(true);
        InputSource src = new InputSource(is);
        SAXParser parser = factory.newSAXParser();
        XMLReader reader = parser.getXMLReader();
        SAXAdapter adapter = new SAXAdapter();
        reader.setContentHandler(adapter);
        reader.parse(src);
        
        ConfigurationImpl config = new ConfigurationImpl(adapter.getDocument().getDocumentElement());
        
        ConfigurableObject object = new ConfigurableObject(config);
        
        assertEquals("a", object.some);
        
        assertEquals(Integer.valueOf(5), object.someInteger);
	}
	
	@Test
	public void notSimple() throws Exception {
		InputStream is = new FastByteArrayInputStream(config3.getBytes(UTF_8));
        
        Configuration config = Configurator.parse(is);
        
        ConfigurableObject2 object = new ConfigurableObject2(config);
        
        assertEquals("a", object.some);
        
        assertEquals(Integer.valueOf(5), object.someInteger);
        assertTrue(object.simpleInteger == 5);
        assertTrue(object.defaultInteger == 3);

        assertTrue(object.someboolean);

        assertFalse(object.someBoolean);
        
        assertEquals("cool", object.sp.name);
	}

}
