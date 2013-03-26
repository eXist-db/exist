/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.w3c.tests;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.Properties;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;

import junit.framework.Assert;

import org.custommonkey.xmlunit.Diff;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.dom.NodeProxy;
import org.exist.memtree.NodeImpl;
import org.exist.memtree.SAXAdapter;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.Configuration;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.LocalXMLResource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class TestCase {
	
    protected static BrokerPool db = null;
    protected static DBBroker broker = null;
    protected static Collection testCollection = null;

	public static final String testLocation = "test/external/";

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
//		System.out.println("setUpBeforeClass ENTERED");

	    Configuration configuration = new Configuration();
        BrokerPool.configure(1, 10, configuration);

        db = BrokerPool.getInstance();
        
        broker = db.get(db.getSecurityManager().getSystemSubject());
        Assert.assertNotNull(broker);

//		System.out.println("setUpBeforeClass PASSED");
	}

	public abstract void loadTS() throws Exception;
	
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
//      System.out.println("tearDownAfterClass ENTERED");

	    db.release(broker);

//		System.out.println("tearDownAfterClass PASSED");
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
//		System.out.println("setUp ENTERED");
		if (testCollection == null) {
			synchronized (db) {
				if (testCollection == null) {
					testCollection = broker.getCollection(getCollection());
					if (testCollection == null) {
						System.out.println("setUp no TS data");
						loadTS();
						System.out.println("setUp checking TS data");
	                    testCollection = broker.getCollection(getCollection());
						if (testCollection == null) {
							Assert.fail("There is no Test Suite data at database");
						}
					}
				}
			}
		}
