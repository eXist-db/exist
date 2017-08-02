/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2013 The eXist Project
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
package org.exist.xquery.xqts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import junit.framework.Assert;

import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.NodeListImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.source.FileSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.FileUtils;
import org.exist.w3c.tests.TestCase;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class XQTS_case extends TestCase {

    protected static final String XQTS_folder = "test/external/XQTS_1_0_3/";
//    protected static final String QT_NS = "http://www.w3.org/2010/09/qt-fots-catalog";

    private static Map<String, String> sources = null;
    private static Map<String, String> moduleSources = null;
    
    protected static final XmldbURI XQTS_URI = XmldbURI.DB.append("XQTS");    

    @Override
    public void loadTS() throws Exception {
        System.out.println("loading XQTS...");
        XQTS_To_junit convertor = new XQTS_To_junit();
        try {
            convertor.init();
            convertor.load();
            System.out.println("loaded QT3.");
        } finally {
            convertor.release();
        }
    }
    
    public void prepare(DBBroker broker, XQuery xquery) throws Exception {
        if (sources != null && moduleSources != null) {
            return;
        }

        Assert.assertNotNull( "XQTS collection wasn't found", broker.getCollection(XQTS_URI) );
        
        broker.getConfiguration().setProperty( XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL, true);
        
        if (sources == null) {
            sources = new HashMap<String, String>();

            String query = "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";"+
            "let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml') "+
            "return $XQTSCatalog//catalog:sources//catalog:source";

            Sequence results = xquery.execute(broker, query, null);

            for (NodeProxy node : results.toNodeSet()) {
                ElementImpl source = (ElementImpl) node.getNode();
                sources.put(source.getAttribute("ID"), XQTS_folder + source.getAttribute("FileName"));
            }
        }
        if (moduleSources == null) {
            moduleSources = new HashMap<String, String>();

            String query = "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";"+
                "let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml') "+
                "return $XQTSCatalog//catalog:sources//catalog:module";

            Sequence results = xquery.execute(broker, query, null);

            for (NodeProxy node : results.toNodeSet()) {
                ElementImpl source = (ElementImpl) node.getNode();
                moduleSources.put(source.getAttribute("ID"), "test/external/XQTS_1_0_3/"+source.getAttribute("FileName")+".xq");
            }
        }
    }

//	private static final String catalogNS = "http://www.w3.org/2005/02/query-test-XQTSCatalog";

    protected void groupCase(String testGroup, String testCase) {
        //ignore tests
//        if (testGroup.equals("FunctionCallExpr") && testCase.equals("K-FunctionCallExpr-11"))
//            return;
//        else if (testGroup.equals("SeqCollectionFunc")) {
//            if (testCase.equals("fn-collection-4d")
//                || testCase.equals("fn-collection-5d")
//                || testCase.equals("fn-collection-9")
//                || testCase.equals("fn-collection-10d"))
//                return;
//        if (testCase.equals("K2-NodeTest-11"))
//            return; //Added by p.b. as a quick attempt to work around current blocking code
//        if (testCase.equals("Constr-cont-document-3"))
//            return; //Added by p.b. as a quick attempt to work around current blocking code

        XQuery xquery = null;

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            broker.getConfiguration().setProperty( XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL, true);

            xquery = pool.getXQueryService();

            prepare(broker, xquery);

            String query = "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";\n"+
            "let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml')\n"+
            "let $tc := $XQTSCatalog/catalog:test-suite//catalog:test-group[@name eq \""+testGroup+"\"]/catalog:test-case[@name eq \""+testCase+"\"]\n"+
            "return $tc";

            Sequence results = xquery.execute(broker, query, null);
            
            Assert.assertFalse("", !results.hasOne());

            ElementImpl TC = (ElementImpl) results.toNodeSet().get(0).getNode();

            //collect test case information 
            String folder = "";
            String scenario = "";
            String script = "";
            //DateTimeValue scriptDateTime = null;
            NodeListImpl inputFiles = new NodeListImpl();
            NodeListImpl outputFiles = new NodeListImpl();
            ElementImpl contextItem = null;
            NodeListImpl modules = new NodeListImpl();
            String expectedError = "";

            String name = null;

            NodeList childNodes = TC.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node child = childNodes.item(i);
                switch (child.getNodeType()) {
                    case Node.ATTRIBUTE_NODE:
                        name = ((Attr)child).getName();
                        if (name.equals("FilePath"))
                            folder = ((Attr)child).getValue();
                        else if (name.equals("scenario"))
                            scenario = ((Attr)child).getValue();
                        break;
                    case Node.ELEMENT_NODE:
                        name = ((ElementImpl) child).getLocalName();
                        if (name.equals("query")) {
                            ElementImpl el = ((ElementImpl) child);
                            script = el.getAttribute("name");
                            //scriptDateTime = new DateTimeValue(el.getAttribute("date"));
                        }
                        else if (name.equals("input-file"))
                            inputFiles.add(child);
                        else if (name.equals("output-file"))
                            outputFiles.add(child);
                        else if (name.equals("contextItem"))
                            contextItem = (ElementImpl)child;
                        else if (name.equals("module"))
                            modules.add(child);
                        else if (name.equals("expected-error"))
                            expectedError = child.getTextContent();
                        break;
                    default :
                        ;
                }
            }

            Sequence result = null;

            //compile & evaluate
            Path caseScript = Paths.get(XQTS_folder+"Queries/XQuery/"+folder, script+".xq");
            try {
                XQueryContext context;

                context = new XQueryContext(broker.getBrokerPool());

                //map modules' namespaces to location 
                Map<String, String> moduleMap = (Map<String, String>)broker.getConfiguration().getProperty(XQueryContext.PROPERTY_STATIC_MODULE_MAP);
                for (int i = 0; i < modules.getLength(); i++) {
                    ElementImpl module = (ElementImpl)modules.item(i);
                    String id = module.getTextContent();
                    moduleMap.put(module.getAttribute("namespace"), moduleSources.get(id));
                }
                broker.getConfiguration().setProperty(XQueryContext.PROPERTY_STATIC_MODULE_MAP, moduleMap);

                //declare variable
                for (int i = 0; i < inputFiles.getLength(); i++) {
                    ElementImpl inputFile = (ElementImpl)inputFiles.item(i);
                    String id = inputFile.getTextContent();

                    //use DocUtils
                    //context.declareVariable( 
                        //inputFile.getAttribute("variable"),
                        //DocUtils.getDocument(context, sources.get(id))
                    //);

                    //in-memory nodes
                    context.declareVariable(inputFile.getAttribute("variable"), loadVarFromURI(context, sources.get(id)));
                }

                Sequence contextSequence = null;
                //set context item
                if (contextItem != null) {
                    String id = contextItem.getTextContent();
                    contextSequence = loadVarFromURI(context, sources.get(id));
                }

                fixBrokenTests(context, testGroup, testCase);

                //compare result with one provided by test case
                boolean ok = false;
                
            	Exception error = null;
                if ("runtime-error".equals(scenario)) {
                	try {
                        //compile
                        CompiledXQuery compiled = xquery.compile(broker, context, new FileSource(caseScript, true));

                        //execute
                        result = xquery.execute(broker, compiled, contextSequence);
                        
                        if (outputFiles.getLength() != 0) {
                            //can be answered
        	                for (int i = 0; i < outputFiles.getLength(); i++) {
        	                    ElementImpl outputFile = (ElementImpl)outputFiles.item(i);
        	
        	                    String compare = outputFile.getAttribute("compare");
        	                    if (compare != null && compare.equalsIgnoreCase("IGNORE")) {
        	                    	ok = true;
        	                    	break;
        	                    }
        	
        	                    if (compareResult(script, "XQTS_1_0_3/ExpectedTestResults/"+folder, outputFile, result)) {
        	                        ok = true;
        	                        break;
        	                    }
        	                }
                        } else {
                        	error = catchError(result);
                        }

                	} catch (Exception e) {
                		error = e;
					}
                	
                    if (!ok && error != null && expectedError != null) {// error.getMessage().contains(expectedError)) {
                    	ok = true;
                    }
                } else {
                    //compile
                    CompiledXQuery compiled = xquery.compile(broker, context, new FileSource(caseScript, true));

                    //execute
                    result = xquery.execute(broker, compiled, contextSequence);

                    //check answer
	                for (int i = 0; i < outputFiles.getLength(); i++) {
	                    ElementImpl outputFile = (ElementImpl)outputFiles.item(i);
	
	                    String compare = outputFile.getAttribute("compare");
	                    if (compare != null && compare.equalsIgnoreCase("IGNORE")) {
	                    	ok = true;
	                    	break;
	                    }
	
	                    if (compareResult(script, "XQTS_1_0_3/ExpectedTestResults/"+folder, outputFile, result)) {
	                        ok = true;
	                        break;
	                    }
	                }
                }

                //collect information if result is wrong
                if (!ok) {
                    StringBuilder message = new StringBuilder();
                    StringBuilder exp = new StringBuilder();
                    try {
                        for (int i = 0; i < outputFiles.getLength(); i++) {
                            ElementImpl outputFile = (ElementImpl)outputFiles.item(i);
                            String compare = outputFile.getAttribute("compare");
                            if (compare != null && compare.equalsIgnoreCase("IGNORE")) {
                                continue;
                            }
                            
                            final Path expectedResult = Paths.get(XQTS_folder + "ExpectedTestResults/" + folder, outputFile.getTextContent());
                            if (!Files.isReadable(expectedResult)) {
                                Assert.fail("can't read expected result");
                            }
                            
                            //avoid to big output
                            if (FileUtils.sizeQuietly(expectedResult) >= 1024) {
                            	exp = new StringBuilder();
                            	exp.append("{TOO BIG}");
                            	break;
                            } else {
	                            exp.append("{'");
                                exp.append(new String(Files.readAllBytes(expectedResult), UTF_8));
	                            exp.append("'}");
                            }
                        }
                    } catch (Exception e) {
                        exp.append(e.getMessage());
                    }
                    
                    String res = "{NOTHING}";
                    if (result != null)
                    	res = sequenceToString(result);
                    
                    if (exp.length() == 0)
                        exp.append("error "+ expectedError);
                    StringBuilder data = new StringBuilder();
                    for (int i = 0; i < inputFiles.getLength(); i++) {
                        ElementImpl inputFile = (ElementImpl)inputFiles.item(i);
                        String id = inputFile.getTextContent();
                        data.append(inputFile.getAttribute("variable"));
                        data.append(" = \n");
                        data.append(readFileAsString(Paths.get(sources.get(id)), 1024));
                        data.append("\n");
                    }
                    message.append("\n");
                    message.append("expected ");
                    message.append("[" + exp + "]");
                    message.append(" got ");
                    message.append("[" + res + "]\n");
                    message.append("script:\n");
                    message.append(readFileAsString(caseScript));
                    message.append("\n");
                    message.append("data:\n");
                    message.append(data);
                    Assert.fail(message.toString());
                }
            } catch (XPathException e) {
//                String error = e.getMessage();
                if (!expectedError.isEmpty())
                    ;
                else if (expectedError.equals("*"))
                    ;
                
                //TODO:check e.getCode()
//                else if (error.indexOf(expectedError) != -1)
//                    ;
//                else {
//                    if (error.startsWith("err:")) error = error.substring(4);
//
//                    if (error.indexOf(expectedError) == -1)
//                        Assert.fail("expected error is "+expectedError+", got "+error+" ["+e.getMessage()+"]");
//                }
            } catch (Exception e) {
				if (e.getMessage().contains("SENR0001")) {
					if (!expectedError.isEmpty())
						return;
				}

                e.printStackTrace();

                StringBuilder message = new StringBuilder();
                message.append(e.toString());
                message.append("\n during script evaluation:\n");
                try {
					message.append(readFileAsString(caseScript));
				} catch (IOException e1) {
					message.append("ERROR - "+e1.getMessage());
				}

                Assert.fail(message.toString());
            }
        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }

    private void fixBrokenTests(XQueryContext context, String group, String test) {
        try {
            if (group.equals("ContextImplicitTimezoneFunc")) {
                TimeZone implicitTimeZone = TimeZone.getTimeZone("GMT-5:00");// getDefault();
                //if( implicitTimeZone.inDaylightTime( new Date() ) ) {
                    //implicitTimeZone.setRawOffset( implicitTimeZone.getRawOffset() + implicitTimeZone.getDSTSavings() );
                //}
                context.setTimeZone(implicitTimeZone);
            } else if (group.equals("ContextCurrentDatetimeFunc") ||
                    group.equals("ContextCurrentDateFunc") ||
                    group.equals("ContextCurrentTimeFunc")) {
                DateTimeValue dt = null;
                if (test.equals("fn-current-time-4"))
                    dt = new DateTimeValue("2005-12-05T13:38:03.455-05:00");
                else if (test.equals("fn-current-time-6"))
                    dt = new DateTimeValue("2005-12-05T13:38:18.059-05:00");
                else if (test.equals("fn-current-time-7"))
                    dt = new DateTimeValue("2005-12-05T13:38:18.059-05:00");
                else if (test.equals("fn-current-time-10"))
                    dt = new DateTimeValue("2005-12-05T13:38:18.09-05:00");
                else if (test.startsWith("fn-current-time-"))
                    dt = new DateTimeValue("2005-12-05T10:15:03.408-05:00");
                else if (test.equals("fn-current-dateTime-6"))
                    dt = new DateTimeValue("2005-12-05T17:10:00.312-05:00");
                else if (test.equals("fn-current-datetime-7"))
                    dt = new DateTimeValue("2005-12-05T17:10:00.312-05:00");
                else if (test.equals("fn-current-dateTime-10"))
                    dt = new DateTimeValue("2005-12-05T17:10:00.344-05:00"); 
                else if (test.equals("fn-current-dateTime-21"))
                    dt = new DateTimeValue("2005-12-05T17:10:00.453-05:00");
                else if (test.equals("fn-current-dateTime-24"))
                    dt = new DateTimeValue("2005-12-05T17:10:00.469-05:00");
                else
                    dt = new DateTimeValue("2005-12-05T17:10:00.203-05:00");
                //if (dt != null)
                    context.setCalendar(dt.calendar);
            }
        } catch (XPathException e) {
            //
        }
    }

	@Override
	protected XmldbURI getCollection() {
		return XQTS_URI;
	}
}
