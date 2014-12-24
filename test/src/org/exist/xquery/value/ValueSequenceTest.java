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
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class ValueSequenceTest {

    private static BrokerPool pool;

    @Test
    public void sortInDocumentOrder() throws EXistException, PermissionDeniedException, AuthenticationException {
        final ValueSequence seq = new ValueSequence(true);
        seq.keepUnOrdered(true);

        //in-memory doc
        final MemTreeBuilder memtree = new MemTreeBuilder();
        memtree.startDocument();
            memtree.startElement(new QName("m1"), null);
                memtree.startElement(new QName("m2"), null);
                    memtree.characters("test data");
                memtree.endElement();
            memtree.endElement();
        memtree.endDocument();

        final Subject admin = pool.getSecurityManager().authenticate("admin", "");
        try(final DBBroker broker = pool.get(admin)) {

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

    @BeforeClass
    public static void startDb() throws EXistException, DatabaseConfigurationException {
        final String conf = "conf.xml";
        final String home = System.getProperty("exist.home", System.getProperty("user.dir"));
        final Configuration config = new Configuration(conf, home);
        BrokerPool.configure(1, 5, config);
        pool = BrokerPool.getInstance();
    }

    @AfterClass
    public static void stopDb() {
        pool = null;
        BrokerPool.stopAll(false);
    }
}
