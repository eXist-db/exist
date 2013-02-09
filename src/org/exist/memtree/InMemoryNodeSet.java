package org.exist.memtree;

import org.w3c.dom.Node;

import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.HashSet;
import java.util.Set;

/**
 * DOCUMENT ME!
 */
public class InMemoryNodeSet extends ValueSequence {

    public final static InMemoryNodeSet EMPTY = new InMemoryNodeSet( 0 );

    public InMemoryNodeSet() {
        super();
    }

    public InMemoryNodeSet( int initialSize ) {
        super( initialSize );
    }

    public InMemoryNodeSet( Sequence otherSequence ) throws XPathException {
        super( otherSequence );
        final Set<DocumentImpl> docs = new HashSet<DocumentImpl>();
        for( int i = 0; i <= size; i++ ) {
            final NodeImpl node = (NodeImpl)values[i];
            docs.add( node.getDocument() );
        }
        for( final DocumentImpl doc : docs ) {
            doc.expand();
        }
    }

    @Override
    public Sequence getAttributes( NodeTest test ) throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for( int i = 0; i <= size; i++ ) {
            final NodeImpl node = (NodeImpl)values[i];
            node.selectAttributes( test, nodes );
        }
        return( nodes );
    }

    @Override
    public Sequence getDescendantAttributes( NodeTest test ) throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for( int i = 0; i <= size; i++ ) {
            final NodeImpl node = (NodeImpl)values[i];
            node.selectDescendantAttributes( test, nodes );
        }
        return( nodes );
    }

    @Override
    public Sequence getChildren( NodeTest test ) throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for( int i = 0; i <= size; i++ ) {
            final NodeImpl node = (NodeImpl)values[i];
            node.selectChildren( test, nodes );
        }
        return( nodes );
    }

    @Override
    public Sequence getChildrenForParent( NodeImpl parent ) {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for( int i = 0; i <= size; i++ ) {
            final NodeImpl node = (NodeImpl)values[i];
            if( node.getNodeId().isChildOf( parent.getNodeId() ) ) {
                nodes.add( node );
            }
        }
        return( nodes );
    }

    @Override
    public Sequence getDescendants( boolean includeSelf, NodeTest test ) 
            throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for( int i = 0; i <= size; i++ ) {
            final NodeImpl node = (NodeImpl)values[i];
            node.selectDescendants( includeSelf, test, nodes );
        }
        return( nodes );
    }

    @Override
    public Sequence getAncestors( boolean includeSelf, NodeTest test ) 
            throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for( int i = 0; i <= size; i++ ) {
            final NodeImpl node = (NodeImpl)values[i];
            node.selectAncestors( includeSelf, test, nodes );
        }
        return( nodes );
    }

    @Override
    public Sequence getParents( NodeTest test ) throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for( int i = 0; i <= size; i++ ) {
            final NodeImpl node   = (NodeImpl)values[i];
            final NodeImpl parent = (NodeImpl)node.selectParentNode();
            if( ( parent != null ) && test.matches( parent ) ) {
                nodes.add( parent );
            }
        }
        return( nodes );
    }

    @Override
    public Sequence getSelf( NodeTest test ) throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for( int i = 0; i <= size; i++ ) {
            final NodeImpl node = (NodeImpl)values[i];
            if( ( ( test.getType() == Type.NODE ) && ( node.getNodeType() == Node.ATTRIBUTE_NODE ) ) || test.matches( node ) ) {
                nodes.add( node );
            }
        }
        return( nodes );
    }

    @Override
    public Sequence getPrecedingSiblings( NodeTest test ) throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for( int i = 0; i <= size; i++ ) {
            final NodeImpl node = (NodeImpl)values[i];
            node.selectPrecedingSiblings( test, nodes );
        }
        return( nodes );
    }

    @Override
    public Sequence getFollowingSiblings( NodeTest test ) throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for( int i = 0; i <= size; i++ ) {
            final NodeImpl node = (NodeImpl)values[i];
            node.selectFollowingSiblings( test, nodes );
        }
        return( nodes );
    }
}
