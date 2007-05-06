/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-06 The eXist Project
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.dom;

import java.io.File;

import junit.framework.TestCase;

import org.apache.log4j.BasicConfigurator;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.security.SecurityManager;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.XMLFilenameFilter;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.AncestorSelector;
import org.exist.xquery.ChildSelector;
import org.exist.xquery.Constants;
import org.exist.xquery.DescendantOrSelfSelector;
import org.exist.xquery.DescendantSelector;
import org.exist.xquery.NameTest;
import org.exist.xquery.NodeSelector;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Test basic {@link org.exist.dom.NodeSet} operations to ensure that
 * the used algorithms are correct.
 *  
 * @author wolf
 *
 */
public class BasicNodeSetTest extends TestCase {

	private final static String NESTED_XML =
        "<section n='1'>" +
		    "<section n='1.1'>" +
		        "<section n='1.1.1'>" +
		            "<para n='1.1.1.1'/>" +
		            "<para n='1.1.1.2'/>" +
		            "<para n='1.1.1.3'/>" +
	            "</section>" +
                "<section n='1.1.2'>" +
                    "<para n='1.1.2.1'/>" +
                "</section>" +
            "</section>" +
            "<section n='1.2'>" +
                "<para n='1.2.1'/>" +
            "</section>" +
        "</section>";
	
	private static String directory = "samples/shakespeare";
    
//    private static File dir = new File(directory);
    private static File dir = null;
    static {
      String existHome = System.getProperty("exist.home");
      File existDir = existHome==null ? new File(".") : new File(existHome);
      dir = new File(existDir,directory);
    }
    
	public static void main(String[] args) {
		BasicConfigurator.configure();
		junit.textui.TestRunner.run(BasicNodeSetTest.class);
	}

	private BrokerPool pool = null;
	private Collection root = null;
	
	public void testSelectors() {
		DBBroker broker = null;
        try {
        	assertNotNull(pool);
            broker = pool.get(SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            
            DocumentSet docs = root.allDocs(broker, new DocumentSet(), true, false);
            Sequence seq = executeQuery(broker, "//SPEECH", 2628, null);
            
            System.out.println("Testing ChildSelector ...");
            NameTest test = new NameTest(Type.ELEMENT, new QName("LINE", ""));
            NodeSelector selector = new ChildSelector(seq.toNodeSet(), -1);
            NodeSet set = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT, seq.getDocumentSet(), 
            		test.getName(), selector);
            assertEquals(9492, set.getLength());
            System.out.println("ChildSelector: PASS");
            
            System.out.println("Testing DescendantOrSelfSelector ...");
            selector = new DescendantOrSelfSelector(seq.toNodeSet(), -1);
            test = new NameTest(Type.ELEMENT, new QName("SPEECH", ""));
            set = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT, seq.getDocumentSet(), 
            		test.getName(), selector);
            assertEquals(2628, set.getLength());
            System.out.println("DescendantOrSelfSelector: PASS");
            
            System.out.println("Testing AncestorSelector ...");
            test = new NameTest(Type.ELEMENT, new QName("ACT", ""));
            selector = new AncestorSelector(seq.toNodeSet(), -1, false);
            set = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT, seq.getDocumentSet(), 
            		test.getName(), selector);
            assertEquals(15, set.getLength());
            System.out.println("AncestorSelector: PASS");
            
