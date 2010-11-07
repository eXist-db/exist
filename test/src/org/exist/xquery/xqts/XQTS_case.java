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
 *  $Id$
 */
package org.exist.xquery.xqts;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import junit.framework.Assert;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeListImpl;
import org.exist.security.xacml.AccessContext;
import org.exist.source.FileSource;
import org.exist.storage.DBBroker;
import org.exist.w3c.tests.TestCase;
import org.exist.xmldb.XQueryService;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class XQTS_case extends TestCase {

    protected static final String XQTS_folder = "test/external/XQTS_1_0_3/";

    private static Map<String, String> sources = null;
    private static Map<String, String> moduleSources = null;

    @Override
    public void loadTS() throws Exception {
        testCollection = DatabaseManager.getCollection("xmldb:exist:///db/XQTS", "admin", "");
        if (testCollection == null) {
            File buildFile = new File("webapp/xqts/build.xml");
            Project p = new Project();
            p.setUserProperty("ant.file", buildFile.getAbsolutePath());
            p.setUserProperty("config.basedir", "../../"+XQTS_folder);
            DefaultLogger consoleLogger = new DefaultLogger();
            consoleLogger.setErrorPrintStream(System.err);
            consoleLogger.setOutputPrintStream(System.out);
            consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
            p.addBuildListener(consoleLogger);

            try {
                p.fireBuildStarted();
                p.init();
                ProjectHelper helper = ProjectHelper.getProjectHelper();
                p.addReference("ant.projectHelper", helper);
                helper.parse(p, buildFile);
                p.executeTarget("store");
                p.fireBuildFinished(null);
                Thread.sleep(60 * 1000);
            } catch (BuildException e) {
                p.fireBuildFinished(e);
            } catch (InterruptedException e) {
                //
            }
        }

        testCollection = DatabaseManager.getCollection("xmldb:exist:///db/XQTS", "admin", "");
        if (testCollection == null) throw new Exception("XQTS collection wasn't found");

        if (sources == null) {
            sources = new HashMap<String, String>();

            XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");

            String query = "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";"+
            "let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml') "+
            "return $XQTSCatalog//catalog:sources//catalog:source";

            ResourceSet results = service.query(query);

            for (int i = 0; i < results.getSize(); i++) {
                ElementImpl source = (ElementImpl) ((XMLResource) results.getResource(i)).getContentAsDOM();
                sources.put(source.getAttribute("ID"), XQTS_folder + source.getAttribute("FileName"));
            }
        }
        if (moduleSources == null) {
            moduleSources = new HashMap<String, String>();

            XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");

            String query = "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";"+
                "let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml') "+
                "return $XQTSCatalog//catalog:sources//catalog:module";

            ResourceSet results = service.query(query);
            for (int i = 0; i < results.getSize(); i++) {
                ElementImpl source = (ElementImpl) ((XMLResource) results.getResource(i)).getContentAsDOM();
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
//        } else if (testGroup.equals("SeqExprCastSI") && testCase.equals("casthcds9")) {
//            return;
//        } else if (testGroup.equals("NotationEQSI")) {
//            if (testCase.equals("Comp-notation-5")
//                || testCase.equals("Comp-notation-8")
//                || testCase.equals("Comp-notation-10")
//                || testCase.equals("Comp-notation-11")
//                || testCase.equals("Comp-notation-12")
//                || testCase.equals("Comp-notation-13")
//                || testCase.equals("Comp-notation-14")
//                || testCase.equals("Comp-notation-19")
//                || testCase.equals("Comp-notation-20")
//                || testCase.equals("Comp-notation-21"))
//                return;
//        } else if (testGroup.equals("SchemaImportProlog")) {
//            if (testCase.equals("schema-import-1")
//                || testCase.equals("schema-import-2")
//                || testCase.equals("schema-import-5")
//                || testCase.equals("schema-import-9")
//                || testCase.equals("schema-import-13")
//                || testCase.equals("schema-import-17")
//                || testCase.equals("schema-import-25"))
//                return;
//        } else if (testGroup.equals("STFLWORExpr")) {
//            /*UNDERSTAND: why it must throw FORG0006?
//                The test description: 
//                    Test 'where' clause with heterogenous sequences. First item is a node
//                The XQuery 1.0: An XML Query Language (W3C Recommendation 23 January 2007)
//                2.4.3 Effective Boolean Value
//                    If its operand is a sequence whose first item is a node, fn:boolean returns true. 
//            */
//            if (testCase.equals("ST-WhereExpr001"))
//                return;
//        }
//        if (testCase.equals("K2-NodeTest-11"))
//            return; //Added by p.b. as a quick attempt to work around current blocking code
//        if (testCase.equals("Constr-cont-document-3"))
//            return; //Added by p.b. as a quick attempt to work around current blocking code

        try {
            XQueryService service = (XQueryService) testCollection.getService(
                    "XQueryService", "1.0");

            String query = "declare namespace catalog=\"http://www.w3.org/2005/02/query-test-XQTSCatalog\";\n"+
            "let $XQTSCatalog := xmldb:document('/db/XQTS/XQTSCatalog.xml')\n"+
            "let $tc := $XQTSCatalog/catalog:test-suite//catalog:test-group[@name eq \""+testGroup+"\"]/catalog:test-case[@name eq \""+testCase+"\"]\n"+
            "return $tc";

            ResourceSet results = service.query(query);
            
            Assert.assertFalse("", results.getSize() != 1);

            ElementImpl TC = (ElementImpl) ((XMLResource) results.getResource(0)).getContentAsDOM();

            //collect test case information 
            String folder = "";
            String scenario = "";
            String script = "";
            DateTimeValue scriptDateTime = null;
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
                            expectedError = ((ElementImpl) child).getNodeValue();
                        break;
                    default :
                        ;
                }
            }

            //compile & evaluate
            File caseScript = new File(XQTS_folder+"Queries/XQuery/"+folder, script+".xq");
            try {
                XQueryContext context;
                XQuery xquery;

                DBBroker broker = null;
                try {
                    broker = pool.get(pool.getSecurityManager().getSystemSubject());
                    xquery = broker.getXQueryService();

                    broker.getConfiguration().setProperty( XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL, true);

                    context = xquery.newContext(AccessContext.TEST);

                    //map modules' namespaces to location 
                    Map<String, String> moduleMap = (Map<String, String>)broker.getConfiguration().getProperty(XQueryContext.PROPERTY_STATIC_MODULE_MAP);
                    for (int i = 0; i < modules.getLength(); i++) {
                        ElementImpl module = (ElementImpl)modules.item(i);
                        String id = module.getNodeValue();
                        moduleMap.put(module.getAttribute("namespace"), moduleSources.get(id));
                    }
                    broker.getConfiguration().setProperty(XQueryContext.PROPERTY_STATIC_MODULE_MAP, moduleMap);

                } catch (Exception e) {
                    Assert.fail(e.getMessage());
                    return;
                } finally {
                    pool.release(broker);
                }

                //declare variable
                for (int i = 0; i < inputFiles.getLength(); i++) {
                    ElementImpl inputFile = (ElementImpl)inputFiles.item(i);
                    String id = inputFile.getNodeValue();

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
                    String id = contextItem.getNodeValue();
                    contextSequence = loadVarFromURI(context, sources.get(id));
                }

                fixBrokenTests(context, testGroup, testCase);

                //compile
                CompiledXQuery compiled = xquery.compile(context, new FileSource(caseScript, "UTF8", true));

                //execute
                Sequence result = xquery.execute(compiled, contextSequence);

                //compare result with one provided by test case
                boolean ok = false;
                for (int i = 0; i < outputFiles.getLength(); i++) {
                    ElementImpl outputFile = (ElementImpl)outputFiles.item(i);

                    if (compareResult(script, "XQTS_1_0_3/ExpectedTestResults/"+folder, outputFile, result)) {
                        ok = true;
                        break;
                    }
                }

                //collect information if result is wrong
                if (!ok) {
                    StringBuilder message = new StringBuilder();
                    String exp = "";
                    try {
                        for (int i = 0; i < outputFiles.getLength(); i++) {
                            exp += "{'";
                            ElementImpl outputFile = (ElementImpl)outputFiles.item(i);
                            File expectedResult = new File(XQTS_folder+"ExpectedTestResults/"+folder, outputFile.getNodeValue());
                            if (!expectedResult.canRead())
                                Assert.fail("can't read expected result");
                            Reader reader = new BufferedReader(new FileReader(expectedResult));
                            char ch;
                            while (reader.ready()) {
                                ch = (char)reader.read();
                                if (ch == '\r')
                                    ch = (char)reader.read();
                                exp += String.valueOf(ch); 
                            }
                            exp += "'}";
                        }
                    } catch (Exception e) {
                        exp += e.getMessage();
                    }
                    String res = sequenceToString(result);
                    if (exp.isEmpty())
                        exp += "error "+ expectedError;
                    StringBuilder data = new StringBuilder();
                    for (int i = 0; i < inputFiles.getLength(); i++) {
                        ElementImpl inputFile = (ElementImpl)inputFiles.item(i);
                        String id = inputFile.getNodeValue();
                        data.append(inputFile.getAttribute("variable"));
                        data.append(" = \n");
                        data.append(readFileAsString(new File(sources.get(id))));
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
                String error = e.getMessage();
                if (!expectedError.isEmpty())
                    ;
                else if (expectedError.equals("*"))
                    ;
                else if (expectedError.equals("FOER0000"))
                    ;
                else if (expectedError.equals("FOCH0002"))
                    ;
                else if (expectedError.equals("FOCA0002"))
                    ;
                else if (expectedError.equals("FORX0002"))
                    ;
                else if (expectedError.equals("XPST0017")) {
                    if (error.indexOf(expectedError) != -1)
                        ;
                    else if (error.indexOf("FODC0002") != -1)
                        ;
                    else
                        Assert.fail("expected error is "+expectedError+", got "+error+" ["+e.getMessage()+"]");
                }
                else if (expectedError.equals("FORG0006")) {
                    if (error.indexOf(expectedError) != -1)
                        ;
                    else if (error.indexOf("XPTY0004") != -1)
                        ;
                    else
                        Assert.fail("expected error is "+expectedError+", got "+error+" ["+e.getMessage()+"]");
                }
                else if (expectedError.equals("XPDY0002")) {
                    if (error.indexOf(expectedError) != -1)
                        ;
                    else if (error.indexOf("XPTY0002") != -1)
                        ;
                    else
                        Assert.fail("expected error is "+expectedError+", got "+error+" ["+e.getMessage()+"]");
                }
                else if (expectedError.equals("XPTY0020")) {
                    if (error.indexOf(expectedError) != -1)
                        ;
                    else if (error.indexOf("cannot convert ") != -1)
                        ;
                    else
                        Assert.fail("expected error is "+expectedError+", got "+error+" ["+e.getMessage()+"]");
                }
                else if (expectedError.equals("XPTY0004")) {
                    if (error.indexOf(expectedError) != -1)
                        ;
                    else if (error.indexOf("FOTY0011") != -1)
                        ;
                    else if (error.indexOf("Cannot cast ") != -1)
                        ;
                    else if (error.indexOf(" is not a sub-type of ") != -1)
                        ;
                    else if (error.indexOf("Type error: ") != -1)
                        ;
                    else
                        Assert.fail("expected error is "+expectedError+", got "+error+" ["+e.getMessage()+"]");
                }
                else if (expectedError.equals("XPST0003")) {
                    if (error.indexOf(expectedError) != -1)
                        ;
                    else if (error.startsWith("expecting "))
                        ;
                    else if (error.startsWith("unexpected char: "))
                        ;
                    else
                        Assert.fail("expected error is "+expectedError+", got "+error+" ["+e.getMessage()+"]");
                } else {
                    if (error.startsWith("err:")) error = error.substring(4);

                    if (error.indexOf(expectedError) == -1)
                        Assert.fail("expected error is "+expectedError+", got "+error+" ["+e.getMessage()+"]");
                }
            } catch (Exception e) {
                //e.printStackTrace();
                Assert.fail(e.toString());
            }
        } catch (XMLDBException e) {
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
}
