package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.numbering.DLN;
import org.exist.numbering.NodeId;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class IntersectTest {

    /**
     * Tests the XQuery `intersect` operator against an
     * in-memory node on both the left and right sides
     */
    @Test
    public void memtree_intersect_memtree() throws XPathException, NoSuchMethodException {

        final XQueryContext mockContext = createMock(XQueryContext.class);
        final PathExpr mockLeft = createMock(PathExpr.class);
        final PathExpr mockRight = createMock(PathExpr.class);
        final Sequence mockContextSequence = createMock(Sequence.class);
        final Item mockContextItem = createMock(Item.class);
        final Profiler mockProfiler = createMock(Profiler.class);

        expect(mockContext.nextExpressionId()).andReturn(1);
        expect(mockContext.getProfiler()).andReturn(mockProfiler);

        final org.exist.dom.memtree.ElementImpl memElement = (org.exist.dom.memtree.ElementImpl)createInMemoryDocument().getDocumentElement();

        expect(mockLeft.eval(mockContextSequence, mockContextItem)).andReturn(memElement); //memtree node
        expect(mockRight.eval(mockContextSequence, mockContextItem)).andReturn(memElement); //same memtree node

        expect(mockContext.getProfiler()).andReturn(mockProfiler);

        replay(mockRight, mockLeft, mockContext);

        //test
        final Intersect intersect = new Intersect(mockContext, mockLeft, mockRight);
        final Sequence result = intersect.eval(mockContextSequence, mockContextItem);

        assertEquals(1, ((ValueSequence)result).size());

        verify(mockRight, mockLeft, mockContext);
    }

    /**
     * Tests the XQuery `intersect` operator against an
     * in-memory node on the left and a persistent node on the right
     */
    @Test
    public void memtree_intersect_persistent() throws XPathException, NoSuchMethodException {

        final XQueryContext mockContext = createMock(XQueryContext.class);
        final PathExpr mockLeft = createMock(PathExpr.class);
        final PathExpr mockRight = createMock(PathExpr.class);
        final Sequence mockContextSequence = createMock(Sequence.class);
        final Item mockContextItem = createMock(Item.class);
        final Profiler mockProfiler = createMock(Profiler.class);

        final DocumentImpl mockPersistentDoc = createMock(DocumentImpl.class);

        final NodeProxy mockPersistentNode = createMockBuilder(NodeProxy.class)
                .withConstructor(DocumentImpl.class, NodeId.class)
                .withArgs(mockPersistentDoc, new DLN(1))
                .addMockedMethods(
                        NodeProxy.class.getMethod("isEmpty", new Class[]{}),
                        NodeProxy.class.getMethod("getItemType", new Class[]{}),
                        NodeProxy.class.getMethod("equals", new Class[]{Object.class})
                ).createMock();

        expect(mockContext.nextExpressionId()).andReturn(1);
        expect(mockContext.getProfiler()).andReturn(mockProfiler);

        expect(mockLeft.eval(mockContextSequence, mockContextItem)).andReturn((org.exist.dom.memtree.ElementImpl)createInMemoryDocument().getDocumentElement()); //memtree node

        expect(mockRight.eval(mockContextSequence, mockContextItem)).andReturn(mockPersistentNode); //persistent node
        expect(mockPersistentNode.isEmpty()).andReturn(false);
        expect(mockPersistentNode.getItemType()).andReturn(Type.NODE);

        expect(mockContext.getProfiler()).andReturn(mockProfiler);

        replay(mockPersistentDoc, mockPersistentNode, mockRight, mockLeft, mockContext);

        //test
        final Intersect intersect = new Intersect(mockContext, mockLeft, mockRight);
        final Sequence result = intersect.eval(mockContextSequence, mockContextItem);

        assertEquals(0, ((ValueSequence)result).size());

        verify(mockPersistentDoc, mockPersistentNode, mockRight, mockLeft, mockContext);
    }

    /**
     * Tests the XQuery `intersect` operator against a
     * persistent node on the left and an in-memory node on the right
     */
    @Test
    public void persistent_intersect_memtree() throws XPathException, NoSuchMethodException {

        final XQueryContext mockContext = createMock(XQueryContext.class);
        final PathExpr mockLeft = createMock(PathExpr.class);
        final PathExpr mockRight = createMock(PathExpr.class);
        final Sequence mockContextSequence = createMock(Sequence.class);
        final Item mockContextItem = createMock(Item.class);
        final Profiler mockProfiler = createMock(Profiler.class);

        final DocumentImpl mockPersistentDoc = createMock(DocumentImpl.class);

        final NodeProxy mockPersistentNode = createMockBuilder(NodeProxy.class)
                .withConstructor(DocumentImpl.class, NodeId.class)
                .withArgs(mockPersistentDoc, new DLN(1))
                .addMockedMethods(
                        NodeProxy.class.getMethod("isEmpty", new Class[]{}),
                        NodeProxy.class.getMethod("getItemType", new Class[]{}),
                        NodeProxy.class.getMethod("equals", new Class[]{Object.class})
                ).createMock();

        expect(mockContext.nextExpressionId()).andReturn(1);
        expect(mockContext.getProfiler()).andReturn(mockProfiler);

        expect(mockLeft.eval(mockContextSequence, mockContextItem)).andReturn(mockPersistentNode); //persistent node
        expect(mockRight.eval(mockContextSequence, mockContextItem)).andReturn((org.exist.dom.memtree.ElementImpl)createInMemoryDocument().getDocumentElement()); //memtree node

        expect(mockPersistentNode.isEmpty()).andReturn(false);
        expect(mockPersistentNode.getItemType()).andReturn(Type.NODE);

        expect(mockPersistentDoc.getDocId()).andReturn(1).times(2);

        expect(mockContext.getProfiler()).andReturn(mockProfiler);

        replay(mockPersistentDoc, mockPersistentNode, mockRight, mockLeft, mockContext);

        //test
        final Intersect intersect = new Intersect(mockContext, mockLeft, mockRight);
        final Sequence result = intersect.eval(mockContextSequence, mockContextItem);

        assertEquals(0, ((ValueSequence)result).size());

        verify(mockPersistentDoc, mockPersistentNode, mockRight, mockLeft, mockContext);
    }

    private Document createInMemoryDocument() {
        final MemTreeBuilder memtree = new MemTreeBuilder();
        memtree.startDocument();
            memtree.startElement(new QName("m1", XMLConstants.NULL_NS_URI), null);
                memtree.startElement(new QName("m2", XMLConstants.NULL_NS_URI), null);
                    memtree.characters("test data");
                memtree.endElement();
            memtree.endElement();
        memtree.endDocument();

        return memtree.getDocument();
    }
}
