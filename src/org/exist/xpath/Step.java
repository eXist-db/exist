/*
 *  eXist Open Source Native XML Database
 * 
 *  Copyright (C) 2001-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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

import java.util.ArrayList;
import java.util.Iterator;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.BrokerPool;

public abstract class Step extends AbstractExpression {

    protected int axis = -1;
    protected BrokerPool pool = null;
    protected ArrayList predicates = new ArrayList();
    protected NodeTest test;
	protected boolean inPredicate = false;
	
    public Step( BrokerPool pool, int axis ) {
        super();
        this.axis = axis;
        this.pool = pool;
    }

    public Step( BrokerPool pool, int axis, NodeTest test ) {
        this( pool, axis );
        this.test = test;
    }

    public void addPredicate( Expression expr ) {
        predicates.add( expr );
    }

    public abstract Value eval( StaticContext context, DocumentSet docs, NodeSet contextSet,
    	NodeProxy contextNode );

    public int getAxis() {
        return axis;
    }

    public String pprint() {
        StringBuffer buf = new StringBuffer();
        if ( axis > -1 )
            buf.append( Constants.AXISSPECIFIERS[axis] );
        buf.append( "::" );
        if ( test != null )
            buf.append( test.toString() );
        else
            buf.append( "*" );
        if ( predicates.size() > 0 )
            for ( Iterator i = predicates.iterator(); i.hasNext();  ) {
                buf.append( '[' );
                buf.append( ( (Predicate) i.next() ).pprint() );
                buf.append( ']' );
            }

        return buf.toString();
    }

    public DocumentSet preselect( DocumentSet in_docs ) {
        DocumentSet out_docs = in_docs;
        if ( predicates.size() > 0 )
            for ( Iterator i = predicates.iterator(); i.hasNext();  )
                out_docs = ( (Predicate) i.next() ).preselect( out_docs );

        return out_docs;
    }

    public int returnsType() {
        return Constants.TYPE_NODELIST;
    }

    public void setAxis( int axis ) {
        this.axis = axis;
    }

    public void setTest( NodeTest test ) {
        this.test = test;
    }

	public void setInPredicate(boolean inPredicate) {
		this.inPredicate = inPredicate;
	}

}

