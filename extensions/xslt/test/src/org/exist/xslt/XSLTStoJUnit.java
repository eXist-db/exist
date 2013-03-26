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
package org.exist.xslt;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Vector;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class XSLTStoJUnit implements ContentHandler {

	private static final String TESTCASES = "test-group";
	private static final String TESTCASE = "testcase";
	
	private static final String NAME = "name";

//	private static final String INPUT = "input";
	private static final String STYLESHEET = "stylesheet";
	private static final String SOURCE_DOCUMENT = "source-document";

//	private static final String OUTPUT = "output";
	private static final String RESULT_DOCUMENT = "result-document";
	
	private static final String ERROR = "error";
	private static final String ERROR_ID = "error-id";

	private static File folder;
    private String sep = File.separator;
    
    private BufferedWriter out;
    private BufferedWriter outAll;
    
    private boolean first = true;

	private Vector<String> currentPath = new Vector<String>();
	
	private String name;
	private String stylesheet;
	private String sourceDocument;
	private String resultDocument;
	private String errors;
	
	public static void main(String[] args) throws Exception {

		folder = new File("extensions/xslt/test/src/org/exist/xslt/xslts_1_1_0");
		
		File xslts = new File("test/external/XSLTS_1_1_0/catalog.xml");
		FileInputStream is = new FileInputStream(xslts);
		InputSource src = new InputSource(is);

		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);

		SAXParser parser = factory.newSAXParser();
		XMLReader reader = parser.getXMLReader();
		reader.setEntityResolver(new SpecialEntityResolver("test/external/XSLTS_1_1_0/"));
		XSLTStoJUnit adapter = new XSLTStoJUnit();
		reader.setContentHandler(adapter);

		reader.parse(src);
	}

	public void characters(char[] ch, int start, int length) throws SAXException {
		if (currentPath.lastElement().equals(NAME)) {
            StringBuilder s = new StringBuilder(name);
            s.append(ch, start, length);
			name = new String(s);
		}
	}

	public void endDocument() throws SAXException {
		try {
			outAll.write("\n})\n\n"+
					"public class AllTests {\n\n" +
					"}");
			outAll.close();
		} catch (IOException e) {
			throw new SAXException(e);
		}
	}

	public void endElement(String uri, String localName, String qName) throws SAXException {
		currentPath.remove(currentPath.size()-1);
		
		if (localName.equals(TESTCASES)) {
			try {
				endTestFile();
			} catch (IOException e) {
				throw new SAXException(e);
			}
		} else if (localName.equals(TESTCASE)) {
				try {
					writeTestCase();
				} catch (IOException e) {
					throw new SAXException(e);
				}
		}
	}

	private void writeTestCase() throws IOException {
		out.write("	/* "+name+" */\n" +
		"	@Test\n" +
		"	public void test_"+adoptString(name)+"() throws Exception {\n" +
		"		testCase(\""+sourceDocument+"\", \""+stylesheet+"\", \""+resultDocument+"\", \""+errors+"\");\n"+
		"	}\n\n");
	}

	public void endPrefixMapping(String prefix) throws SAXException {
	}

	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
	}

	public void processingInstruction(String target, String data) throws SAXException {
	}

	public void setDocumentLocator(Locator locator) {
	}

	public void skippedEntity(String name) throws SAXException {
	}

	public void startDocument() throws SAXException {
   	    try {
   	   		File jTest = new File(folder.getAbsolutePath()+sep+"AllTests.java");

   	   		FileWriter fstream = new FileWriter(jTest.getAbsoluteFile());
   	   		outAll = new BufferedWriter(fstream);

   	   	    outAll.write("package org.exist.xslt.xslts_1_1_0;\n\n" +
					"import org.junit.runner.RunWith;\n" +
					"import org.junit.runners.Suite;\n\n" +
					"@RunWith(Suite.class)\n" +
					"@Suite.SuiteClasses({\n");
   	   	    
   	   	    first = true;
		} catch (IOException e) {
			throw new SAXException(e);
		}
	}

	public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
		currentPath.add(localName);
		
		if (localName.equals(TESTCASES)) {
			try {
				newTestFile(adoptString(atts.getValue("name")));
			} catch (IOException e) {
				throw new SAXException(e);
			}
			
		} else if (localName.equals(TESTCASE)) {
			name = "";
			stylesheet = "";
			sourceDocument = "";
			resultDocument = "";
			errors = "";
			
		} else if (localName.equals(STYLESHEET)) {
			stylesheet = atts.getValue("file");
			
		} else if (localName.equals(SOURCE_DOCUMENT)) {
			sourceDocument = atts.getValue("file");
			
		} else if (localName.equals(RESULT_DOCUMENT)) {
			resultDocument = atts.getValue("file");
			
		} else if (localName.equals(ERROR)) {
			errors += atts.getValue(ERROR_ID)+" ";
			
		}
		
	}

	private void newTestFile(String name) throws IOException {
   		File jTest = new File(folder.getAbsolutePath()+sep+name+".java");

   		FileWriter fstream = new FileWriter(jTest.getAbsoluteFile());
   	    out = new BufferedWriter(fstream);
   	    
   	    out.write("package org.exist.xslt.xslts_1_1_0;\n\n"+
//   	    		"import org.exist.xquery.xqts.XQTS_case;\n" +
//   	    		"import static org.junit.Assert.*;\n" +
   	    		"import org.exist.xslt.XSLTS_case;\n" +
   	    		"import org.junit.Test;\n\n" +
   	    		"public class "+name+" extends XSLTS_case {\n");

   	    if (first) {
   	    	outAll.write("	"+name+".class");
   	    	first = false;
   	    } else {
   	    	outAll.write(",\n	"+name+".class");
   	    }
	}

	private void endTestFile() throws IOException {
		out.write("}");
		out.close();
	}


	public void startPrefixMapping(String prefix, String uri) throws SAXException {
	}

	private String adoptString(String caseName) {
		if (caseName.equals("for"))
			return "_for_";
		else if (caseName.equals("if"))
			return "_if_";

		String result = caseName.replace("-", "_");
		result = result.replace(".", "_");
		return result;
	}
}
