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
package org.exist.xslt;

import java.io.File;
import java.io.FileInputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.memtree.SAXAdapter;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class XSLTStoJUnit extends SAXAdapter {

	public static void main(String[] args) throws Exception {

		File xslts = new File("test/external/XSLTS_1_1_0/catalog.xml");
		FileInputStream is = new FileInputStream(xslts);
		InputSource src = new InputSource(is);

		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);

		SAXParser parser = factory.newSAXParser();
		XMLReader reader = parser.getXMLReader();
		SAXAdapter adapter = new XSLTStoJUnit();
		reader.setContentHandler(adapter);
		reader.parse(src);

		System.out.println(adapter.getDocument());
	}

}
