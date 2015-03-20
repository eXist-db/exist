/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.exist.storage.BrokerPool;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XPathQueryService;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XSLTestCase {

	private final static String URI = XmldbURI.LOCAL_DB;
	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
	private final static String XSLT_COLLECTION = "xslt_tests";

    static File existDir = new File(".");

	private Collection col = null;

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		try {
			Class<?> cl = Class.forName(DRIVER);
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			col = DatabaseManager.getCollection(URI + "/" + XSLT_COLLECTION);
			if (col == null) {
				Collection root = DatabaseManager.getCollection(URI);
				CollectionManagementService mgtService =
					(CollectionManagementService) root.getService(
						"CollectionManagementService",
						"1.0");
				col = mgtService.createCollection(XSLT_COLLECTION);
			}
			
			BrokerPool.getInstance().getConfiguration().setProperty(
					TransformerFactoryAllocator.PROPERTY_TRANSFORMER_CLASS, 
					"org.exist.xslt.TransformerFactoryImpl");
			
			loadBench("test/src/org/exist/xslt/test/bench/v1_0", bench);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Map<String, Map<String, String>> bench = new TreeMap<String, Map<String, String>>();
	
	private void loadBench(String benchLocation, Map<String, Map<String, String>> bench) throws Exception {
        File testConf = new File(benchLocation+"/default.conf");
		if (testConf.canRead()) { 
			// Open the file and then get a channel from the stream
			FileInputStream fis = new FileInputStream(testConf);
			try {
    			FileChannel fc = fis.getChannel();
    
    			// Get the file's size and then map it into memory
    			int sz = (int)fc.size();
    			MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, sz);
    
    			// Charset and decoder for ISO-8859-15
    			Charset charset = Charset.forName("ISO-8859-15");
    			CharsetDecoder decoder = charset.newDecoder();
    		    
    		    // Decode the file into a char buffer
    			CharBuffer cb = decoder.decode(bb);
    
    			// Perform the search
    			loadBench(testConf, cb, bench);
    
    			// Close the channel and the stream
    			fc.close();
			} finally {
			    fis.close();
			}
		}
	}

	private void loadBench(File testConf, CharBuffer cb, Map<String, Map<String, String>> bench) {
		// Pattern used to parse lines
		Pattern linePattern = Pattern.compile(".*\r?\n");

		String testName = null;
		Map<String, String> testInfo = null;
		int position;
		
		Matcher lm = linePattern.matcher(cb);	// Line matcher
//		int lines = 0;
		while (lm.find()) {
//			lines++;
			CharSequence cs = lm.group(); 	// The current line
			String str = cs.toString();
			
			if (cs.charAt(0) == (char)0x005B) {
				position = str.indexOf("]");
				testName = str.substring(1, position);
				
				if (bench.containsKey(testName)) {
					testInfo = bench.get(testName);
				} else {
					testInfo = new HashMap<String, String>();
					bench.put(testName, testInfo);
				}
					
			} else if (testName != null){
				position = str.indexOf("=");
				if (position != -1) {
					String key = str.substring(0, position).trim(); 
					String value = str.substring(position+1).trim();
					testInfo.put(key, value);
				}
			}

			if (lm.end() == cb.limit())
				break;
		}
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	@Test
    public void testSimpleTransform() {
        try {
            XPathQueryService service = (XPathQueryService) col.getService("XPathQueryService", "1.0");

            String query =
                "xquery version \"1.0\";\n" +
                "declare namespace transform=\"http://exist-db.org/xquery/transform\";\n" +
                "declare variable $xml {\n" +
                "	<node xmlns=\"http://www.w3.org/1999/xhtml\">text</node>\n" +
                "};\n" +
                "declare variable $xslt {\n" +
                "	<xsl:stylesheet xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">\n" +
                "		<xsl:template match=\"node\">\n" +
                "			<div><xsl:value-of select=\".\"/></div>\n" +
                "		</xsl:template>\n" +
                "	</xsl:stylesheet>\n" +
                "};\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                "	<body>\n" +
                "		{transform:transform($xml, $xslt, ())}\n" +
                "	</body>\n" +
                "</html>";

            ResourceSet result = service.query(query);

            //check there is one result
            assertEquals(1, result.getSize());

            String content = (String) result.getResource(0).getContent();
//            System.out.println(content);

            //check the namespace
            assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

            //check the content
            assertTrue(content.indexOf("<div>text</div>") > -1);
        } catch (XMLDBException e) {
//            fail(e.getMessage());
            throw new RuntimeException(e);
        }
    }
	
	@Test
    public void testComplexTransform() throws Exception {
        try {
            XPathQueryService service = (XPathQueryService) col.getService("XPathQueryService", "1.0");

            String query =
                    "xquery version \"1.0\";\n" +
                    "declare namespace transform=\"http://exist-db.org/xquery/transform\";\n" +
                    "declare variable $xml {\n" +
                    "<salesdata>\n" +
                    " <year>\n" +
                    "  <year>1997</year>\n" +
                  	"  <region>\n" +
                  	"   <name>west</name>\n" +
                    "   <sales unit=\"millions\">32</sales>\n" +
                    "  </region>\n" +
                  	"  <region>\n" +
                  	"   <name>central</name>\n" +
                    "   <sales unit=\"millions\">11</sales>\n" +
                    "  </region>\n" +
                  	"  <region>\n" +
                  	"   <name>east</name>\n" +
                    "   <sales unit=\"millions\">19</sales>\n" +
                    "  </region>\n" +
                    " </year>\n" +
                    " <year>\n" +
                    "  <year>1998</year>\n" +
                  	"  <region>\n" +
                  	"   <name>west</name>\n" +
                    "   <sales unit=\"millions\">35</sales>\n" +
                    "  </region>\n" +
                  	"  <region>\n" +
                  	"   <name>central</name>\n" +
                    "   <sales unit=\"millions\">12</sales>\n" +
                    "  </region>\n" +
                  	"  <region>\n" +
                  	"   <name>east</name>\n" +
                    "   <sales unit=\"millions\">25</sales>\n" +
                    "  </region>\n" +
                    " </year>\n" +
                    " <year>\n" +
                    "  <year>1999</year>\n" +
                  	"  <region>\n" +
                  	"   <name>west</name>\n" +
                    "   <sales unit=\"millions\">36</sales>\n" +
                    "  </region>\n" +
                  	"  <region>\n" +
                  	"   <name>central</name>\n" +
                    "   <sales unit=\"millions\">12</sales>\n" +
                    "  </region>\n" +
                  	"  <region>\n" +
                  	"   <name>east</name>\n" +
                    "   <sales unit=\"millions\">31</sales>\n" +
                    "  </region>\n" +
                    " </year>\n" +
                    " <year>\n" +
                    "  <year>2000</year>\n" +
                  	"  <region>\n" +
                  	"   <name>west</name>\n" +
                    "   <sales unit=\"millions\">37</sales>\n" +
                    "  </region>\n" +
                  	"  <region>\n" +
                  	"   <name>central</name>\n" +
                    "   <sales unit=\"millions\">11</sales>\n" +
                    "  </region>\n" +
                  	"  <region>\n" +
                  	"   <name>east</name>\n" +
                    "   <sales unit=\"millions\">40</sales>\n" +
                    "  </region>\n" +
                    " </year>\n" +
                    "</salesdata>\n" +
                    "};\n" +
                    "declare variable $xslt {\n" +
                    "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
                    "<xsl:output method=\"html\" encoding=\"utf-8\"/>\n" +
                    "<xsl:template match=\"/\">\n" +
                    "  <html>\n" +
                    "    <table border=\"1\">\n" +
                    "      <tr>\n" +
                    "        <td colspan=\"2\">Total Sales</td>\n" +
                    "      </tr>\n" +
                    "      <xsl:for-each select=\"salesdata/year\">\n" +
                    "        <tr>\n" +
                    "          <td>\n" +
                    "            <xsl:value-of select=\"year\"/>\n" +
                    "          </td>\n" +
                    "          <td align=\"right\">\n" +
                    "            <xsl:value-of select=\"sum(region/sales)\"/>\n" +
                    "          </td>\n" +
                    "        </tr>\n" +
                    "      </xsl:for-each>\n" +
                    "      <tr>\n" +
                    "        <td>Grand Total</td>\n" +
                    "        <td align=\"right\">\n" +
                    "          <xsl:value-of select=\"sum(salesdata/year/region/sales)\"/>\n" +
                    "        </td>\n" +
                    "      </tr>\n" +
                    "    </table>\n" +
                    "  </html>\n" +
                    "</xsl:template>\n" +
                    "</xsl:stylesheet>\n" +
                    "};\n" +
                    "transform:transform($xml, $xslt, ())";

            ResourceSet result = service.query(query);

            //check there is one result
            assertEquals(1, result.getSize());

            String content = (String) result.getResource(0).getContent();
//            System.out.println(content);

            //check the content
            assertTrue(checkResult("total.ref", content));
        } catch (XMLDBException e) {
//            fail(e.getMessage());
            throw new RuntimeException(e);
        }
    }

	private boolean checkResult(String file, String result) throws Exception {
		int tokenCount = 0;
		
		String ref = loadFile(file);
		ref = ref.replaceAll("\\n", " ");
		ref = ref.replaceAll("<dgnorm_document>", "");
		ref = ref.replaceAll("</dgnorm_document>", "");

		String delim = " \t\n\r\f<>";
		StringTokenizer refTokenizer = new StringTokenizer(ref, delim);
		StringTokenizer resTokenizer = new StringTokenizer(result, delim);
		
		while (refTokenizer.hasMoreTokens()) {
			tokenCount++;
			String refToken = refTokenizer.nextToken();
			if (!resTokenizer.hasMoreTokens()) {
				throw new Exception("result should have: "+refToken+", but get EOF (at "+tokenCount+")");
			}
			String resToken = resTokenizer.nextToken();
			if (!refToken.equals(resToken)) {
				throw new Exception("result should have: "+refToken+", but get "+resToken+" (at "+tokenCount+")");
			}
		}
		if (resTokenizer.hasMoreTokens()) {
			String resToken = resTokenizer.nextToken();
			throw new Exception("result should have nothing, but get "+resToken+" (at "+tokenCount+")");
		}
		return true;
	}

	@Test
    public void testBench() throws Exception {
    	long start_time;
    	long end_time;

    	String query = null;
		String content = null;
		int passed = 0;
		
		boolean passing;
		Map<String, String> testInfo;
		
		String reqTest = null;//"avts";
		
		for (String testName : bench.keySet()) {
			if ((reqTest != null) && (!testName.equals(reqTest)))
				continue;
			
			passing = true;
			query = null;
			content = null;
			
			testInfo = bench.get(testName);

			if (testInfo.containsKey("storeBeforeTest")) {
//				System.out.print("skipping");
//				if (testInfo.containsKey("comment"))
//					System.out.print(" ("+testInfo.get("comment")+")");
//				System.out.println();
				continue;
			}
			
			
			String input = loadFile(testInfo.get("input"));
			String stylesheet = loadFile(testInfo.get("stylesheet"));

	        try {
	        	start_time = System.currentTimeMillis();
	            
	        	XPathQueryService service = (XPathQueryService) col.getService("XPathQueryService", "1.0");

	            query = "xquery version \"1.0\";\n" +
	                    "declare namespace transform=\"http://exist-db.org/xquery/transform\";\n" +
	                    "declare variable $xml {"+input+"};\n" +
	                    "declare variable $xslt {"+stylesheet+"};\n" +
	                    "transform:transform($xml, $xslt, ())\n";

	            ResourceSet result = service.query(query);

	        	end_time = System.currentTimeMillis();

	        	//check there is one result
//	            assertEquals(1, result.getSize());

	        	content = "";
	        	for (int i = 0; i < result.getSize(); i++)
	        		content = content + (String) result.getResource(i).getContent();

	            //check the content
	            assertTrue(checkResult(testInfo.get("reference"), content));
	        } catch (Exception e) {
//	        	System.out.println("************************************** query ******************************");
//	        	System.out.println(query);
//	        	System.out.println();
//	        	System.out.println("************************************* content ******************************");
//	        	System.out.println(content);
				passing = false;
	            throw new RuntimeException(e);
	        }
	        if (passing) {
	        	end_time = end_time - start_time;
//	        	System.out.println("pass ("+end_time+" ms)");
	        	passed++;
	        } else {
				System.err.println("faild");
            }
		}
		
//		System.out.println(" "+passed+" of "+bench.keySet().size());
    }
	
	//TODO: test <!-- reassembles an xml tree in reverse order -->

	
	private String loadFile(String string) throws IOException {
		String result = null;
		
		File file = new File("test/src/org/exist/xslt/test/bench/v1_0/"+string);
		if (!file.canRead()) {
			throw new IOException("can load information.");
		} else {
			// Open the file and then get a channel from the stream
			FileInputStream fis = new FileInputStream(file);
			try {
    			FileChannel fc = fis.getChannel();
    
    			// Get the file's size and then map it into memory
    			int sz = (int)fc.size();
    			MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, sz);
    
    			// Charset and decoder for ISO-8859-15
    			Charset charset = Charset.forName("ISO-8859-15");
    			CharsetDecoder decoder = charset.newDecoder();
    		    
    		    // Decode the file into a char buffer
    			CharBuffer cb = decoder.decode(bb);
    
    			result = cb.toString();
    			//TODO: rewrite to handle <?xml*?>
    			if (result.startsWith("<?xml version=\"1.0\"?>"))
    					result = result.substring("<?xml version=\"1.0\"?>".length());
    			if (result.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>"))
    				result = result.substring("<?xml version=\"1.0\" encoding=\"utf-8\"?>".length());
    
    			//XXX: rethink: prexslt query processing
//    			result = result.replaceAll("{", "{{");
//    			result = result.replaceAll("}", "}}");
    
    			// Close the channel and the stream
    			fc.close();
			} finally {
			    fis.close();
			}
		}
		return result;
	}

}
