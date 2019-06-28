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
import org.exist.xquery.XPathException;
import org.junit.*;

import javax.xml.XMLConstants;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class ValueSequenceTest {

    @ClassRule
    public final static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

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

    @Test
    public void iterate_loop() throws XPathException {
        final ValueSequence valueSequence = mockValueSequence(99);

        final SequenceIterator it = valueSequence.iterate();
        int count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(99, count);
    }

    @Test
    public void iterate_skip_loop() throws XPathException {
        final ValueSequence valueSequence = mockValueSequence(99);
        final SequenceIterator it = valueSequence.iterate();

        assertEquals(99, it.skippable());

        assertEquals(10, it.skip(10));

        assertEquals(89, it.skippable());

        int count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(89, count);
    }

    @Test
    public void iterate_loop_skip_loop() throws XPathException {
        final ValueSequence valueSequence = mockValueSequence(99);
        final SequenceIterator it = valueSequence.iterate();

        int len = 20;
        int count = 0;
        for (int i = 0; it.hasNext() && i < len; i++) {
            it.nextItem();
            count++;
        }
        assertEquals(20, count);

        assertEquals(79, it.skippable());

        assertEquals(10, it.skip(10));

        assertEquals(69, it.skippable());

        count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(69, count);
    }

    private static ValueSequence mockValueSequence(final int size) throws XPathException {
        final ValueSequence valueSequence = new ValueSequence();
        for (int i = 0; i < size; i++) {
            valueSequence.add(new StringValue(String.valueOf(i), Type.STRING));
        }
        return valueSequence;
    }
}
