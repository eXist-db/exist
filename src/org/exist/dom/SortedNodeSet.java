
package org.exist.dom;
import java.io.StringReader;
import java.util.Iterator;

import org.apache.log4j.Category;
import org.exist.parser.*;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.*;
import org.exist.util.*;
import org.exist.xpath.*;
import org.exist.EXistException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    28. August 2002
 */
public class SortedNodeSet extends NodeSet {

    private static Category LOG = Category.getInstance( SortedNodeSet.class.getName() );

    private PathExpr expr;
    private OrderedLinkedList list = new OrderedLinkedList();
    private DocumentSet ndocs;
    private String sortExpr;
	private BrokerPool pool;

    /**
     *  Constructor for the SortedNodeSet object
     *
     *@param  sortExpr  Description of the Parameter
     */
    public SortedNodeSet( BrokerPool pool, String sortExpr ) {
        this.sortExpr = sortExpr;
        this.pool = pool;
    }


    /**
     *  Adds a feature to the All attribute of the SortedNodeSet object
     *
     *@param  other  The feature to be added to the All attribute
     */
    public void addAll( NodeSet other ) {
        long start = System.currentTimeMillis();
        NodeProxy p;
        Item item;
        DocumentSet docs = new DocumentSet();
        for ( Iterator i = other.iterator(); i.hasNext();  ) {
            p = (NodeProxy) i.next();
            docs.add( p.doc );
        }
        try {
            XPathLexer lexer = new XPathLexer( new StringReader( sortExpr ) );

            XPathParser parser = new XPathParser( pool, new User( "admin", null, "dba" ), lexer );
            expr = new PathExpr(pool);
            parser.expr( expr );
            LOG.info( "query: " + expr.pprint() );
            if ( parser.foundErrors() )
                LOG.debug( parser.getErrorMsg() );
            ndocs = expr.preselect( docs );
        } catch ( antlr.RecognitionException re ) {
            LOG.debug( re );
        } catch ( antlr.TokenStreamException tse ) {
            LOG.debug( tse );
        } catch ( PermissionDeniedException e ) {
            LOG.debug( e );
        } catch ( EXistException e ) {
	    LOG.debug( e );
	}
        DBBroker broker = null;
            try {
                broker = pool.get();
                for ( Iterator i = other.iterator(); i.hasNext();  ) {
                    p = (NodeProxy) i.next();
                    item = new Item( broker, p );
                    list.add( item );
                }
            } catch( EXistException e ) {
            	LOG.debug("exception during sort", e);
            } finally {
                pool.release( broker );
            }
        LOG.debug( "sort-expression took " + ( System.currentTimeMillis() - start ) +
            "ms." );
    }


    /**
     *  Adds a feature to the All attribute of the SortedNodeSet object
     *
     *@param  other  The feature to be added to the All attribute
     */
    public void addAll( NodeList other ) {
        if ( !( other instanceof NodeSet ) )
            throw new RuntimeException( "not implemented!" );
        addAll( (NodeSet) other );
    }


    /**
     *  Description of the Method
     *
     *@param  doc     Description of the Parameter
     *@param  nodeId  Description of the Parameter
     *@return         Description of the Return Value
     */
    public boolean contains( DocumentImpl doc, long nodeId ) {
        return contains( new NodeProxy( doc, nodeId ) );
    }


    /**
     *  Description of the Method
     *
     *@param  proxy  Description of the Parameter
     *@return        Description of the Return Value
     */
    public boolean contains( NodeProxy proxy ) {
        NodeProxy p;
        for ( Iterator i = list.iterator(); i.hasNext();  ) {
            p = ( (Item) i.next() ).proxy;
            if ( p.compareTo( proxy ) == 0 )
                return true;
        }
        return false;
    }


    /**
     *  Description of the Method
     *
     *@param  pos  Description of the Parameter
     *@return      Description of the Return Value
     */
    public NodeProxy get( int pos ) {
        return ( (Item) list.get( pos ) ).proxy;
    }


