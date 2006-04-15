package org.exist.dom.test;

import org.custommonkey.xmlunit.XMLTestCase;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeVisitor;
import org.exist.dom.StoredNode;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Tests basic DOM methods like getChildNodes(), getAttribute() ...
 * 
 * @author wolf
 *
 */
public class NodeTest extends XMLTestCase {

	public static void main(String[] args) {
		junit.textui.TestRunner.run(NodeTest.class);
	}

	private static final String XML =
		"<test xmlns:ns=\"http://foo.org\">" +
		"	<a ns:a=\"1\" ns:b=\"m\">abc</a>" +
		"	<b ns:a=\"2\">def</b>" +
        "   <c>ghi</c>" +
        "   <d>jkl</d>" +
		"</test>";
	
	private BrokerPool pool = null;
	private Collection root = null;
	
	public void testChildAxis() {
		DBBroker broker = null;
		DocumentImpl doc = null;
        try {
        	assertNotNull(pool);
            broker = pool.get(SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            
            doc = root.getDocumentWithLock(broker, "test.xml");
            Element rootNode = doc.getDocumentElement();
            
            System.out.println("Testing getChildNodes() ...");
            NodeList cl = rootNode.getChildNodes();
            assertEquals(((StoredNode)rootNode).getChildCount(), cl.getLength());
            assertEquals(4, cl.getLength());
        	assertEquals(cl.item(0).getNodeName(), "a");
        	assertEquals(cl.item(1).getNodeName(), "b");
        	
        	System.out.println("Testing getFirstChild() ...");
        	Node first = cl.item(1).getFirstChild();
            assertEquals(first.getNodeValue(), "def");
            
            System.out.println("Testing getChildNodes() ...");
        	cl = cl.item(0).getChildNodes();
        	assertEquals(1, cl.getLength());
        	assertEquals(cl.item(0).getNodeValue(), "abc");
        	
        	System.out.println("Testing getParentNode() ...");
        	Node parent = cl.item(0).getParentNode();
        	assertNotNull(parent);
        	assertEquals(parent.getNodeName(), "a");
        	
        	parent = parent.getParentNode();
        	assertNotNull(parent);
        	assertEquals(parent.getNodeName(), "test");
        } catch (Exception e) {
        	e.printStackTrace();
	        fail(e.getMessage());
        } finally {
        	if (doc != null) doc.getUpdateLock().release();
        	if (pool != null) pool.release(broker);
        }
	}
	
    public void testSiblingAxis() {
        DBBroker broker = null;
        DocumentImpl doc = null;
        try {
            assertNotNull(pool);
            broker = pool.get(SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            
            System.out.println("testSiblingAxis() ...");
            
            doc = root.getDocumentWithLock(broker, "test.xml");
            Element rootNode = doc.getDocumentElement();
            Element child = (Element) rootNode.getFirstChild();
            assertNotNull(child);
            assertEquals(child.getNodeName(), "a");
            Node sibling = child.getNextSibling();
            assertNotNull(sibling);
            assertEquals(sibling.getNodeName(), "b");
            while (sibling != null) {
                System.out.println(sibling);
                sibling = sibling.getNextSibling();
            }
            
            NodeList cl = rootNode.getChildNodes();
            sibling = cl.item(2).getFirstChild();
            System.out.println("Sibling = " + sibling);
            sibling = sibling.getNextSibling();
            // should be null - there's no following sibling
            System.out.println("Sibling = " + sibling);
            
            int count = 0;
            sibling = cl.item(3);
            while (sibling != null) {
                System.out.println(sibling);
                sibling = sibling.getPreviousSibling();
                count++;
            }
            assertEquals(count, 4);
            System.out.println("testSiblingAxis(): PASS");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (doc != null) doc.getUpdateLock().release();
            if (pool != null) pool.release(broker);
        }
    }
    
	public void testAttributeAxis() {
		DBBroker broker = null;
		DocumentImpl doc = null;
        try {
        	assertNotNull(pool);
            broker = pool.get(SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            
            doc = root.getDocumentWithLock(broker, "test.xml");
            Element rootNode = doc.getDocumentElement();
            Element first = (Element) rootNode.getFirstChild();
            assertEquals(first.getNodeName(), "a");
            
            assertEquals(first.getAttribute("ns:a"), "1");
            assertEquals(first.getAttributeNS("http://foo.org", "a"), "1");
            
            Attr attr = first.getAttributeNode("ns:a");
            assertNotNull(attr);
            assertEquals(attr.getLocalName(), "a");
            assertEquals(attr.getNamespaceURI(), "http://foo.org");
            assertEquals(attr.getValue(), "1");
            
            Node parent = attr.getOwnerElement();
            assertNotNull(parent);
            assertEquals(parent.getNodeName(), "a");
            
            parent = attr.getParentNode();
            assertNotNull(parent);
            assertEquals(parent.getNodeName(), "a");
            
            attr = first.getAttributeNodeNS("http://foo.org", "a");
            assertNotNull(attr);
            assertEquals(attr.getLocalName(), "a");
            assertEquals(attr.getNamespaceURI(), "http://foo.org");
            assertEquals(attr.getValue(), "1");
            
            NamedNodeMap map = first.getAttributes();
            assertEquals(2, map.getLength());
            for (int i = 0; i < map.getLength(); i++)
            	System.out.println(map.item(i).getNodeName());
            attr = (Attr) map.getNamedItemNS("http://foo.org", "b");
            assertNotNull(attr);
            assertEquals(attr.getLocalName(), "b");
            assertEquals(attr.getNamespaceURI(), "http://foo.org");
            assertEquals(attr.getValue(), "m");
        } catch (Exception e) {
        	e.printStackTrace();
	        fail(e.getMessage());
        } finally {
        	if (doc != null) doc.getUpdateLock().release();
        	if (pool != null) pool.release(broker);
        }
	}
	
    public void testVisitor() {
        DBBroker broker = null;
        DocumentImpl doc = null;
        try {
            assertNotNull(pool);
            broker = pool.get(SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            
            System.out.println("testVisitor() ...");
            
            doc = root.getDocumentWithLock(broker, "test.xml");
            StoredNode rootNode = (StoredNode) doc.getDocumentElement();
            NodeVisitor visitor = new NodeVisitor() {
                public boolean visit(StoredNode node) {
                    System.out.println(node.getNodeId() + "\t" + node.getNodeName());
                    return true;
                };
            };
            rootNode.accept(visitor);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (doc != null) doc.getUpdateLock().release();
            if (pool != null) pool.release(broker);
        }
    }
    
	protected void setUp() throws Exception {        
        DBBroker broker = null;
        try {
        	pool = startDB();
        	assertNotNull(pool);
            broker = pool.get(SecurityManager.SYSTEM_USER);
            assertNotNull(broker);            
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Txn transaction = transact.beginTransaction();
            assertNotNull(transaction);            
            System.out.println("NodeTest#setUp ...");
            
            root = broker.getOrCreateCollection(transaction, DBBroker.ROOT_COLLECTION + "/test");
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            
            IndexInfo info = root.validateXMLResource(transaction, broker, "test.xml", XML);
            assertNotNull(info);
            root.store(transaction, broker, info, XML, false);
            
            transact.commit(transaction);
            System.out.println("NodeTest#setUp finished.");
        } catch (Exception e) {            
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
        BrokerPool.stopAll(false);
    }
}