            System.out.println("Testing AncestorSelector: self");
            test = new NameTest(Type.ELEMENT, new QName("SPEECH", ""));
            NodeSet ns = seq.toNodeSet();
            System.out.println("ns = " + ns.getLength());
            selector = new AncestorSelector(ns, -1, true);
            set = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT, seq.getDocumentSet(), 
            		test.getName(), selector);
            assertEquals(2628, set.getLength());
            System.out.println("AncestorSelector: PASS");
            
            System.out.println("Testing DescendantSelector ...");
            seq = executeQuery(broker, "//SCENE", 72, null);
            test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
            selector = new DescendantSelector(seq.toNodeSet(), -1);
            set = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT, seq.getDocumentSet(), 
            		test.getName(), selector);
            assertEquals(2639, set.getLength());
            System.out.println("DescendantSelector: PASS");
        } catch (Exception e) {
            e.printStackTrace();
	        fail(e.getMessage());
        } finally {
        	if (pool != null) pool.release(broker);
        }
	}
	
	public void testAxes() {
		DBBroker broker = null;
        try {
        	assertNotNull(pool);
            broker = pool.get(SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            DocumentSet docs = root.allDocs(broker, new DocumentSet(), true, false);
            
            Sequence smallSet = executeQuery(broker, "//SPEECH[LINE &= 'perturbed spirit']", 1, null);
            Sequence largeSet = executeQuery(broker, "//SPEECH[LINE &= 'love']", 160, null);
            Sequence outerSet = executeQuery(broker, "//SCENE[TITLE &= 'closet']", 1, null);
            
            NameTest test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
            NodeSet speakers = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT,
                    docs, test.getName(), null);
            
            System.out.println("Testing NodeSetHelper.selectParentChild ...");
            NodeSet result = NodeSetHelper.selectParentChild(speakers, smallSet.toNodeSet(), NodeSet.DESCENDANT, -1);
            assertEquals(1, result.getLength());
            String value = serialize(broker, result.itemAt(0));
            System.out.println("NodeSetHelper.selectParentChild: " + value);
            assertEquals(value, "<SPEAKER>HAMLET</SPEAKER>");
            
            result = NodeSetHelper.selectParentChild(speakers, largeSet.toNodeSet(), NodeSet.DESCENDANT, -1);
            assertEquals(160, result.getLength());
            System.out.println("NodeSetHelper.selectParentChild: PASS");
            
            System.out.println("Testing AbstractNodeSet.selectAncestorDescendant ...");
            result = speakers.selectAncestorDescendant(outerSet.toNodeSet(), NodeSet.DESCENDANT, false, -1);
            assertEquals(56, result.getLength());
            System.out.println("AbstractNodeSet.selectAncestorDescendant: PASS");
            
            System.out.println("Testing AbstractNodeSet.selectAncestorDescendant2 ...");
            result = ((AbstractNodeSet)outerSet).selectAncestorDescendant(outerSet.toNodeSet(), NodeSet.DESCENDANT, true, -1);
            assertEquals(1, result.getLength());
            System.out.println("AbstractNodeSet.selectAncestorDescendant2: PASS");
            
            System.out.println("Testing AbstractNodeSet.getParents ...");
            result = ((AbstractNodeSet)largeSet).getParents(-1);
            assertEquals(49, result.getLength());
            System.out.println("AbstractNodeSet.getParents: PASS");
            
            test = new NameTest(Type.ELEMENT, new QName("SCENE", ""));
            NodeSet scenes = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT,
                    docs, test.getName(), null);
            scenes.getLength();
            System.out.println("Testing AbstractNodeSet.selectAncestors ...");
            result = ((AbstractNodeSet)scenes).selectAncestors(largeSet.toNodeSet(), false, -1);
            assertEquals(47, result.getLength());
            System.out.println("AbstractNodeSet.selectAncestors: PASS");
            
            NodeProxy proxy = (NodeProxy) smallSet.itemAt(0);
            System.out.println("Testing NodeProxy.getParents ...");
            result = proxy.getParents(-1);
            assertEquals(1, result.getLength());
            System.out.println("NodeProxy.getParents: PASS");
            
            result = speakers.selectParentChild(proxy, NodeSet.DESCENDANT, -1);
            assertEquals(1, result.getLength());
            
            largeSet = executeQuery(broker, "//SPEECH[LINE &= 'love']/SPEAKER", 160, null);
            test = new NameTest(Type.ELEMENT, new QName("LINE", ""));
            NodeSet lines = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT,
                    docs, test.getName(), null);
            System.out.println("LINE: " + lines.getLength());
            System.out.println("SPEAKER: " + largeSet.getItemCount());
            result = ((AbstractNodeSet) lines).selectFollowingSiblings(largeSet.toNodeSet(), -1);
            assertEquals(1451, result.getLength());
            
            largeSet = executeQuery(broker, "//SPEECH[LINE &= 'love']/LINE[1]", 160, null);
            result = ((AbstractNodeSet) speakers).selectPrecedingSiblings(largeSet.toNodeSet(), -1);
            assertEquals(160, result.getLength());
            
            System.out.println("Testing ExtArrayNodeSet.selectParentChild ...");
            Sequence nestedSet = executeQuery(broker, "//section[@n = ('1.1', '1.1.1')]", 2, null);
            test = new NameTest(Type.ELEMENT, new QName("para", ""));
            NodeSet children = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT,
                    docs, test.getName(), null);
            result = children.selectParentChild(nestedSet.toNodeSet(), NodeSet.DESCENDANT);
            assertEquals(3, result.getLength());
            
            nestedSet = executeQuery(broker, "//section[@n = ('1.1', '1.1.2', '1.2')]", 3, null);
            result = children.selectParentChild(nestedSet.toNodeSet(), NodeSet.DESCENDANT);
            assertEquals(2, result.getLength());
            
            nestedSet = executeQuery(broker, "//section[@n = ('1.1', '1.1.1', '1.2')]", 3, null);
            result = children.selectParentChild(nestedSet.toNodeSet(), NodeSet.DESCENDANT);
            assertEquals(4, result.getLength());
            
            nestedSet = executeQuery(broker, "//para[@n = ('1.1.2.1')]", 1, null);
            test = new NameTest(Type.ELEMENT, new QName("section", ""));
            NodeSet sections = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT,
                    docs, test.getName(), null);
            result = ((NodeSet) nestedSet).selectParentChild(sections.toNodeSet(), NodeSet.DESCENDANT);
            assertEquals(1, result.getLength());
        } catch (Exception e) {
        	e.printStackTrace();
	        fail(e.getMessage());
        } finally {
        	if (pool != null) pool.release(broker);
        }
	}
	
    public void testOptimizations() {
        DBBroker broker = null;
        try {
            assertNotNull(pool);
            broker = pool.get(SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            DocumentSet docs = root.allDocs(broker, new DocumentSet(), true, false);

            System.out.println("------------ Testing NativeElementIndex.findChildNodesByTagName ---------");
            // parent set: 1.1.1; child set: 1.1.1.1, 1.1.1.2, 1.1.1.3, 1.1.2.1, 1.2.1
            ExtArrayNodeSet nestedSet = (ExtArrayNodeSet) executeQuery(broker, "//section[@n = '1.1.1']", 1, null);
            NodeSet children = 
            	broker.getElementIndex().findDescendantsByTagName(ElementValue.ELEMENT, 
            			new QName("para", ""), Constants.CHILD_AXIS, docs, nestedSet, -1);
            assertEquals(3, children.getLength());
            
            // parent set: 1.1; child set: 1.1.1, 1.1.2
            nestedSet = (ExtArrayNodeSet) executeQuery(broker, "//section[@n = '1.1']", 1, null);
            children = 
            	broker.getElementIndex().findDescendantsByTagName(ElementValue.ELEMENT, 
            			new QName("section", ""), Constants.CHILD_AXIS, docs, nestedSet, -1);
            assertEquals(2, children.getLength());
            
            // parent set: 1, 1.1, 1.1.1, 1.1.2 ; child set: 1.1.1.1, 1.1.1.2, 1.1.1.3, 1.1.2.1, 1.2.1
            // problem: ancestor set contains nested nodes
            nestedSet = (ExtArrayNodeSet) executeQuery(broker, "//section[@n = ('1.1', '1.1.1', '1.1.2')]", 3, null);
            children = 
            	broker.getElementIndex().findDescendantsByTagName(ElementValue.ELEMENT, 
        			new QName("para", ""), Constants.CHILD_AXIS, docs, nestedSet, -1);
            assertEquals(4, children.getLength());
            
            // parent set: 1.1, 1.1.2, 1.2 ; child set: 1.1.1.1, 1.1.1.2, 1.1.1.3, 1.1.2.1, 1.2.1
            // problem: ancestor set contains nested nodes
            nestedSet = (ExtArrayNodeSet) executeQuery(broker, "//section[@n = ('1.1', '1.1.2', '1.2')]", 3, null);
            children = 
                broker.getElementIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("para", ""), 
                		Constants.CHILD_AXIS, docs, nestedSet, -1);
            assertEquals(2, children.getLength());
            
            nestedSet = (ExtArrayNodeSet) executeQuery(broker, "//section[@n = '1.1']", 1, null);
            children = 
                broker.getElementIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("para", ""), 
                		Constants.DESCENDANT_AXIS, docs, nestedSet, -1);
            assertEquals(4, children.getLength());
            
            nestedSet = (ExtArrayNodeSet) executeQuery(broker, "//section[@n = '1']", 1, null);
            children = 
                broker.getElementIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("para", ""), 
                		Constants.DESCENDANT_AXIS, docs, nestedSet, -1);
            assertEquals(5, children.getLength());
            
            nestedSet = (ExtArrayNodeSet) executeQuery(broker, "//section[@n = '1.1.2']", 1, null);
            children = 
                broker.getElementIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("section", ""), 
                		Constants.DESCENDANT_SELF_AXIS, docs, nestedSet, -1);
            assertEquals(1, children.getLength());
            
            nestedSet = (ExtArrayNodeSet) executeQuery(broker, "//section[@n = '1.1.2']", 1, null);
            children = 
                broker.getElementIndex().findDescendantsByTagName(ElementValue.ATTRIBUTE, new QName("n", ""), 
                		Constants.ATTRIBUTE_AXIS, docs, nestedSet, -1);
            assertEquals(1, children.getLength());
            
            nestedSet = (ExtArrayNodeSet) executeQuery(broker, "//section[@n = '1.1']", 1, null);
            children = 
                broker.getElementIndex().findDescendantsByTagName(ElementValue.ATTRIBUTE, new QName("n", ""), 
                		Constants.DESCENDANT_ATTRIBUTE_AXIS, docs, nestedSet, -1);
            assertEquals(7, children.getLength());
            
            System.out.println("------------ PASSED: NativeElementIndex.findChildNodesByTagName ---------");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) pool.release(broker);
        }
    }
    
	public void testVirtualNodeSet() {
		DBBroker broker = null;
        try {
        	assertNotNull(pool);
            broker = pool.get(SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            
            executeQuery(broker, "//*/LINE", 9492, null);
            executeQuery(broker, "//*/LINE/*", 61, null);
            executeQuery(broker, "//*/LINE/text()", 9485, null);
            executeQuery(broker, "//SCENE/*/LINE", 9464, null);
            executeQuery(broker, "//SCENE/*[LINE &= 'spirit']", 21, null);
            executeQuery(broker, "//SCENE/*[LINE &= 'the']", 1005, null);
            executeQuery(broker, "//SCENE/*/LINE[. &= 'the']", 2167, null);
            executeQuery(broker, "//SPEECH[* &= 'the']", 1008, null);
            executeQuery(broker, "//*[. &= 'me']", 584, null);
            executeQuery(broker, "//SPEECH[LINE &= 'spirit']/ancestor::*", 30, null);
            executeQuery(broker, "for $s in //SCENE/*[LINE &= 'the'] return node-name($s)", 1005, null);
            
            executeQuery(broker, "//SPEECH[LINE &= 'perturbed spirit']/preceding-sibling::*", 65, null);
            executeQuery(broker, "//SPEECH[LINE &= 'perturbed spirit']/following-sibling::*", 1, null);
        } catch (Exception e) {
        	e.printStackTrace();
	        fail(e.getMessage());
        } finally {
        	if (pool != null) pool.release(broker);
        }
	}
	
	private Sequence executeQuery(DBBroker broker, String query, int expected,
			String expectedResult) throws XPathException, SAXException {
		XQuery xquery = broker.getXQueryService();
		assertNotNull(xquery);
		Sequence seq = xquery.execute(query, null, AccessContext.TEST);
		assertNotNull(seq);
		assertEquals(expected, seq.getItemCount());
		System.out.println("Found: " + seq.getItemCount() + " for query:\n" + query);
		if (expectedResult != null) {
	        Item item = seq.itemAt(0);
	        String value = serialize(broker, item);
	        assertEquals(expectedResult, value);
		}
		return seq;
	}

	private String serialize(DBBroker broker, Item item) throws SAXException, XPathException {
		Serializer serializer = broker.getSerializer();
		assertNotNull(serializer);
        serializer.reset();
		String value;
		if (Type.subTypeOf(item.getType(), Type.NODE))
			value = serializer.serialize((NodeValue) item);
		else
			value = item.getStringValue();
		return value;
	}
	
	protected void setUp() throws Exception {        
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
        	pool = startDB();
        	assertNotNull(pool);
            broker = pool.get(SecurityManager.SYSTEM_USER);
            assertNotNull(broker);            
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);            
            System.out.println("BasicNodeSetTest#setUp ...");
            
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(DBBroker.ROOT_COLLECTION + "/test"));
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            
            
            File files[] = dir.listFiles(new XMLFilenameFilter());
            assertNotNull(files);
            
            File f;
            IndexInfo info;
            // store some documents.
            for (int i = 0; i < files.length; i++) {
                f = files[i];
                try {
                    info = root.validateXMLResource(transaction, broker, XmldbURI.create(f.getName()), new InputSource(f.toURI().toASCIIString()));
                    assertNotNull(info);
                    root.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
                } catch (SAXException e) {
                    System.err.println("Error found while parsing document: " + f.getName() + ": " + e.getMessage());
                }
            }
            
            info = root.validateXMLResource(transaction, broker, XmldbURI.create("nested.xml"), NESTED_XML);
            //TODO : unlock the collection here ?
            assertNotNull(info);
            root.store(transaction, broker, info, NESTED_XML, false);
            
            transact.commit(transaction);
            System.out.println("BasicNodeSetTest#setUp finished.");
        } catch (Exception e) {
        	transact.abort(transaction);
            e.printStackTrace();
	        fail(e.getMessage()); 	        
        } finally {
        	if (pool != null) pool.release(broker);
        }
	}
	
	protected BrokerPool startDB() {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null)
            home = System.getProperty("user.dir");
        try {
            Configuration config = new Configuration(file, home);
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {            
            fail(e.getMessage());
        }
        return null;
    }

    protected void tearDown() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            assertNotNull(broker);            
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);            
            System.out.println("BasicNodeSetTest#tearDown >>>");
            
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(DBBroker.ROOT_COLLECTION + "/test"));
            assertNotNull(root);
//            broker.removeCollection(transaction, root);
            
            transact.commit(transaction);
        } catch (Exception e) {
            transact.abort(transaction);
            e.printStackTrace();
        } finally {
            if (pool != null) pool.release(broker);
        }
        BrokerPool.stopAll(false);
        pool = null;
        root = null;
    }
}
