package org.exist.xquery.value;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.*;

import javax.xml.XMLConstants;
import java.util.Optional;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class ValueSequenceTest {

    @ClassRule
    public final static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, false);

    @Test
    public void sortInDocumentOrder() throws EXistException, PermissionDeniedException, AuthenticationException {
        final ValueSequence seq = new ValueSequence(true);
        seq.keepUnOrdered(true);

        //in-memory doc
        final MemTreeBuilder memtree = new MemTreeBuilder();
        memtree.startDocument();
            memtree.startElement(new QName("m1", XMLConstants.NULL_NS_URI), null);
                memtree.startElement(new QName("m2", XMLConstants.NULL_NS_URI), null);
                    memtree.characters("test data");
                memtree.endElement();
            memtree.endElement();
        memtree.endDocument();

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Subject admin = pool.getSecurityManager().authenticate("admin", "");
        try(final DBBroker broker = pool.get(Optional.of(admin))) {

            //persistent doc
            final Collection sysCollection = broker.getCollection(SecurityManager.SECURITY_COLLECTION_URI);
            final DocumentImpl doc = sysCollection.getDocument(broker, XmldbURI.create("config.xml"));

            final NodeProxy docProxy = new NodeProxy(doc);
            final NodeProxy nodeProxy = new NodeProxy(doc, ((NodeImpl)doc.getFirstChild()).getNodeId());

            seq.add(memtree.getDocument());
            seq.add(docProxy);
            seq.add((org.exist.dom.memtree.NodeImpl)memtree.getDocument().getFirstChild());
            seq.add(nodeProxy);

            //call sort
            seq.sortInDocumentOrder();
        }
    }
}
