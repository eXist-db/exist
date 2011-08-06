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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.security.SecurityManager;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
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

import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Test basic {@link org.exist.dom.NodeSet} operations to ensure that
 * the used algorithms are correct.
 *  
 * @author wolf
 *
 */
public class BasicNodeSetTest {

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

    private static String COLLECTION_CONFIG1 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
    	"		<fulltext default=\"none\">" +
        "           <create qname=\"LINE\"/>" +
        "           <create qname=\"SPEAKER\"/>" +
        "           <create qname=\"TITLE\"/>" +
        "		</fulltext>" +
        "	</index>" +
    	"</collection>";

    private static String directory = "samples/shakespeare";
    
    //private static File dir = new File(directory);
    private static File dir = null;
    static {
      String existHome = System.getProperty("exist.home");
      File existDir = existHome==null ? new File(".") : new File(existHome);
      dir = new File(existDir,directory);
    }

    private static BrokerPool pool = null;
    private static Collection root = null;
    private static DBBroker broker = null;
    private static Sequence seqSpeech = null;
    
    @Test
    public void childSelector() throws XPathException {
        NodeSelector selector = new ChildSelector(seqSpeech.toNodeSet(), -1);
        NameTest test = new NameTest(Type.ELEMENT, new QName("LINE", ""));
        NodeSet set = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT, seqSpeech.getDocumentSet(), test.getName(), selector);
        