    /**
     *  Description of the Method
     *
     *@param  doc     Description of the Parameter
     *@param  nodeId  Description of the Parameter
     *@return         Description of the Return Value
     */
    public NodeProxy get( DocumentImpl doc, long nodeId ) {
        NodeProxy p;
        NodeProxy proxy = new NodeProxy( doc, nodeId );
        for ( Iterator i = list.iterator(); i.hasNext();  ) {
            p = ( (Item) i.next() ).proxy;
            if ( p.compareTo( proxy ) == 0 )
                return p;
        }
        return null;
    }


    /**
     *  Gets the length attribute of the SortedNodeSet object
     *
     *@return    The length value
     */
    public int getLength() {
        return list.size();
    }


    /**
     *  Description of the Method
     *
     *@param  pos  Description of the Parameter
     *@return      Description of the Return Value
     */
    public Node item( int pos ) {
        NodeProxy p = ( (Item) list.get( pos ) ).proxy;
        return p == null ? null : p.doc.getNode( p.gid );
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public Iterator iterator() {
        return new SortedNodeSetIterator( list.iterator() );
    }


    private final static class SortedNodeSetIterator
         implements Iterator {

        Iterator pi;


        /**
         *  Constructor for the SortedNodeSetIterator object
         *
         *@param  i  Description of the Parameter
         */
        public SortedNodeSetIterator( Iterator i ) {
            pi = i;
        }


        /**
         *  Description of the Method
         *
         *@return    Description of the Return Value
         */
        public boolean hasNext() {
            return pi.hasNext();
        }


        /**
         *  Description of the Method
         *
         *@return    Description of the Return Value
         */
        public Object next() {
            if ( !pi.hasNext() )
                return null;
            return ( (Item) pi.next() ).proxy;
        }


        /**  Description of the Method */
        public void remove() {
        }
    }


    private final class Item implements Comparable {
        NodeProxy proxy;
        String value = null;


        /**
         *  Constructor for the Item object
         *
         *@param  proxy   Description of the Parameter
         *@param  broker  Description of the Parameter
         */
        public Item( DBBroker broker, NodeProxy proxy ) {
            this.proxy = proxy;
            ArraySet context = new ArraySet( 1 );
            context.add( proxy );
            Value v = expr.eval( ndocs, context, proxy );
            StringBuffer buf = new StringBuffer();
            OrderedLinkedList strings = new OrderedLinkedList();
            switch ( v.getType() ) {
                case Value.isNodeList:
                    NodeSet resultSet = (NodeSet) v.getNodeList();
                    if ( resultSet.getLength() == 0 )
                        return;
                    NodeProxy p;
                    for ( Iterator i = resultSet.iterator(); i.hasNext();  ) {
                        p = (NodeProxy) i.next();
                        strings.add( broker.getNodeValue( p ).toUpperCase() );
                    }

                    for ( Iterator j = strings.iterator(); j.hasNext();  )
                        buf.append( (String) j.next() );
                    value = buf.toString();
                    break;
                default:
                    ValueSet valueSet = v.getValueSet();
                    if ( valueSet.getLength() == 0 )
                        return;
                    for ( int k = 0; k < valueSet.getLength(); k++ ) {
                        v = valueSet.get( k );
                        strings.add( v.getStringValue().toUpperCase() );
                    }
                    for ( Iterator j = strings.iterator(); j.hasNext();  )
                        buf.append( (String) j.next() );
                    value = buf.toString();
                    break;
            }
        }


        /**
         *  Description of the Method
         *
         *@param  other  Description of the Parameter
         *@return        Description of the Return Value
         */
        public int compareTo( Object other ) {
            Item o = (Item) other;
            if ( value == null )
                return o.value == null ? 0 : 1;
            if ( o.value == null )
                return value == null ? 0 : -1;
            return value.compareTo( o.value );
        }
    }
}

