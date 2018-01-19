package org.exist.dom.memtree;

import com.googlecode.junittoolbox.ParallelRunner;
import org.easymock.EasyMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.replay;
import org.exist.util.hashtable.NamePool;
import org.exist.xquery.XQueryContext;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 *
 * @author aretter
 */
@RunWith(ParallelRunner.class)
public class DocumentBuilderReceiverTest {

    private static String ATOM_NS = "http://www.w3.org/2005/Atom";
//    private static String ATOM_PREFIX = "atom";
    
    @Test
    public void when_prefix_is_known_in_context_dont_use_if_namespace_equals_default_namespace() throws SAXException {
        
        final String entry_name = "entry";
        final String id_name = "id";
        
        XQueryContext mockContext = EasyMock.createMock(XQueryContext.class);
        
        expect(mockContext.getDatabase()).andReturn(null);
        expect(mockContext.getSharedNamePool()).andReturn(new NamePool());
        //expect(mockContext.getPrefixForURI(ATOM_NS)).andReturn(ATOM_PREFIX).times(2);
        
        replay(mockContext);
        
        MemTreeBuilder builder = new MemTreeBuilder(mockContext);
        DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder, true);
        
        builder.startDocument();
        
        receiver.startPrefixMapping("", ATOM_NS);
        receiver.startElement(ATOM_NS, entry_name, entry_name, null);
        
        receiver.startElement(ATOM_NS, id_name, id_name, null);
        receiver.endElement(ATOM_NS, id_name, id_name);
        
        
        receiver.endElement(ATOM_NS, entry_name, entry_name);
        
        builder.endDocument();
        
        verify(mockContext);
        
        Document doc = builder.getDocument();
        Node entryNode = doc.getFirstChild();
        
        assertEquals(entry_name, entryNode.getNodeName());
        
        Node idNode = entryNode.getFirstChild();
        assertEquals(id_name, idNode.getNodeName());
    }

    @Test
    public void use_given_namespace_prefix() throws SAXException {
        // if an explicit namespace prefix is present, it should be used
        // unless a different mapping was defined in context
        final String title = "title";
        final String titleQName = "atom:title";

        XQueryContext mockContext = EasyMock.createMock(XQueryContext.class);

        expect(mockContext.getDatabase()).andReturn(null);
        expect(mockContext.getSharedNamePool()).andReturn(new NamePool());
        // no namespace mapping in context
        expect(mockContext.getPrefixForURI(ATOM_NS)).andReturn(null);

        replay(mockContext);

        MemTreeBuilder builder = new MemTreeBuilder(mockContext);

        DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder, true);

        builder.startDocument();

        receiver.startElement(ATOM_NS, title, titleQName, null);
        receiver.endElement(ATOM_NS, title, titleQName);

        builder.endDocument();

        verify(mockContext);

        Document doc = builder.getDocument();
        Node entryNode = doc.getFirstChild();

        assertEquals("Explicit namespace prefix should be preserved", titleQName, entryNode.getNodeName());
    }

    @Test
    public void use_namespace_prefix_from_context() throws SAXException {
        // if a namespace is mapped in the current context, use its prefix and overwrite
        // local prefix
        final String title = "title";
        final String titleQName = "atom:title";

        XQueryContext mockContext = EasyMock.createMock(XQueryContext.class);

        expect(mockContext.getDatabase()).andReturn(null);
        expect(mockContext.getSharedNamePool()).andReturn(new NamePool());
        // namespace mapping in context
        expect(mockContext.getPrefixForURI(ATOM_NS)).andReturn("a");

        replay(mockContext);

        MemTreeBuilder builder = new MemTreeBuilder(mockContext);

        DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder, true);

        builder.startDocument();

        receiver.startElement(ATOM_NS, title, titleQName, null);
        receiver.endElement(ATOM_NS, title, titleQName);

        builder.endDocument();

        verify(mockContext);

        Document doc = builder.getDocument();
        Node entryNode = doc.getFirstChild();

        assertEquals("Explicit namespace prefix should be preserved", "a:title", entryNode.getNodeName());
    }
}