        assertEquals(9492, set.getLength());
    }
    
    @Test
    public void descendantOrSelfSelector() throws XPathException {
        NodeSelector selector = new DescendantOrSelfSelector(seqSpeech.toNodeSet(), -1);
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEECH", ""));
        NodeSet set = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT, seqSpeech.getDocumentSet(), test.getName(), selector);
        
        assertEquals(2628, set.getLength());
    }
    
    @Test
    public void ancestorSelector() throws XPathException {
        NodeSelector selector = new AncestorSelector(seqSpeech.toNodeSet(), -1, false, true);
        NameTest test = new NameTest(Type.ELEMENT, new QName("ACT", ""));
        NodeSet set = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT, seqSpeech.getDocumentSet(),  test.getName(), selector);
        
        assertEquals(15, set.getLength());
    }
    
    @Test
    public void ancestorSelector_self() throws XPathException {
        NodeSet ns = seqSpeech.toNodeSet();
        NodeSelector selector = new AncestorSelector(ns, -1, true, true);
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEECH", ""));
        NodeSet set = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT, seqSpeech.getDocumentSet(), test.getName(), selector);
        
        assertEquals(2628, set.getLength());
    }

    @Test
    public void descendantSelector() throws XPathException, SAXException {
        Sequence seq = executeQuery(broker, "//SCENE", 72, null);
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
        NodeSelector selector = new DescendantSelector(seq.toNodeSet(), -1);
        NodeSet set = broker.getElementIndex().findElementsByTagName(ElementValue.ELEMENT, seq.getDocumentSet(), test.getName(), selector);
        
        assertEquals(2639, set.getLength());
    }
	
    @Test
    public void testAxes() throws XPathException, SAXException {
            
        Serializer serializer = broker.getSerializer();
        serializer.reset();
        DocumentSet docs = root.allDocs(broker, new DefaultDocumentSet(), true, false);

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
        result = speakers.selectAncestorDescendant(outerSet.toNodeSet(), NodeSet.DESCENDANT, false, -1, true);
        assertEquals(56, result.getLength());
        System.out.println("AbstractNodeSet.selectAncestorDescendant: PASS");

        System.out.println("Testing AbstractNodeSet.selectAncestorDescendant2 ...");
        result = ((AbstractNodeSet)outerSet).selectAncestorDescendant(outerSet.toNodeSet(), NodeSet.DESCENDANT,
            true, -1, true);
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
        
    }
	
    @Test
    public void testOptimizations() throws XPathException, SAXException {
            
        Serializer serializer = broker.getSerializer();
        serializer.reset();
        DocumentSet docs = root.allDocs(broker, new DefaultDocumentSet(), true, false);

        System.out.println("------------ Testing NativeElementIndex.findChildNodesByTagName ---------");
        // parent set: 1.1.1; child set: 1.1.1.1, 1.1.1.2, 1.1.1.3, 1.1.2.1, 1.2.1
        ExtNodeSet nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1.1']", 1, null);
        NodeSet children = 
            broker.getElementIndex().findDescendantsByTagName(ElementValue.ELEMENT, 
                            new QName("para", ""), Constants.CHILD_AXIS, docs, nestedSet, -1);
        assertEquals(3, children.getLength());

        // parent set: 1.1; child set: 1.1.1, 1.1.2
        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1']", 1, null);
        children = 
            broker.getElementIndex().findDescendantsByTagName(ElementValue.ELEMENT, 
                            new QName("section", ""), Constants.CHILD_AXIS, docs, nestedSet, -1);
        assertEquals(2, children.getLength());

        // parent set: 1, 1.1, 1.1.1, 1.1.2 ; child set: 1.1.1.1, 1.1.1.2, 1.1.1.3, 1.1.2.1, 1.2.1
        // problem: ancestor set contains nested nodes
        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = ('1.1', '1.1.1', '1.1.2')]", 3, null);
        children = 
            broker.getElementIndex().findDescendantsByTagName(ElementValue.ELEMENT, 
                            new QName("para", ""), Constants.CHILD_AXIS, docs, nestedSet, -1);
        assertEquals(4, children.getLength());

        // parent set: 1.1, 1.1.2, 1.2 ; child set: 1.1.1.1, 1.1.1.2, 1.1.1.3, 1.1.2.1, 1.2.1
        // problem: ancestor set contains nested nodes
        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = ('1.1', '1.1.2', '1.2')]", 3, null);
        children = 
            broker.getElementIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("para", ""), 
                            Constants.CHILD_AXIS, docs, nestedSet, -1);
        assertEquals(2, children.getLength());

        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1']", 1, null);
        children = 
            broker.getElementIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("para", ""), 
                            Constants.DESCENDANT_AXIS, docs, nestedSet, -1);
        assertEquals(4, children.getLength());

        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1']", 1, null);
        children = 
            broker.getElementIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("para", ""), 
                            Constants.DESCENDANT_AXIS, docs, nestedSet, -1);
        assertEquals(5, children.getLength());

        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1.2']", 1, null);
        children = 
            broker.getElementIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("section", ""), 
                            Constants.DESCENDANT_SELF_AXIS, docs, nestedSet, -1);
        assertEquals(1, children.getLength());

        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1.2']", 1, null);
        children = 
            broker.getElementIndex().findDescendantsByTagName(ElementValue.ATTRIBUTE, new QName("n", ""), 
                            Constants.ATTRIBUTE_AXIS, docs, nestedSet, -1);
        assertEquals(1, children.getLength());

        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1']", 1, null);
        children = 
            broker.getElementIndex().findDescendantsByTagName(ElementValue.ATTRIBUTE, new QName("n", ""), 
                            Constants.DESCENDANT_ATTRIBUTE_AXIS, docs, nestedSet, -1);
        assertEquals(7, children.getLength());

        System.out.println("------------ PASSED: NativeElementIndex.findChildNodesByTagName ---------");
    }
    
    @Test
    public void virtualNodeSet() throws XPathException, SAXException {

        Serializer serializer = broker.getSerializer();
        serializer.reset();

        executeQuery(broker, "//*/LINE", 9492, null);
        executeQuery(broker, "//*/LINE/*", 61, null);
        executeQuery(broker, "//*/LINE/text()", 9485, null);
        executeQuery(broker, "//SCENE/*/LINE", 9464, null);
        executeQuery(broker, "//SCENE/*[LINE &= 'spirit']", 21, null);
        executeQuery(broker, "//SCENE/*[LINE &= 'the']", 1005, null);
        executeQuery(broker, "//SCENE/*/LINE[. &= 'the']", 2167, null);
        executeQuery(broker, "//SPEECH[LINE &= 'spirit']/ancestor::*", 30, null);
        executeQuery(broker, "for $s in //SCENE/*[LINE &= 'the'] return node-name($s)", 1005, null);

        executeQuery(broker, "//SPEECH[LINE &= 'perturbed spirit']/preceding-sibling::*", 65, null);
        executeQuery(broker, "//SPEECH[LINE &= 'perturbed spirit']/following-sibling::*", 1, null);
    }
	
    private static Sequence executeQuery(DBBroker broker, String query, int expected, String expectedResult) throws XPathException, SAXException {
        XQuery xquery = broker.getXQueryService();
        Sequence seq = xquery.execute(query, null, AccessContext.TEST);
        assertEquals(expected, seq.getItemCount());
        
        if (expectedResult != null) {
            Item item = seq.itemAt(0);
            String value = serialize(broker, item);
            assertEquals(expectedResult, value);
        }
        return seq;
    }

    private static String serialize(DBBroker broker, Item item) throws SAXException, XPathException {
        Serializer serializer = broker.getSerializer();
	
        serializer.reset();
        String value;
        if(Type.subTypeOf(item.getType(), Type.NODE)) {
            value = serializer.serialize((NodeValue) item);
        } else {	
            value = item.getStringValue();
        }
        return value;
    }
	
    @BeforeClass
    public static void setUp() throws Exception {
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            pool = startDB();
            broker = pool.get(SecurityManager.SYSTEM_USER);
            transact = pool.getTransactionManager();
            transaction = transact.beginTransaction();

            root = broker.getOrCreateCollection(transaction, XmldbURI.create(DBBroker.ROOT_COLLECTION + "/test"));
            broker.saveCollection(transaction, root);

            CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, root, COLLECTION_CONFIG1);

            File files[] = dir.listFiles(new XMLFilenameFilter());

            File f;
            IndexInfo info;

            // store some documents.
            for (int i = 0; i < files.length; i++) {
                f = files[i];
                info = root.validateXMLResource(transaction, broker, XmldbURI.create(f.getName()), new InputSource(f.toURI().toASCIIString()));
                root.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
            }

            info = root.validateXMLResource(transaction, broker, XmldbURI.create("nested.xml"), NESTED_XML);
            root.store(transaction, broker, info, NESTED_XML, false);

            transact.commit(transaction);
            pool.release(broker);
            

            //for the tests
            broker = pool.get(SecurityManager.SYSTEM_USER);
            DocumentSet docs = root.allDocs(broker, new DefaultDocumentSet(), true, false);
            seqSpeech = executeQuery(broker, "//SPEECH", 2628, null);
            
        } catch(Exception e) {
            if (pool != null) {
                pool.release(broker);
                BrokerPool.stopAll(false);
                pool = null;
                root = null;
            }
            throw e;
        }
    }
	
    private static BrokerPool startDB() throws DatabaseConfigurationException, EXistException {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null) {
            home = System.getProperty("user.dir");
        }
        
        Configuration config = new Configuration(file, home);
        BrokerPool.configure(1, 5, config);
        return BrokerPool.getInstance();
    }

    @AfterClass
    public static void tearDown() {
        
        TransactionManager transact = null;
        Txn transaction = null;
        try {    
            transact = pool.getTransactionManager();
            transaction = transact.beginTransaction();
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(DBBroker.ROOT_COLLECTION + "/test"));
//          broker.removeCollection(transaction, root);
            
            transact.commit(transaction);
        } catch(Exception e) {
            if(transaction != null) {
                transact.abort(transaction);
            }
        } finally {
            if (pool != null) pool.release(broker);
        }
        BrokerPool.stopAll(false);
        pool = null;
        root = null;
    }
}
