package org.exist.dom.persistent;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests basic DOM methods like getChildNodes(), getAttribute() ...
 * 
 * @author wolf
 *
 */
public class NodeTest {

	private static final String XML =
		"<!-- doc starts here -->" +
        "<test xmlns:ns=\"http://foo.org\">" +
		"	<a ns:a=\"1\" ns:b=\"m\">abc</a>" +
		"	<b ns:a=\"2\">def</b>" +
        "   <c>ghi</c>" +
        "   <d>jkl</d>" +
		"</test>";
	
	private BrokerPool pool = null;
	private Collection root = null;

    @Test
    public void document() throws EXistException, LockException, PermissionDeniedException {
        DocumentImpl doc = null;
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            doc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"),Lock.READ_LOCK);
            NodeList children = doc.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                IStoredNode node = (IStoredNode) children.item(i);
                node.getNodeId();
                node.getNodeName();
            }
        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
        }
    }

    @Test
	public void childAxis() throws EXistException, LockException, PermissionDeniedException {
		DocumentImpl doc = null;
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            doc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"),Lock.READ_LOCK);
            Element rootNode = doc.getDocumentElement();
            
            //Testing getChildNodes()
            NodeList cl = rootNode.getChildNodes();
            assertEquals(((IStoredNode)rootNode).getChildCount(), cl.getLength());
            assertEquals(4, cl.getLength());
        	assertEquals(cl.item(0).getNodeName(), "a");
        	assertEquals(cl.item(1).getNodeName(), "b");
        	
        	//Testing getFirstChild()
        	StoredNode node = (StoredNode) cl.item(1).getFirstChild();
            assertEquals(node.getNodeValue(), "def");
            
            //Testing getChildNodes()
            node = (StoredNode) cl.item(0);
            assertEquals(3, node.getChildCount());
            assertEquals(2, node.getAttributes().getLength());
        	cl = node.getChildNodes();
        	assertEquals(3, cl.getLength());
        	assertEquals(cl.item(2).getNodeValue(), "abc");
        	
        	//Testing getParentNode()
        	Node parent = cl.item(0).getParentNode();
        	assertNotNull(parent);
        	assertEquals(parent.getNodeName(), "a");
        	
        	parent = parent.getParentNode();
        	assertNotNull(parent);
        	assertEquals(parent.getNodeName(), "test");
        } finally {
        	if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
        }
	}

    @Test
    public void siblingAxis() throws EXistException, LockException, PermissionDeniedException {
        DocumentImpl doc = null;
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            
            doc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"),Lock.READ_LOCK);
            Element rootNode = doc.getDocumentElement();
            Element child = (Element) rootNode.getFirstChild();
            assertNotNull(child);
            assertEquals(child.getNodeName(), "a");
            Node sibling = child.getNextSibling();
            assertNotNull(sibling);
            assertEquals(sibling.getNodeName(), "b");
            while (sibling != null) {
                sibling = sibling.getNextSibling();
            }
            
            NodeList cl = rootNode.getChildNodes();
            sibling = cl.item(2).getFirstChild();
            sibling = sibling.getNextSibling();
            // should be null - there's no following sibling
            
            int count = 0;
            sibling = cl.item(3);
            while (sibling != null) {
                sibling = sibling.getPreviousSibling();
                count++;
            }
            assertEquals(count, 4);
        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
        }
    }

    @Test
	public void attributeAxis() throws EXistException, LockException, PermissionDeniedException {
		DocumentImpl doc = null;
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            doc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"),Lock.READ_LOCK);
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

            attr = (Attr) map.getNamedItemNS("http://foo.org", "b");
            assertNotNull(attr);
            assertEquals(attr.getLocalName(), "b");
            assertEquals(attr.getNamespaceURI(), "http://foo.org");
            assertEquals(attr.getValue(), "m");
        } finally {
        	if (doc != null) doc.getUpdateLock().release(Lock.READ_LOCK);
        }
	}

    @Deprecated
	@Test
    public void visitor() throws EXistException, LockException, PermissionDeniedException {

        DocumentImpl doc = null;
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            
            doc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"));
            StoredNode rootNode = (StoredNode) doc.getDocumentElement();
            NodeVisitor visitor = new NodeVisitor() {
                @Override
                public boolean visit(IStoredNode node) {
                    node.getNodeId();
                    node.getNodeName();
                    return true;
                };
            };
            rootNode.accept(visitor);
        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
        }
    }
    
	@Before
    public void setUp() throws Exception {
        pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
            final Txn transaction = transact.beginTransaction()) {
            
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            
            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), XML);
            //TODO : unlock the collection here ?
            assertNotNull(info);
            root.store(transaction, broker, info, XML, false);
            
            transact.commit(transaction);
        }
	}
	
	protected BrokerPool startDB() throws DatabaseConfigurationException, EXistException {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null) {
            home = System.getProperty("user.dir");
        }
        Configuration config = new Configuration(file, home);
        BrokerPool.configure(1, 5, config);
        return BrokerPool.getInstance();
    }

    @After
    public void tearDown() throws EXistException, PermissionDeniedException, IOException, TriggerException {

        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
            final Txn transaction = transact.beginTransaction()) {
            
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
            assertNotNull(root);
            broker.removeCollection(transaction, root);
            
            transact.commit(transaction);
        }

        BrokerPool.stopAll(false);
    }
}
