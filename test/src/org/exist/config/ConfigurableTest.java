/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 *  $Id:$
 */
package org.exist.config;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.dom.ElementAtExist;
import org.exist.memtree.SAXAdapter;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;


/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ConfigurableTest {

	String config1 = "<instance value1=\"a\" value2=\"5\"></instance>";
	String config2 = "<config value1=\"b\"><instance value1=\"a\" value2=\"5\"></instance></config>";
	
	@Test
	public void simple() throws Exception {
		InputStream is = new ByteArrayInputStream(config1.getBytes("UTF-8"));
		
        // initialize xml parser
        // we use eXist's in-memory DOM implementation to work
        // around a bug in Xerces
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        InputSource src = new InputSource(is);
        SAXParser parser = factory.newSAXParser();
        XMLReader reader = parser.getXMLReader();
        SAXAdapter adapter = new SAXAdapter();
        reader.setContentHandler(adapter);
        reader.parse(src);
        
        ConfigElementImpl config = new ConfigElementImpl((ElementAtExist) adapter.getDocument().getDocumentElement());
        
        ConfigurableObject object = new ConfigurableObject(config);
        
        assertEquals("a", object.some);
        
        assertEquals(Integer.valueOf(5), object.someInteger);
	}

	@Test
	public void subelement() throws Exception {
		InputStream is = new ByteArrayInputStream(config2.getBytes("UTF-8"));
		
        // initialize xml parser
        // we use eXist's in-memory DOM implementation to work
        // around a bug in Xerces
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        InputSource src = new InputSource(is);
        SAXParser parser = factory.newSAXParser();
        XMLReader reader = parser.getXMLReader();
        SAXAdapter adapter = new SAXAdapter();
        reader.setContentHandler(adapter);
        reader.parse(src);
        
        ConfigElementImpl config = new ConfigElementImpl((ElementAtExist) adapter.getDocument().getDocumentElement());
        
        ConfigurableObject object = new ConfigurableObject(config);
        
        assertEquals("a", object.some);
        
        assertEquals(Integer.valueOf(5), object.someInteger);
	}
}
