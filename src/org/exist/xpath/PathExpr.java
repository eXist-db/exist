
/*
 *  eXist Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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

public class PathExpr extends AbstractExpression {
	
    protected static Logger LOG = Logger.getLogger( PathExpr.class );
    protected DocumentSet docs = new DocumentSet();
    protected boolean keepVirtual = false;
    protected BrokerPool pool = null;
    protected LinkedList steps = new LinkedList();
	protected boolean inPredicate = false;
	
    public PathExpr(BrokerPool pool) {
		this.pool = pool;
    }

    public void add( Expression s ) {
        steps.add( s );
    }

    public void add( PathExpr path ) {
    	Expression expr;
        for ( Iterator i = path.steps.iterator(); i.hasNext();  ) {
			expr = (Expression) i.next();
            add( expr );
        }
    }

    public void addDocument( DocumentImpl doc ) {
        docs.add( doc );
    }

    public void addPath( PathExpr path ) {
        steps.add( path );
    }

    public void addPredicate( Predicate pred ) {
        Expression e = (Expression) steps.getLast();
        if ( e instanceof Step )
            ( (Step) e ).addPredicate( pred );
    }

    public Value eval( StaticContext context, DocumentSet docs, 
    	NodeSet contextSet, NodeProxy contextNode) {
        if ( docs.getLength() == 0 )
            return new ValueNodeSet( new ArraySet( 1 ) );
        Value r;
        if ( contextSet != null )
            r = new ValueNodeSet( contextSet );
        else
            r = new ValueNodeSet( new ArraySet( 1 ) );
        
        NodeSet set;
		NodeProxy current;
        Expression expr;
        ValueSet values;
        for ( Iterator iter = steps.iterator(); iter.hasNext();  ) {
            set = (NodeSet) r.getNodeList();
            expr = (Expression) iter.next();
            if ( expr.returnsType() != Constants.TYPE_NODELIST ) {
                if ( expr instanceof Literal || expr instanceof IntNumber )
                    return expr.eval( context, docs, set, null );
                values = new ValueSet();
                for ( Iterator iter2 = set.iterator(); iter2.hasNext();  ) {
                	current = (NodeProxy)iter2.next();
                    values.add( expr.eval( context, docs, set, current ) );
                }
                return values;
            }
            r = expr.eval( context, docs, set );
        }
        return r;
    }

    public DocumentSet getDocumentSet() {
        return docs;
    }

    public Expression getExpression( int pos ) {
        return (Expression) steps.get( pos );
    }

    public int getLength() {
        return steps.size();
    }

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

    public DocumentSet preselect() {
        return preselect( docs );
    }

    public DocumentSet preselect( DocumentSet in_docs ) {
        DocumentSet docs = in_docs;
        if ( docs.getLength() == 0 )
            return docs;
        for ( Iterator iter = steps.iterator(); iter.hasNext();  )
            docs = ( (Expression) iter.next() ).preselect( docs );
        return docs;
    }
		
    public int returnsType() {
        if ( steps.get( 0 ) != null )
            return ( (Expression) steps.get( 0 ) ).returnsType();
        return Constants.TYPE_NODELIST;
    }

    public void setDocumentSet( DocumentSet docs ) {
        this.docs = docs;
    }

    public void setFirstExpression( Expression s ) {
        steps.addFirst( s );
    }
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#setInPredicate(boolean)
	 */
	public void setInPredicate(boolean inPredicate) {
		this.inPredicate = inPredicate;
		if(steps.size() > 0)
			((Expression)steps.get(0)).setInPredicate(inPredicate);
	}

}