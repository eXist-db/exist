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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Optional;
import java.util.StringTokenizer;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.TransformerHandler;

import junit.framework.Assert;

import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.NodeImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.w3c.tests.TestCase;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XSLTS_case extends TestCase {

	private final static String XSLT_COLLECTION = "XSLTS";

	private final static String XSLTS_folder = XSLT_COLLECTION+"_1_1_0";
	
	@Override
	public void loadTS() throws Exception {
		BrokerPool.getInstance().getConfiguration().setProperty(
				TransformerFactoryAllocator.PROPERTY_TRANSFORMER_CLASS, 
				"org.exist.xslt.TransformerFactoryImpl");
		
	}

	protected void testCase(String inputURL, String xslURL, String outputURL, String expectedError) throws Exception {
//		String input = loadFile(XSLTS_folder+"TestInputs/"+inputURL, false);
//		String stylesheet = loadFile(XSLTS_folder+"TestInputs/"+xslURL, true);

//        String query = "xquery version \"1.0\";\n" +
//                "declare namespace transform=\"http://exist-db.org/xquery/transform\";\n" +
//                "declare variable $xml external;\n" +
//                "declare variable $xslt external;\n" +
//                "transform:transform($xml, $xslt, ())\n";
        

		try(final DBBroker broker = db.get(Optional.of(db.getSecurityManager().getSystemSubject()))) {
			XQueryContext context;
//			XQuery xquery;
//
//				xquery = broker.getXQueryService();
//
//				broker.getConfiguration().setProperty( XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL, true);
//				
//				context = xquery.newContext(AccessContext.TEST);
				context = new XSLContext(db);
//		        
//			} catch (Exception e) {
//				Assert.fail(e.getMessage());
//				return;
//			}
//
//			//declare variable
//	        if (inputURL !=  null && inputURL != "")
//	        	context.declareVariable("xml", loadVarFromURI(context, testLocation+XSLTS_folder+"/TestInputs/"+inputURL));
//	        else
//	        	context.declareVariable("xml", loadVarFromString(context, "<empty/>"));
//	        	
//			context.declareVariable("xslt", loadVarFromURI(context, testLocation+XSLTS_folder+"/TestInputs/"+xslURL));
//			
//			//compile
//			CompiledXQuery compiled = xquery.compile(context, query);
//			
//			//execute
//			Sequence result = xquery.execute(compiled, null);
			
			TransformerFactoryImpl factory = new TransformerFactoryImpl();
			factory.setBrokerPool(db);
			
			Templates templates = factory.newTemplates(
					new SourceImpl(
							loadVarFromURI(context, testLocation+XSLTS_folder+"/TestInputs/"+xslURL).getOwnerDocument()
						)
					);
			TransformerHandler handler = factory.newTransformerHandler(templates);

//	        TransformErrorListener errorListener = new TransformErrorListener();
//	        handler.getTransformer().setErrorListener(errorListener);
			
	        NodeImpl input;
			if (inputURL !=  null && inputURL != "")
	        	input = loadVarFromURI(context, testLocation+XSLTS_folder+"/TestInputs/"+inputURL);
	        else
	        	input = loadVarFromString(context, "<empty/>");

        	Transformer transformer = handler.getTransformer();
    		Sequence result = ((org.exist.xslt.Transformer)transformer).transform(input);
			
			//compare result with one provided by test case
			boolean ok = false;
			if (outputURL == null || outputURL.equals("")) {
				Assert.fail("expected error: " + expectedError);
			} else {
				//workaround
		        Document doc = new DocumentImpl(context, false);
		        Element outputFile = doc.createElement("outputFile");
		        outputFile.setAttribute("compare", "Fragment");
		        outputFile.setTextContent(outputURL);
	
				if (compareResult("", XSLTS_folder+"/ExpectedTestResults/", outputFile, result)) {
					ok = true;
				}
			}
			
			if (!ok)
				Assert.fail("expected \n" +
						"["+readFileAsString(new File(testLocation+XSLTS_folder+"/ExpectedTestResults/", outputURL))+"]\n" +
						", get \n["+sequenceToString(result)+"]");
		} catch (XPathException e) {
			String error = e.getMessage();

			if (!expectedError.isEmpty())
				;
			else {
				e.printStackTrace();
				Assert.fail("expected error is "+expectedError+", get "+error+" ["+e.getMessage()+"]");
			}
		}

//        StringBuilder content = new StringBuilder();
//    	for (int i = 0; i < result.getSize(); i++)
//    		content.append((String) result.getResource(i).getContent());
//
//        assertTrue(checkResult(outputURL, content.toString()));
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
//				System.out.println("expected:");
//				System.out.println(ref);
//				System.out.println("get:");
//				System.out.println(result);
				throw new Exception("result should have: "+refToken+", but get EOF (at "+tokenCount+")");
			}
			String resToken = resTokenizer.nextToken();
			if (!refToken.equals(resToken)) {
//				System.out.println(ref);
//				System.out.println(result);
				throw new Exception("result should have: "+refToken+", but get "+resToken+" (at "+tokenCount+")");
			}
		}
		if (resTokenizer.hasMoreTokens()) {
			String resToken = resTokenizer.nextToken();
//			System.out.println(ref);
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
			} finally {
			    fis.close();
			}
		}
		return result;
	}

	@Override
	protected XmldbURI getCollection() {
		return XmldbURI.create("/db/XSLTS");
	}
}
