/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.xqts;

import java.util.HashMap;

import junit.framework.Assert;

import org.exist.dom.ElementImpl;
import org.exist.dom.NodeProxy;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.DBBroker;
import org.exist.w3c.tests.TestCase;
import org.exist.xmldb.XQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.ErrorCodes.ErrorCode;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class QT3TS_case extends TestCase {

	protected static final String FOLDER = "test/external/QT3-test-suite/";
	protected static final String QT_NS = "http://www.w3.org/2010/09/qt-fots-catalog";
    protected static final XmldbURI QT3_URI = XmldbURI.DB.append("QT3");

    @Override
    public void loadTS() throws Exception {
    }

    private Sequence enviroment(String file) throws Exception {
        DBBroker broker = null;
        XQuery xquery = null;

        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            xquery = broker.getXQueryService();

            broker.getConfiguration().setProperty( XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL, true);
            
            String query = "xmldb:document('"+file+"')";

            return xquery.execute(query, null, AccessContext.TEST);
            
        } finally {
        	pool.release(broker);
        }
    }
    
    private HashMap<String, Sequence> enviroments(String file) {
    	HashMap<String, Sequence> enviroments = new HashMap<String, Sequence>();

		DBBroker broker = null;
	    try {
	        broker = pool.get(pool.getSecurityManager().getSystemSubject());
	        XQuery xquery = broker.getXQueryService();
	
	        broker.getConfiguration().setProperty( XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL, true);
	        
	        String query = "declare namespace qt='"+QT_NS+"';\n"+
	        "let $testCases := xmldb:document('/db/QT3/"+file+"')\n"+
	        "let $tc := $testCases//qt:environment\n"+
	        "return $tc";

	        Sequence result = xquery.execute(query, null, AccessContext.TEST);
	        
	        String col = XmldbURI.create("/db/QT3/"+file).removeLastSegment().toString();
	
	        for (NodeProxy node : result.toNodeSet()) {
	        	ElementImpl el = (ElementImpl) node.getNode();

                String name = el.getAttribute("name");
                if (name == null)
                	continue;
                
                NodeList sources = el.getElementsByTagNameNS(QT_NS, "source");
                for (int j = 0; j < sources.getLength(); j++) {
                	ElementImpl source = (ElementImpl) sources.item(j);
                	
                	String role = source.getAttribute("role");
                    Assert.assertEquals(".", role);

                	String url = source.getAttribute("file");
                    Assert.assertFalse("".equals(url));
                    Assert.assertFalse(enviroments.containsKey(name));
                    try {
                    	enviroments.put(name, enviroment(col+"/"+url));
					} catch (Exception e) {
		                Assert.fail(e.getMessage());
					}
                }
            }
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			pool.release(broker);
		}
	    return enviroments;
    }

    protected void testCase(String file, String tcName) {
    	
        DBBroker broker = null;
        Sequence result = null;
        XQuery xquery = null;

    	try {
            XQueryService service = (XQueryService) testCollection.getService(
                    "XQueryService", "1.0");

            String query = "declare namespace qt='"+QT_NS+"';\n"+
            "let $testCases := xmldb:document('/db/QT3/"+file+"')\n"+
            "let $tc := $testCases//qt:test-case[@name eq \""+tcName+"\"]\n"+
            "return $tc";

            ResourceSet results = service.query(query);
            
            Assert.assertFalse("", results.getSize() != 1);

            ElementImpl TC = (ElementImpl) ((XMLResource) results.getResource(0)).getContentAsDOM();

            Sequence contextSequence = null;
            
            NodeList expected = null;
            String extectedError = null;
            String nodeName = "";

            //compile & evaluate
            String caseScript = null;

            NodeList childNodes = TC.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node child = childNodes.item(i);
                switch (child.getNodeType()) {
                    case Node.ATTRIBUTE_NODE:
//                        String name = ((Attr)child).getName();
//                        if (name.equals("scenario"))
//                            scenario = ((Attr)child).getValue();
                        break;
                    case Node.ELEMENT_NODE:
                    	nodeName = ((ElementImpl) child).getLocalName();
                        if (nodeName.equals("test")) {
                            ElementImpl el = ((ElementImpl) child);
                            caseScript = el.getNodeValue();
                        
                        } else if (nodeName.equals("environment")) {
                            ElementImpl el = ((ElementImpl) child);
                            
                            String ref = el.getAttribute("ref");
                            if (ref == null || "empty".equals(ref))
                            	continue;
                            
                            Assert.assertNull(contextSequence);
							contextSequence = enviroments(file).get(ref);
                            
                        } else if (nodeName.equals("result")) {
                            ElementImpl el = ((ElementImpl) child);

                            //check for 'error' element
                            NodeList errors = el.getElementsByTagNameNS(QT_NS, "error");
                            for (int j = 0; j < errors.getLength(); j++) {
                            	ElementImpl error = (ElementImpl) errors.item(j);

                                //check error for 'code' attribute
                            	String code = error.getAttribute("code");
                            	if (code != null && !code.isEmpty()) {
                                	Assert.assertNull(extectedError);
                                	extectedError = code;
                                }
                            }

                            expected = el.getChildNodes();
                        }
                        break;
                    default :
                        ;
                }
            }

            try {
                broker = pool.get(pool.getSecurityManager().getSystemSubject());
                xquery = broker.getXQueryService();

                broker.getConfiguration().setProperty( XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL, true);
                
                result = xquery.execute(caseScript, contextSequence, AccessContext.TEST);

                for (int i = 0; i < expected.getLength(); i++) {
                	Node node = expected.item(i);
                	checkResults(node.getLocalName(), node.getChildNodes(), result);
                }
            } catch (XPathException e) {
            	ErrorCode errorCode = e.getErrorCode();
            	if (errorCode != null && errorCode.getErrorQName().getLocalName().equals(extectedError))
            		return;
            	
                Assert.fail("expected error code '"+extectedError+"' get '"+e.getMessage()+"'");
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            } finally {
            	pool.release(broker);
            }
        } catch (XMLDBException e) {
            Assert.fail(e.toString());
        } 
    }
    
    private void checkResults(String type, NodeList expected, Sequence result) throws Exception {
    		
		if ("all-of".equals(type)) {
	        for (int i = 0; i < expected.getLength(); i++) {
	        	final Node node = expected.item(i);
	        	checkResults(node.getLocalName(), node.getChildNodes(), result);
	        }
		} else if ("any-of".equals(type)) {
			StringBuilder sb = new StringBuilder();
			sb.append("at any-of all failed\n");
	        for (int i = 0; i < expected.getLength(); i++) {
	        	final Node node = expected.item(i);
	        	try {
	        		checkResults(node.getLocalName(), node.getChildNodes(), result);
	        		return;
	        	} catch (Throwable e) {
	        		sb.append(e.getMessage()).append("\n");
				}
	        }
			Assert.assertTrue(sb.toString(), false);
			
		} else if ("assert".equals(type)) {
			Assert.assertTrue("not implemented 'assert'", false);

		} else if ("assert-type".equals(type)) {
	        for (int i = 0; i < expected.getLength(); i++) {
	        	final Node node = expected.item(i);
	        	
	        	final String expect = node.getNodeValue();
	        	final int actual = result.itemAt(i).getType();
	        	
        		if (Type.subTypeOf(actual, Type.getType(expect)))
        			return;

	        	Assert.assertEquals("expected '"+expect+"' get '"+Type.getTypeName(actual),
        			Type.getType(expect), 
        			result.itemAt(i).getType()
    			); 
	        }

		} else if ("assert-eq".equals(type)) {
	        for (int i = 0; i < expected.getLength(); i++) {
	        	final Node node = expected.item(i);
	        	String expect = node.getNodeValue();
	        	if (expect.startsWith("\"") && expect.endsWith("\""))
	        		//? check is it xs:string ?
		        	Assert.assertEquals(
	        			expect.substring(1, expect.length()-1), 
	        			result.itemAt(i).getStringValue()
	    			); 
	        	else
		        	Assert.assertEquals(
	        			expect, 
	        			result.itemAt(i).getStringValue()
	    			); 
	        }

		} else if ("assert-deep-eq".equals(type)) {
			Assert.assertTrue("not implemented 'assert-deep-eq'", false);
		
		} else if ("assert-true".equals(type)) {
			Assert.assertTrue(result.effectiveBooleanValue());

		} else if ("assert-false".equals(type)) {
			Assert.assertFalse(result.effectiveBooleanValue());

		} else if ("assert-string-value".equals(type)) {
	        for (int i = 0; i < expected.getLength(); i++) {
	        	final Node node = expected.item(i);
	        	String expect = node.getNodeValue();

	        	Assert.assertEquals(
        			expect, 
        			result.itemAt(i).getStringValue()
    			); 
	        }

		} else if ("assert-serialization-error".equals(type)) {
			Assert.assertTrue("not implemented 'assert-serialization-error'", false);

		} else if ("serialization-matches".equals(type)) {
			Assert.assertTrue("not implemented 'serialization-matches'", false);

		} else if ("assert-permutation".equals(type)) {
			Assert.assertTrue("not implemented 'assert-permutation'", false);

		} else if ("assert-count".equals(type)) {
			Assert.assertTrue("not implemented 'assert-count'", false);

		} else if ("assert-empty".equals(type)) {
			Assert.assertTrue("not implemented 'assert-empty'", false);

		} else if ("assert-xml".equals(type)) {
			Assert.assertTrue("not implemented 'assert-xml'", false);

		} else if ("error".equals(type)) {
			Assert.assertTrue("not implemented 'error'", false);

		} else {
			Assert.assertTrue("unknown '"+type+"'", false);
		}
    }

    @Override
	protected String getCollection() {
		return QT3_URI.toString();
	}
}
