/*
 *  eXist Open Source Native XML Database
 * 
 *  Copyright (C) 2001-06,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
package org.exist.xquery;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

public abstract class Step extends AbstractExpression {

	protected final static Logger LOG = Logger.getLogger(Step.class);
	
    protected int axis = Constants.UNKNOWN_AXIS;
    protected ArrayList predicates = new ArrayList();
    protected NodeTest test;
	protected boolean inPredicate = false;
	
	/**
	 * Holds the context id for the context of this expression.
	 */
	protected int contextId = Expression.NO_CONTEXT_ID;
	
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

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	//context.("t")
    	if (test != null && test.getName() != null && test.getName().getPrefix() != null &&
    			!test.getName().getPrefix().equals("") && context.inScopePrefixes !=  null && 
    			context.getURIForPrefix(test.getName().getPrefix()) == null)
    		throw new XPathException(getASTNode(), "XPST0081 : undeclared prefix '" + 
    				test.getName().getPrefix() + "'");
    	inPredicate = (contextInfo.getFlags() & IN_PREDICATE) > 0;
    	this.contextId = contextInfo.getContextId();
    	
    	if (predicates.size() > 0) {
	    	AnalyzeContextInfo newContext = new AnalyzeContextInfo(contextInfo);
	        newContext.setStaticType(this.axis == Constants.SELF_AXIS ? contextInfo.getStaticType() : Type.NODE);
	    	newContext.setParent(this);
	        for ( Iterator i = predicates.iterator(); i.hasNext();  ) {
	            ((Predicate) i.next()).analyze(newContext);
	        }
    	}
    }
    
    public abstract Sequence eval( Sequence contextSequence, Item contextItem ) throws XPathException;

    public int getAxis() {
        return axis;
    }

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#setPrimaryAxis(int)
	 */
	public void setPrimaryAxis(int axis) {
		this.axis = axis;
	}

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        if (axis != Constants.UNKNOWN_AXIS)
            dumper.display( Constants.AXISSPECIFIERS[axis] );
        dumper.display( "::" );
        if ( test != null )
        	//TODO : toString() or... dump ?
            dumper.display( test.toString() );
        else
            dumper.display( "node()" );
        if ( predicates.size() > 0 )
            for ( Iterator i = predicates.iterator(); i.hasNext();  ) {
                dumper.display( '[' );
                ( (Predicate) i.next() ).dump(dumper);
                dumper.display( ']' );
            }
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();
        if ( axis != Constants.UNKNOWN_AXIS)
        	result.append( Constants.AXISSPECIFIERS[axis] );
        result.append( "::" );
        if ( test != null )
        	result.append( test.toString() );
        else
        	result.append( "node()" );
        if ( predicates.size() > 0 )
            for ( Iterator i = predicates.iterator(); i.hasNext();  ) {
            	result.append( '[' );
            	result.append(( (Predicate) i.next() ).toString());
                result.append( ']' );
            }
        return result.toString();
    }    
    
    public int returnsType() {
        return Type.NODE;
    }
    
    public int getCardinality() {
   	return Cardinality.ZERO_OR_MORE;
   }

    public void setAxis( int axis ) {
        this.axis = axis;
    }

    public void setTest( NodeTest test ) {
        this.test = test;
    }

    public NodeTest getTest() {
        return test;
    }
    
    /* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState() {
		super.resetState();
		for (Iterator i = predicates.iterator(); i.hasNext();) {
			Predicate pred = (Predicate) i.next();
			pred.resetState();
		}
	}
}

