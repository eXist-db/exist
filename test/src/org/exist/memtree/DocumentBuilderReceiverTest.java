package org.exist.memtree;

import org.easymock.classextension.EasyMock;
import static org.easymock.classextension.EasyMock.expect;
import static org.easymock.classextension.EasyMock.verify;
import static org.easymock.classextension.EasyMock.replay;
import org.exist.util.hashtable.NamePool;
import org.exist.xquery.XQueryContext;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 *
 * @author aretter
 */


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
}
