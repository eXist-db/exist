
package org.exist.dom;

import java.util.Iterator;
import java.util.Stack;
import org.dbxml.core.data.Value;
import org.exist.storage.*;
import org.exist.util.Configuration;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    5. September 2002
 */
public class DocumentIterator implements Iterator {

    DBBroker broker;
    DocumentImpl doc;
    Iterator iterator = null;
    NodeImpl nextNode = null;
    Stack stack = new Stack();


    /**
     *  Constructor for the DocumentIterator object
     *
     *@param  doc     Description of the Parameter
     *@param  broker  Description of the Parameter
     */
    public DocumentIterator( DBBroker broker, DocumentImpl doc ) {
        this.broker = broker;
        this.doc = doc;
        ElementImpl n = (ElementImpl) doc.getDocumentElement();
        stack.push( new InternalNode( n ) );
        iterator =
            broker.getDOMIterator( doc, n.getGID() );
        iterator.next();
        nextNode = n;
    }


    /**
     *  Description of the Method
     *
     *@param  args  Description of the Parameter
     */
    public static void main( String args[] ) {
        String pathSep = System.getProperty( "file.separator", "/" );
        String home = System.getProperty( "exist.home" );
        if ( home == null )
            home = System.getProperty( "user.dir" );

        try {
            Configuration config = new Configuration( "conf.xml", home );
            BrokerPool.configure( 1, 5, config );
            BrokerPool pool = BrokerPool.getInstance();
            DBBroker broker = pool.get();
            DocumentImpl doc = (DocumentImpl) broker.getDocument( args[0] );
            DocumentIterator i = new DocumentIterator( broker, doc );
            NodeImpl n;
            while ( ( n = (NodeImpl) i.next() ) != null )
                switch ( n.getNodeType() ) {
                    case Node.ELEMENT_NODE:
                        System.out.println( '<' + n.getNodeName() + '>' );
                        break;
                    case Node.ATTRIBUTE_NODE:
                        System.out.println( '@' + n.getNodeName() + '=' +
                            n.getNodeValue() );
                        break;
                    default:
                        System.out.println( n.getNodeValue() );
                }

            pool.release( broker );
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public boolean hasNext() {
        return nextNode != null;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public Object next() {
        if ( nextNode == null )
            return null;
        NodeImpl tempNode = nextNode;
        if ( stack.size() == 0 ) {
            nextNode = null;
            return tempNode;
        }
        InternalNode parent = (InternalNode) stack.peek();
        Value value = (Value) iterator.next();
        if ( value != null ) {
            if ( ++parent.childCount == parent.node.getChildCount() )
                stack.pop();
            nextNode = NodeImpl.deserialize( value.getData(), doc );
            nextNode.setOwnerDocument( doc );
            if ( nextNode.getNodeType() == Node.ELEMENT_NODE )
                stack.push( new InternalNode( (ElementImpl) nextNode ) );
        }
        return tempNode;
    }


    /**  Description of the Method */
    public void remove() {
        throw new RuntimeException( "operation not implemented" );
    }


    private final class InternalNode {
        int childCount = 0;

        ElementImpl node;


        /**
         *  Constructor for the InternalNode object
         *
         *@param  node  Description of the Parameter
         */
        public InternalNode( ElementImpl node ) {
            this.node = node;
        }
    }
}

