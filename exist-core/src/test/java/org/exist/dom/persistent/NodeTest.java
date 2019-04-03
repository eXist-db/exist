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
import org.w3c.dom.*;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests basic DOM methods like getChildNodes(), getAttribute() ...
 * 
 * @author wolf
 *
 */
public class NodeTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

	private static final String XML =
		"<!-- doc starts here -->" +
        "<test xmlns:ns=\"http://foo.org\">" +
            "<a ns:a=\"1\" ns:b=\"m\">abc</a>" +
            "<b ns:a=\"2\">def</b>" +
            "<c>ghi</c>" +
            "<d>jkl</d>" +
	    "</test>" +
        "<!-- doc ends here -->";
	private static Collection root = null;

    @Test
    public void document() throws EXistException, LockException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final LockedDocument lockedDoc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"),LockMode.READ_LOCK)) {
            final NodeList children = lockedDoc.getDocument().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                final IStoredNode node = (IStoredNode<?>) children.item(i);
                node.getNodeId();
                node.getNodeName();
            }
        }
    }

    @Test
	public void childAxis() throws EXistException, LockException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final LockedDocument lockedDoc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"),LockMode.READ_LOCK)) {

            NodeList cl = lockedDoc.getDocument().getChildNodes();
            assertEquals(3, cl.getLength());

            final Element docElement = lockedDoc.getDocument().getDocumentElement();
            
            //Testing getChildNodes()
            cl = docElement.getChildNodes();
            assertEquals(4, cl.getLength());
            assertEquals(4, ((IStoredNode<?>)docElement).getChildCount());
        	assertEquals("a", cl.item(0).getNodeName());
        	assertEquals("b", cl.item(1).getNodeName());
        	
        	//Testing getFirstChild()
        	StoredNode node = (StoredNode) cl.item(1).getFirstChild();
            assertEquals("def", node.getNodeValue());
            
            //Testing getChildNodes()
            node = (StoredNode) cl.item(0);
            assertEquals(3, node.getChildCount());
            assertEquals(2, node.getAttributes().getLength());
        	cl = node.getChildNodes();
            assertEquals(1, cl.getLength());
            assertEquals("abc", cl.item(0).getNodeValue());
        	
        	//Testing getParentNode()
            Node parent = cl.item(0).getParentNode();
            assertNotNull(parent);
            parent = node.getParentNode();
            assertEquals("test", parent.getNodeName());
            parent = parent.getParentNode();
            assertNotNull(parent);
            parent = parent.getParentNode();
            assertNull(parent);
        }
	}

    @Test
    public void siblingAxis() throws EXistException, LockException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final LockedDocument lockedDoc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"),LockMode.READ_LOCK)) {

            final Comment firstNode = (Comment)lockedDoc.getDocument().getFirstChild();
            assertNotNull(firstNode);
            assertNull(firstNode.getPreviousSibling());

            final Element secondNode = (Element)firstNode.getNextSibling();
            assertNotNull(secondNode);
            assertEquals(firstNode, secondNode.getPreviousSibling());

            final Comment thirdNode = (Comment)secondNode.getNextSibling();
            assertNotNull(thirdNode);
            assertEquals(secondNode, thirdNode.getPreviousSibling());
            assertNull(thirdNode.getNextSibling());

            final Element docElement = lockedDoc.getDocument().getDocumentElement();
            assertEquals(secondNode, docElement);
            final Element child = (Element) docElement.getFirstChild();
            assertNotNull(child);
            assertEquals("a", child.getNodeName(), "a");
            Node sibling = child.getNextSibling();
            assertNotNull(sibling);
            assertEquals("b", sibling.getNodeName());
            while (sibling != null) {
                sibling = sibling.getNextSibling();
            }
            
            final NodeList cl = docElement.getChildNodes();
            sibling = cl.item(2).getFirstChild();
            sibling = sibling.getNextSibling();
            // should be null - there's no following sibling
            
            int count = 0;
            sibling = cl.item(3);
            while (sibling != null) {
                sibling = sibling.getPreviousSibling();
                count++;
            }
            assertEquals(4, count);
        }
    }

    @Test
	public void attributeAxis() throws EXistException, LockException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final LockedDocument lockedDoc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"),LockMode.READ_LOCK)) {
            Element docElement = lockedDoc.getDocument().getDocumentElement();
            Element first = (Element) docElement.getFirstChild();
            assertEquals("a", first.getNodeName());
            
            assertEquals("1", first.getAttribute("ns:a"));
            assertEquals("1", first.getAttributeNS("http://foo.org", "a"));
            
            Attr attr = first.getAttributeNode("ns:a");
            assertNotNull(attr);
            assertEquals("a", attr.getLocalName());
            assertEquals("http://foo.org", attr.getNamespaceURI());
            assertEquals("1", attr.getValue());
            
            Node parent = attr.getOwnerElement();
            assertNotNull(parent);
            assertEquals("a", parent.getNodeName());
            
            parent = attr.getParentNode();
            assertNull(parent);
            
            attr = first.getAttributeNodeNS("http://foo.org", "a");
            assertNotNull(attr);
            assertEquals("a", attr.getLocalName());
            assertEquals("http://foo.org", attr.getNamespaceURI());
            assertEquals("1", attr.getValue());
            
            NamedNodeMap map = first.getAttributes();
            assertEquals(2, map.getLength());

            attr = (Attr) map.getNamedItemNS("http://foo.org", "b");
            assertNotNull(attr);
            assertEquals("b", attr.getLocalName());
            assertEquals("http://foo.org", attr.getNamespaceURI());
            assertEquals("m", attr.getValue());
        }
	}

    @Deprecated
	@Test
    public void visitor() throws EXistException, LockException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final LockedDocument lockedDoc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"),LockMode.READ_LOCK)) {
            StoredNode rootNode = (StoredNode) lockedDoc.getDocument().getDocumentElement();
            NodeVisitor visitor = new NodeVisitor() {
                @Override
                public boolean visit(IStoredNode node) {
                    node.getNodeId();
                    node.getNodeName();
                    return true;
                };
            };
            rootNode.accept(visitor);
        }
    }

	@BeforeClass
    public static void setUp() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {
            
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            
            final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), XML);
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
