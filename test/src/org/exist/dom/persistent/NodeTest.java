package org.exist.dom.persistent;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.util.Optional;

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
	"<a ns:a=\"1\" ns:b=\"m\">abc</a>" +
	"<b ns:a=\"2\">def</b>" +
        "<c>ghi</c>" +
        "<d>jkl</d>" +
	"</test>";
	private static Collection root = null;

    @Test
    public void document() throws EXistException, LockException, PermissionDeniedException {
        DocumentImpl doc = null;
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            doc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"),LockMode.READ_LOCK);
            final NodeList children = doc.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                final IStoredNode node = (IStoredNode<?>) children.item(i);
                node.getNodeId();
                node.getNodeName();
            }
        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(LockMode.READ_LOCK);
            }
        }
    }

    @Test
	public void childAxis() throws EXistException, LockException, PermissionDeniedException {
		DocumentImpl doc = null;
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            doc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"),LockMode.READ_LOCK);
            final Element rootNode = doc.getDocumentElement();
            
            //Testing getChildNodes()
            NodeList cl = rootNode.getChildNodes();
            assertEquals(((IStoredNode<?>)rootNode).getChildCount(), cl.getLength());
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
                doc.getUpdateLock().release(LockMode.READ_LOCK);
            }
        }
	}

    @Test
    public void siblingAxis() throws EXistException, LockException, PermissionDeniedException {
        DocumentImpl doc = null;
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            doc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"),LockMode.READ_LOCK);
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
                doc.getUpdateLock().release(LockMode.READ_LOCK);
            }
        }
    }

    @Test
	public void attributeAxis() throws EXistException, LockException, PermissionDeniedException {
		DocumentImpl doc = null;
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            doc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"),LockMode.READ_LOCK);
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
        	if (doc != null) doc.getUpdateLock().release(LockMode.READ_LOCK);
        }
	}

    @Deprecated
	@Test
    public void visitor() throws EXistException, LockException, PermissionDeniedException {
        DocumentImpl doc = null;
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            
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
                doc.getUpdateLock().release(LockMode.READ_LOCK);
            }
        }
    }

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, false);

	@BeforeClass
    public static void setUp() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {
            
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            
            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), XML);
            //TODO : unlock the collection here ?
            assertNotNull(info);
            root.store(transaction, broker, info, XML);
            
            transact.commit(transaction);
        }
	}

    @AfterClass
    public static void tearDown() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {
            
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
            assertNotNull(root);
            broker.removeCollection(transaction, root);
            
            transact.commit(transaction);
        }
    }
}