//		System.out.println("setUp PASSED");
	}
	
	protected abstract XmldbURI getCollection();

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
		// System.out.println("tearDown PASSED");
	}

	public Exception catchError(Sequence result) {
		
		try {
			for(SequenceIterator i = result.iterate(); i.hasNext(); ) {
				Resource xmldbResource = getResource(i.nextItem());
				
				xmldbResource.getContent().toString();
			}
		} catch (Exception e) {
			return e;
		}
		return null;
	}

	public boolean compareResult(String testCase, String folder, Element outputFile, Sequence result) {
		if (outputFile == null)
			Assert.fail("no expected result information");

		File expectedResult = new File(testLocation+folder, outputFile.getNodeValue());
		if (!expectedResult.canRead()) Assert.fail("can't read expected result");
		
		String compare = outputFile.getAttribute("compare");
		if (compare == null) compare = "Fragment";
		compare = compare.toUpperCase();
		
		Reader reader = null;
		try {
			reader = new BufferedReader(new FileReader(expectedResult));

			if (result.isEmpty() && expectedResult.length() > 0)
				return false;
			
			int pos = 0;
			for(SequenceIterator i = result.iterate(); i.hasNext(); ) {
				Resource xmldbResource = getResource(i.nextItem());
				
//		        StringWriter writer = new StringWriter();
//		        Properties outputProperties = new Properties();
//		        outputProperties.setProperty("indent", "yes");
//		        SAXSerializer serializer = new SAXSerializer(writer, outputProperties);
//		        xmldbResource.getContentAsSAX(serializer);

				String res = xmldbResource.getContent().toString();
				
				int l;
				//expected result length is only one result
				if (result.getItemCount() == 1)
					l = (int) expectedResult.length();
				
				//caught-on length on last result
				else if (!i.hasNext())
					l = (int) expectedResult.length() - pos;
				
				else
					l = res.length();
				
				l += fixResultLength(testCase);
				
				int skipped = 0;
				char[] chars = new char[l];
				for (int x = 0; x < l; x++) {
					
					if (!reader.ready()) {
						skipped += l - x;
						break;
					}
					
					chars[x] = (char)reader.read();
					
					if (chars[x] == '\r') {
						chars[x] = (char)reader.read();
						pos++;
					}
					
					pos++;
				}
				
				if ( (result.getItemCount() == 1 || !i.hasNext() ) && skipped != 0) {
					char[] oldChars = chars; 
					chars = new char[l-skipped];
					System.arraycopy(oldChars, 0, chars, 0, l-skipped);
				}
				
				String expResult = String.copyValueOf(chars);
				
				boolean ok = false;
				
				if (compare.equals("XML")) {
					try {
						ok = diffXML(expResult, res);
					} catch (Exception e) {
					}
				}

				if (!ok) {
					if (!expResult.equals(res))
						if (compare.equals("FRAGMENT") || compare.equals("INSPECT")) {
							
							try {
								ok = diffXML(expResult, res);
							} catch (Exception e) {
							}
							
							if (!ok) { 
								//workaround problematic results
								if (expResult.equals("<?pi ?>") && (res.equals("<?pi?>")))
									;
								else
									return false;
							}
							
						} else {
							//workaround problematic results
							if (expResult.equals("&amp;") && res.equals("&"))
								;
							else if (expResult.equals("&lt;") && res.equals("<"))
								;
							else {
								//last try
								expResult = expResult.replaceAll("&lt;","<");
								expResult = expResult.replaceAll("&gt;",">");
								expResult = expResult.replaceAll("&amp;","&");
								if (!expResult.equals(res))
									return false;
							}
						}
						
					if ((compare.equals("TEXT") || compare.equals("FRAGMENT")) && (i.hasNext())) {
						reader.mark(1);
						if (' ' != (char)reader.read())
							reader.reset();
						else
							pos++;
					}
				}
			}
		} catch (Exception e) {
			return false;
		} finally {
			if (reader != null)
				try {
					reader.close();
				} catch (IOException e) {
				}
		}

		return true;
	}

	public int fixResultLength(String testCase) {
		return 0;
	}

	private boolean diffXML(String expResult, String res) throws SAXException, IOException {
		res = res.replaceAll("\n", "");
		res = res.replaceAll("\t", "");
		expResult = expResult.replaceAll("\n", "");
		expResult = expResult.replaceAll("\t", "");
		
		Diff diff = new Diff(expResult.trim(), res);
        if (!diff.identical()) {
        	System.out.println("expected:");
        	System.out.println(expResult);
        	System.out.println("get:");
        	System.out.println(res);
        	System.out.println(diff.toString());
            return false;
        }
        
        return true;
	}

    public final static String NORMALIZE_HTML = "normalize-html";

    protected final static Properties defaultProperties = new Properties();
    static {
        defaultProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
        defaultProperties.setProperty(OutputKeys.INDENT, "no");
        defaultProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes");
        defaultProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
        defaultProperties.setProperty(NORMALIZE_HTML, "yes");
    }

	public Resource getResource(Object r) throws XMLDBException {
		LocalCollection collection = null;
		Subject user = null;
		
		LocalXMLResource res = null;
		if (r instanceof NodeProxy) {
			NodeProxy p = (NodeProxy) r;
			res = new LocalXMLResource(user, db, collection, p);
		} else if (r instanceof Node) {
			res = new LocalXMLResource(user, db, collection, XmldbURI.EMPTY_URI);
			res.setContentAsDOM((Node)r);
		} else if (r instanceof AtomicValue) {
			res = new LocalXMLResource(user, db, collection, XmldbURI.EMPTY_URI);
			res.setContent(r);
		} else if (r instanceof LocalXMLResource)
			res = (LocalXMLResource) r;
		else
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "unknown object "+r.getClass());
		
		try {
			Field field = res.getClass().getDeclaredField("outputProperties");
			field.setAccessible(true);
			field.set(res, new Properties(defaultProperties));
		} catch (Exception e) {
		}
		return res;
	}
	
    public NodeImpl loadVarFromURI(XQueryContext context, String uri) throws IOException {
        SAXAdapter adapter = new SAXAdapter(context);

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        
        XMLReader xr;
		try {
			SAXParser parser = factory.newSAXParser();

			xr = parser.getXMLReader();
			xr.setContentHandler(adapter);
			xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);

		} catch (Exception e) {
			throw new IOException(e);
		}
        
        try {
//			URL url = new URL(uri);
//			InputStreamReader isr = new InputStreamReader(url.openStream(), "UTF-8");
			InputStreamReader isr = new InputStreamReader(new FileInputStream(uri), "UTF-8");
            InputSource src = new InputSource(isr);
            xr.parse(src);
            isr.close();
            
            adapter.getDocument().setDocumentURI(new File(uri).getAbsoluteFile().toString());
            
            return (NodeImpl) adapter.getDocument();
		} catch (SAXException e) {
			
			//workaround BOM
			if (e.getMessage().equals("Content is not allowed in prolog.")) {
	            try {
	            	String xml = readFileAsString(new File(uri));
	            	xml = xml.trim().replaceFirst("^([\\W]+)<","<");
	            	InputSource src = new InputSource(new StringReader(xml));
					xr.parse(src);
		            
					adapter.getDocument().setDocumentURI(new File(uri).getAbsoluteFile().toString());

		            return (NodeImpl) adapter.getDocument();
				} catch (SAXException e1) {
					throw new IOException(e);
				}
			}
			throw new IOException(e);
        }
    }

    public NodeImpl loadVarFromString(XQueryContext context, String source) throws IOException {
        SAXAdapter adapter = new SAXAdapter(context);

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        
        XMLReader xr;
		try {
			SAXParser parser = factory.newSAXParser();

			xr = parser.getXMLReader();
			xr.setContentHandler(adapter);
			xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);

		} catch (Exception e) {
			throw new IOException(e);
		}
        
        try {
            InputSource src = new InputSource(new StringReader(source));
            xr.parse(src);
            
            return (NodeImpl) adapter.getDocument();
		} catch (SAXException e) {
			throw new IOException(e);
        }
    }
	
	public static String readFileAsString(File file) throws IOException {
	    byte[] buffer = new byte[(int) file.length()];
	    FileInputStream f = new FileInputStream(file);
	    try {
	    	f.read(buffer);
	    	return new String(buffer);
	    } finally {
	    	f.close();
	    }
	}
	
	public static String readFileAsString(File file, long limit) throws IOException {
		if (file.length() >= limit) return "DATA TOO BIG";
		
	    byte[] buffer = new byte[(int) file.length()];
	    FileInputStream f = new FileInputStream(file);
	    try {
	    	f.read(buffer);
	    	return new String(buffer);
	    } finally {
	    	f.close();
	    }
	}

	public String sequenceToString(Sequence seq) {
		StringBuilder res = new StringBuilder();
		try {
			for(SequenceIterator i = seq.iterate(); i.hasNext(); ) {
				Resource resource = getResource(i.nextItem());
				res.append(resource.getContent().toString());
				if (i.hasNext()) res.append(" ");
				
				//avoid to big output
				if (res.length() >= 1024) return "{TOO BIG}";
			}
		} catch (Exception e) {
			res.append(e.getMessage());
		}
		return res.toString();
	}
}
