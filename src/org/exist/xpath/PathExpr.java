
/*
 *  eXist xml document repository and xpath implementation
 *  Copyright (C) 2000,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.exist.xpath;

import java.util.Iterator;
import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.BrokerPool;

public class PathExpr implements Expression {
    protected static Logger LOG = Logger.getLogger( PathExpr.class );
    protected DocumentSet docs = new DocumentSet();
    protected boolean keepVirtual = false;
    protected BrokerPool pool = null;
    protected LinkedList steps = new LinkedList();


    /**  Constructor for the PathExpr object */
    public PathExpr(BrokerPool pool) {
		this.pool = pool;
    }


    /**
     *  Description of the Method
     *
     *@param  s  Description of the Parameter
     */
    public void add( Expression s ) {
        steps.add( s );
    }


    /**
     *  Description of the Method
     *
     *@param  path  Description of the Parameter
     */
    public void add( PathExpr path ) {
        for ( Iterator i = path.steps.iterator(); i.hasNext();  )
            add( (Expression) i.next() );
    }


    /**
     *  Adds a feature to the Document attribute of the PathExpr object
     *
     *@param  doc  The feature to be added to the Document attribute
     */
    public void addDocument( DocumentImpl doc ) {
        docs.add( doc );
    }


    /**
     *  Adds a feature to the Path attribute of the PathExpr object
     *
     *@param  path  The feature to be added to the Path attribute
     */
    public void addPath( PathExpr path ) {
        steps.add( path );
    }


    /**
     *  Adds a feature to the Predicate attribute of the PathExpr object
     *
     *@param  pred  The feature to be added to the Predicate attribute
     */
    public void addPredicate( Predicate pred ) {
        Expression e = (Expression) steps.getLast();
        if ( e instanceof Step )
            ( (Step) e ).addPredicate( pred );
        else
            System.out.println( "not a Step" );
    }


    /**
     *  Description of the Method
     *
     *@param  docs     Description of the Parameter
     *@param  context  Description of the Parameter
     *@param  node     Description of the Parameter
     *@return          Description of the Return Value
     */
    public Value eval( DocumentSet docs, NodeSet context, NodeProxy node ) {
        if ( docs.getLength() == 0 )
            return new ValueNodeSet( new ArraySet( 1 ) );
        Value r;
        if ( context != null )
            r = new ValueNodeSet( context );
        else
            r = new ValueNodeSet( new ArraySet( 1 ) );
        NodeSet set;
        Expression expr;
        for ( Iterator iter = steps.iterator(); iter.hasNext();  ) {
            set = (NodeSet) r.getNodeList();
            expr = (Expression) iter.next();
            if ( expr.returnsType() != Constants.TYPE_NODELIST ) {
                if ( expr instanceof Literal || expr instanceof IntNumber )
                    return expr.eval( docs, set, null );
                ValueSet values = new ValueSet();
                for ( Iterator iter2 = set.iterator(); iter2.hasNext();  )
                    values.add( expr.eval( docs, set, (NodeProxy) iter2.next() ) );
                return values;
            }
            r = expr.eval( docs, set, node );
        }
        return r;
    }


    /**
     *  Gets the documentSet attribute of the PathExpr object
     *
     *@return    The documentSet value
     */
    public DocumentSet getDocumentSet() {
        return docs;
    }


    /**
     *  Gets the expression attribute of the PathExpr object
     *
     *@param  pos  Description of the Parameter
     *@return      The expression value
     */
    public Expression getExpression( int pos ) {
        return (Expression) steps.get( pos );
    }


    /**
     *  Gets the length attribute of the PathExpr object
     *
     *@return    The length value
     */
    public int getLength() {
        return steps.size();
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String pprint() {
        StringBuffer buf = new StringBuffer();
        buf.append( '(' );
        for ( Iterator iter = steps.iterator(); iter.hasNext();  ) {
            if ( buf.length() > 1 )
                buf.append( '/' );
            buf.append( ( (Expression) iter.next() ).pprint() );
        }
        buf.append( ')' );
        return buf.toString();
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public DocumentSet preselect() {
        return preselect( docs );
    }


    /**
     *  Description of the Method
     *
     *@param  in_docs  Description of the Parameter
     *@return          Description of the Return Value
     */
    public DocumentSet preselect( DocumentSet in_docs ) {
        DocumentSet docs = in_docs;
        if ( docs.getLength() == 0 )
            return docs;
        for ( Iterator iter = steps.iterator(); iter.hasNext();  )
            docs = ( (Expression) iter.next() ).preselect( docs );
        return docs;
    }
		
    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public int returnsType() {
        if ( steps.get( 0 ) != null )
            return ( (Expression) steps.get( 0 ) ).returnsType();
        return Constants.TYPE_NODELIST;
    }


    /**
     *  Sets the documentSet attribute of the PathExpr object
     *
     *@param  docs  The new documentSet value
     */
    public void setDocumentSet( DocumentSet docs ) {
        this.docs = docs;
    }


    /**
     *  Sets the firstExpression attribute of the PathExpr object
     *
     *@param  s  The new firstExpression value
     */
    public void setFirstExpression( Expression s ) {
        steps.addFirst( s );
    }
}

