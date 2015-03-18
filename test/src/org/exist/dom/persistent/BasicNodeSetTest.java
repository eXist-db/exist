/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist-db Project
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
package org.exist.dom.persistent;

import org.exist.collections.triggers.TriggerException;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.TransactionException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
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
import java.io.IOException;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Test basic {@link org.exist.dom.persistent.NodeSet} operations to ensure that
 * the used algorithms are correct.
 *  
 * @author wolf
 * @author Adam Retter <adam@exist-db.org>
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
    

    private static BrokerPool pool = null;
    private static Collection root = null;
    private static DBBroker broker = null;
    private static Sequence seqSpeech = null;
    private static DocumentSet docs = null;
    
    @Test
    public void childSelector() throws XPathException {
        NodeSelector selector = new ChildSelector(seqSpeech.toNodeSet(), -1);
        NameTest test = new NameTest(Type.ELEMENT, new QName("LINE", ""));
        NodeSet set = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, seqSpeech.getDocumentSet(), test.getName(), selector);
        
        assertEquals(9492, set.getLength());
    }
    
    @Test
    public void descendantOrSelfSelector() throws XPathException {
        NodeSelector selector = new DescendantOrSelfSelector(seqSpeech.toNodeSet(), -1);
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEECH", ""));
        NodeSet set = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, seqSpeech.getDocumentSet(), test.getName(), selector);
        
        assertEquals(2628, set.getLength());
    }
    
    @Test
    public void ancestorSelector() throws XPathException {
        NodeSelector selector = new AncestorSelector(seqSpeech.toNodeSet(), -1, false, true);
        NameTest test = new NameTest(Type.ELEMENT, new QName("ACT", ""));
        NodeSet set = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, seqSpeech.getDocumentSet(),  test.getName(), selector);
        
        assertEquals(15, set.getLength());
    }
    
    @Test
    public void ancestorSelector_self() throws XPathException {
        NodeSet ns = seqSpeech.toNodeSet();
        NodeSelector selector = new AncestorSelector(ns, -1, true, true);
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEECH", ""));
        NodeSet set = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, seqSpeech.getDocumentSet(), test.getName(), selector);
        
        assertEquals(2628, set.getLength());
    }

    @Test
    public void descendantSelector() throws XPathException, SAXException, PermissionDeniedException {
        Sequence seq = executeQuery(broker, "//SCENE", 72, null);
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
        NodeSelector selector = new DescendantSelector(seq.toNodeSet(), -1);
        NodeSet set = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, seq.getDocumentSet(), test.getName(), selector);
        
        assertEquals(2639, set.getLength());
    }
	
    @Test
    public void selectParentChild() throws XPathException, SAXException, PermissionDeniedException {
        
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
        NodeSet speakers = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
        Sequence smallSet = executeQuery(broker, "//SPEECH/LINE[fn:contains(., 'perturbed spirit')]/ancestor::SPEECH", 1, null);
        
        NodeSet result = NodeSetHelper.selectParentChild(speakers, smallSet.toNodeSet(), NodeSet.DESCENDANT, -1);
        assertEquals(1, result.getLength());
        String value = serialize(broker, result.itemAt(0));
        assertEquals(value, "<SPEAKER>HAMLET</SPEAKER>");
    }
    
    @Test
    public void selectParentChild_2() throws XPathException, SAXException, PermissionDeniedException {
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
        NodeSet speakers = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
        Sequence largeSet = executeQuery(broker, "//SPEECH/LINE[fn:contains(., 'love')]/ancestor::SPEECH", 187, null);
        
        NodeSet result = NodeSetHelper.selectParentChild(speakers, largeSet.toNodeSet(), NodeSet.DESCENDANT, -1);
        assertEquals(187, result.getLength());
    }
    
    @Test
    public void selectAncestorDescendant() throws XPathException, SAXException, PermissionDeniedException{
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
        NodeSet speakers = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
        Sequence outerSet = executeQuery(broker, "//SCENE/TITLE[fn:contains(., 'closet')]/ancestor::SCENE", 1, null);
        
        NodeSet result = speakers.selectAncestorDescendant(outerSet.toNodeSet(), NodeSet.DESCENDANT, false, -1, true);
        assertEquals(56, result.getLength());
    }
    
    @Test
    public void selectAncestorDescendant_2() throws XPathException, SAXException, PermissionDeniedException{
        Sequence outerSet = executeQuery(broker, "//SCENE/TITLE[fn:contains(., 'closet')]/ancestor::SCENE", 1, null);
        
        NodeSet result = ((AbstractNodeSet)outerSet).selectAncestorDescendant(outerSet.toNodeSet(), NodeSet.DESCENDANT, true, -1, true);
        assertEquals(1, result.getLength());
    }
    
    
    @Test
    public void getParents() throws XPathException, SAXException, PermissionDeniedException{
        Sequence largeSet = executeQuery(broker, "//SPEECH/LINE[fn:contains(., 'love')]/ancestor::SPEECH", 187, null);
        
        NodeSet result = ((AbstractNodeSet)largeSet).getParents(-1);
        assertEquals(51, result.getLength());
    }
    
    @Test
    public void selectAncestors() throws XPathException, SAXException, PermissionDeniedException {
        NameTest test = new NameTest(Type.ELEMENT, new QName("SCENE", ""));
        NodeSet scenes = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
        Sequence largeSet = executeQuery(broker, "//SPEECH/LINE[fn:contains(., 'love')]/ancestor::SPEECH", 187, null);
        
        NodeSet result = ((AbstractNodeSet)scenes).selectAncestors(largeSet.toNodeSet(), false, -1);
        assertEquals(49, result.getLength());
    }
    
    @Test
    public void nodeProxy_getParents() throws XPathException, SAXException, PermissionDeniedException {
        Sequence smallSet = executeQuery(broker, "//SPEECH/LINE[fn:contains(., 'perturbed spirit')]/ancestor::SPEECH", 1, null);
        
        NodeProxy proxy = (NodeProxy) smallSet.itemAt(0);
        
        NodeSet result = proxy.getParents(-1);
        assertEquals(1, result.getLength());
        
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
        NodeSet speakers = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
        
            result = speakers.selectParentChild(proxy, NodeSet.DESCENDANT, -1);
            assertEquals(1, result.getLength());
    }
    
    @Test
    public void selectFollowingSiblings() throws XPathException, SAXException, PermissionDeniedException {
        Sequence largeSet = executeQuery(broker, "//SPEECH/LINE[fn:contains(., 'love')]/ancestor::SPEECH/SPEAKER", 187, null);
        NameTest test = new NameTest(Type.ELEMENT, new QName("LINE", ""));
        NodeSet lines = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
        
        NodeSet result = ((AbstractNodeSet) lines).selectFollowingSiblings(largeSet.toNodeSet(), -1);
        assertEquals(1689, result.getLength());
    }
    
    @Test
    public void selectPrecedingSiblings() throws XPathException, SAXException, PermissionDeniedException {
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
        NodeSet speakers = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
        Sequence largeSet = executeQuery(broker, "//SPEECH/LINE[fn:contains(., 'love')]/ancestor::SPEECH/LINE[1]", 187, null);
        
        NodeSet result = ((AbstractNodeSet) speakers).selectPrecedingSiblings(largeSet.toNodeSet(), -1);
        assertEquals(187, result.getLength());
    }
    
    @Test
    public void extArrayNodeSet_selectParentChild_1() throws XPathException, SAXException, PermissionDeniedException {
        Sequence nestedSet = executeQuery(broker, "//section[@n = ('1.1', '1.1.1')]", 2, null);
        NameTest test = new NameTest(Type.ELEMENT, new QName("para", ""));
        NodeSet children = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
        
        NodeSet result = children.selectParentChild(nestedSet.toNodeSet(), NodeSet.DESCENDANT);
        assertEquals(3, result.getLength());
    }
    
    @Test
    public void extArrayNodeSet_selectParentChild_2() throws XPathException, SAXException, PermissionDeniedException {
        Sequence nestedSet = executeQuery(broker, "//section[@n = ('1.1', '1.1.2', '1.2')]", 3, null);
        NameTest test = new NameTest(Type.ELEMENT, new QName("para", ""));
        NodeSet children = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
        
        NodeSet result = children.selectParentChild(nestedSet.toNodeSet(), NodeSet.DESCENDANT);
        assertEquals(2, result.getLength());
    }
    
    @Test
    public void extArrayNodeSet_selectParentChild_3() throws XPathException, SAXException, PermissionDeniedException {
        Sequence nestedSet = executeQuery(broker, "//section[@n = ('1.1', '1.1.1', '1.2')]", 3, null);
        NameTest test = new NameTest(Type.ELEMENT, new QName("para", ""));
        NodeSet children = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
        
        NodeSet result = children.selectParentChild(nestedSet.toNodeSet(), NodeSet.DESCENDANT);
        assertEquals(4, result.getLength());
    }
    
    @Test
    public void extArrayNodeSet_selectParentChild_4() throws XPathException, SAXException, PermissionDeniedException {
        Sequence nestedSet = executeQuery(broker, "//para[@n = ('1.1.2.1')]", 1, null);
        NameTest test = new NameTest(Type.ELEMENT, new QName("section", ""));
        NodeSet sections = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
        
        NodeSet result = ((NodeSet) nestedSet).selectParentChild(sections.toNodeSet(), NodeSet.DESCENDANT);
        assertEquals(1, result.getLength());
    }
	
    @Test
    public void testOptimizations() throws XPathException, SAXException, PermissionDeniedException {
            
        Serializer serializer = broker.getSerializer();
        serializer.reset();
        DocumentSet docs = root.allDocs(broker, new DefaultDocumentSet(), true);

        //Testing NativeElementIndex.findChildNodesByTagName
        // parent set: 1.1.1; child set: 1.1.1.1, 1.1.1.2, 1.1.1.3, 1.1.2.1, 1.2.1
        ExtNodeSet nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1.1']", 1, null);
        NodeSet children = 
            broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ELEMENT, 
                            new QName("para", ""), Constants.CHILD_AXIS, docs, nestedSet, -1);
        assertEquals(3, children.getLength());

        // parent set: 1.1; child set: 1.1.1, 1.1.2
        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1']", 1, null);
        children = 
            broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ELEMENT, 
                            new QName("section", ""), Constants.CHILD_AXIS, docs, nestedSet, -1);
        assertEquals(2, children.getLength());

        // parent set: 1, 1.1, 1.1.1, 1.1.2 ; child set: 1.1.1.1, 1.1.1.2, 1.1.1.3, 1.1.2.1, 1.2.1
        // problem: ancestor set contains nested nodes
        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = ('1.1', '1.1.1', '1.1.2')]", 3, null);
        children = 
            broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ELEMENT, 
                            new QName("para", ""), Constants.CHILD_AXIS, docs, nestedSet, -1);
        assertEquals(4, children.getLength());

        // parent set: 1.1, 1.1.2, 1.2 ; child set: 1.1.1.1, 1.1.1.2, 1.1.1.3, 1.1.2.1, 1.2.1
        // problem: ancestor set contains nested nodes
        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = ('1.1', '1.1.2', '1.2')]", 3, null);
        children = 
            broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("para", ""), 
                            Constants.CHILD_AXIS, docs, nestedSet, -1);
        assertEquals(2, children.getLength());

        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1']", 1, null);
        children = 
            broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("para", ""), 
                            Constants.DESCENDANT_AXIS, docs, nestedSet, -1);
        assertEquals(4, children.getLength());

        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1']", 1, null);
        children = 
            broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("para", ""), 
                            Constants.DESCENDANT_AXIS, docs, nestedSet, -1);
        assertEquals(5, children.getLength());

        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1.2']", 1, null);
        children = 
            broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("section", ""), 
                            Constants.DESCENDANT_SELF_AXIS, docs, nestedSet, -1);
        assertEquals(1, children.getLength());

        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1.2']", 1, null);
        children = 
            broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ATTRIBUTE, new QName("n", ""), 
                            Constants.ATTRIBUTE_AXIS, docs, nestedSet, -1);
        assertEquals(1, children.getLength());

        nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1']", 1, null);
        children = 
            broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ATTRIBUTE, new QName("n", ""), 
                            Constants.DESCENDANT_ATTRIBUTE_AXIS, docs, nestedSet, -1);
        assertEquals(7, children.getLength());
    }
    
    @Test
    public void virtualNodeSet_1() throws XPathException, SAXException, PermissionDeniedException {
        executeQuery(broker, "//*/LINE", 9492, null);
    }
    
    @Test
    public void virtualNodeSet_2() throws XPathException, SAXException, PermissionDeniedException {
        executeQuery(broker, "//*/LINE/*", 61, null);
    }
    
    @Test
    public void virtualNodeSet_3() throws XPathException, SAXException, PermissionDeniedException {
        executeQuery(broker, "//*/LINE/text()", 9485, null);
    }
    
    @Test
    public void virtualNodeSet_4() throws XPathException, SAXException, PermissionDeniedException {
        executeQuery(broker, "//SCENE/*/LINE", 9464, null);
    }

    @Test
    public void virtualNodeSet_5() throws XPathException, SAXException, PermissionDeniedException {
        executeQuery(broker, "//SCENE/*[fn:contains(LINE, 'spirit')]", 30, null);
    }
    
    @Test
    public void virtualNodeSet_6() throws XPathException, SAXException, PermissionDeniedException {
        executeQuery(broker, "//SCENE/*[fn:contains(LINE, 'the')]", 1313, null);
    }
    
    @Test
    public void virtualNodeSet_7() throws XPathException, SAXException, PermissionDeniedException {
        executeQuery(broker, "//SCENE/*/LINE[fn:contains(., 'the')]", 3198, null);
    }
    
    @Test
    public void virtualNodeSet_8() throws XPathException, SAXException, PermissionDeniedException {
        executeQuery(broker, "//SCENE[fn:contains(., 'spirit')]/ancestor::*", 16, null);
    }
    
    @Test
    public void virtualNodeSet_9() throws XPathException, SAXException, PermissionDeniedException {
        executeQuery(broker, "for $s in //SCENE/*[fn:contains(LINE, 'the')] return fn:node-name($s)", 1313, null);
    }
    
    @Test
    public void virtualNodeSet_10() throws XPathException, SAXException, PermissionDeniedException {
        executeQuery(broker, "//SPEECH[fn:contains(LINE, 'perturbed spirit')]/preceding-sibling::*", 65, null);
    }
    
    @Test
    public void virtualNodeSet_11() throws XPathException, SAXException, PermissionDeniedException {
        executeQuery(broker, "//SPEECH[fn:contains(LINE, 'perturbed spirit')]/following-sibling::*", 1, null);
    }
    
    private static Sequence executeQuery(DBBroker broker, String query, int expected, String expectedResult) throws XPathException, SAXException, PermissionDeniedException {
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
        pool = startDB();

        final TransactionManager transact = pool.getTransactionManager();
        try(final Txn transaction = transact.beginTransaction()) {

            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
            broker.saveCollection(transaction, root);

            String existHome = System.getProperty("exist.home");
            File existDir = existHome==null ? new File(".") : new File(existHome);
            String directory = "samples/shakespeare";
            File dir = new File(existDir, directory);

            // store some documents.
            for(File f : dir.listFiles(new XMLFilenameFilter())) {
                IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create(f.getName()), new InputSource(f.toURI().toASCIIString()));
                root.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
            }

            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("nested.xml"), NESTED_XML);
            root.store(transaction, broker, info, NESTED_XML, false);
            transact.commit(transaction);
            
            
            //for the tests
            docs = root.allDocs(broker, new DefaultDocumentSet(), true);
            seqSpeech = executeQuery(broker, "//SPEECH", 2628, null);
            
        } catch(Exception e) {
            if (pool != null) {
                pool.release(broker);
                BrokerPool.stopAll(false);
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
    public static void tearDown() throws PermissionDeniedException, IOException, TriggerException, TransactionException {
        
        final TransactionManager transact = pool.getTransactionManager();
        try(final Txn transaction = transact.beginTransaction()) {
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
//          broker.removeCollection(transaction, root);

            transact.commit(transaction);
        } finally {
            pool.release(broker);
            BrokerPool.stopAll(false);
        }
    }
}
