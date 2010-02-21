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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.StringTokenizer;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.junit.After;
import org.junit.Before;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XPathQueryService;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XSLTS_case {

	private final static String URI = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
	private final static String XSLT_COLLECTION = "xslt_tests";

	private Collection col = null;

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
				System.out.println("collection created.");
			}
			
			BrokerPool.getInstance().getConfiguration().setProperty(
					TransformerFactoryAllocator.PROPERTY_TRANSFORMER_CLASS, 
					"org.exist.xslt.TransformerFactoryImpl");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@After
	public void shutdown() throws Exception {
		DatabaseInstanceManager dim =
		    (DatabaseInstanceManager) col.getService(
		        "DatabaseInstanceManager", "1.0");
		dim.shutdown();
	}
	

	protected void testCase(String inputURL, String xslURL, String outputURL) throws Exception {
		String input = loadFile("test/external/XSLTS_1_1_0/TestInputs/"+inputURL, false);
		String stylesheet = loadFile("test/external/XSLTS_1_1_0/TestInputs/"+xslURL, true);

		XPathQueryService service = (XPathQueryService) col.getService("XPathQueryService", "1.0");

        String query = "xquery version \"1.0\";\n" +
                "declare namespace transform=\"http://exist-db.org/xquery/transform\";\n" +
                "declare variable $xml {"+input+"};\n" +
                "declare variable $xslt {"+stylesheet+"};\n" +
                "transform:transform($xml, $xslt, ())\n";

        ResourceSet result = service.query(query);

        StringBuilder content = new StringBuilder();
    	for (int i = 0; i < result.getSize(); i++)
    		content.append((String) result.getResource(i).getContent());

        assertTrue(checkResult(outputURL, content.toString()));
	}

	private boolean checkResult(String file, String result) throws Exception {
		int tokenCount = 0;
		
		String ref = loadFile("test/external/XSLTS_1_1_0/ExpectedTestResults/"+file, false);
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
				System.out.println(ref);
				System.out.println(result);
				throw new Exception("result should have: "+refToken+", but get EOF (at "+tokenCount+")");
			}
			String resToken = resTokenizer.nextToken();
			if (!refToken.equals(resToken)) {
				System.out.println(ref);
				System.out.println(result);
				throw new Exception("result should have: "+refToken+", but get "+resToken+" (at "+tokenCount+")");
			}
		}
		if (resTokenizer.hasMoreTokens()) {
			String resToken = resTokenizer.nextToken();
			System.out.println(ref);
			throw new Exception("result should have nothing, but get "+resToken+" (at "+tokenCount+")");
		}
		return true;
	}

	private String loadFile(String fileURL, boolean incapsulate) throws IOException {
		String result = null;
		
		File file = new File(fileURL);
		if (!file.canRead()) {
			throw new IOException("can load information.");
		} else {
			// Open the file and then get a channel from the stream
			FileInputStream fis = new FileInputStream(file);
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
			if (result.startsWith("<?xml ")) {
				int endAt = result.indexOf("?>");
				result = result.substring(endAt+2);
			}

			//XXX: rethink: prexslt query processing
			if (incapsulate) {
				result = result.replaceAll("\\{", "\\{\\{");
				result = result.replaceAll("\\}", "\\}\\}");
			}

			// Close the channel and the stream
			fc.close();
		}
		return result;
	}
}
