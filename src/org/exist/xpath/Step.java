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
 * 
 * $Id$
 */
package org.exist.xpath;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

public abstract class Step extends AbstractExpression {

	protected final static Logger LOG = Logger.getLogger(Step.class);
	
    protected int axis = -1;
    protected ArrayList predicates = new ArrayList();
    protected NodeTest test;
	protected boolean inPredicate = false;
	
    public Step( XQueryContext context, int axis ) {
        super(context);
        this.axis = axis;
    }

    public Step( XQueryContext context, int axis, NodeTest test ) {
        this( context, axis );
        this.test = test;
    }

    public void addPredicate( Expression expr ) {
        predicates.add( expr );
    }

    public abstract Sequence eval( Sequence contextSequence, Item contextItem ) throws XPathException;

    public int getAxis() {
        return axis;
    }

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#setPrimaryAxis(int)
	 */
	public void setPrimaryAxis(int axis) {
		this.axis = axis;
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

    public int returnsType() {
        return Type.NODE;
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

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#resetState()
	 */
	public void resetState() {
		for (Iterator i = predicates.iterator(); i.hasNext();) {
			Predicate pred = (Predicate) i.next();
			pred.resetState();
		}
	}
}

