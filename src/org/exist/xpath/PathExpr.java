
/*
 *  eXist Native XML Database
 *  Copyright (C) 2001-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
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

import java.util.Iterator;
import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.xmldb.CompiledExpression;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.Type;
import org.exist.xpath.value.ValueSequence;
import org.xmldb.api.base.XMLDBException;

public class PathExpr extends AbstractExpression implements CompiledExpression {
	
    protected static Logger LOG = Logger.getLogger( PathExpr.class );
    protected DocumentSet inputDocumentSet = new DocumentSet();
    protected boolean keepVirtual = false;
    protected LinkedList steps = new LinkedList();
	protected boolean inPredicate = false;
	
    public PathExpr(StaticContext context) {
    	super(context);
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
        inputDocumentSet.add( doc );
    }

    public void addPath( PathExpr path ) {
        steps.add( path );
    }

    public void addPredicate( Predicate pred ) {
        Expression e = (Expression) steps.getLast();
        if ( e instanceof Step )
            ( (Step) e ).addPredicate( pred );
    }

    public Sequence eval( Sequence contextSequence, Item contextItem) throws XPathException {
        if ( steps.size() == 0 )
            return Sequence.EMPTY_SEQUENCE;
        Sequence r;
        if ( contextSequence != null )
            r = contextSequence;
        else {
			r = Sequence.EMPTY_SEQUENCE;
        }
        NodeSet set;
		Item current;
        Expression expr;
        Sequence values;
        for ( Iterator iter = steps.iterator(); iter.hasNext();  ) {
            expr = (Expression) iter.next();
            if ((expr.getDependencies() & Dependency.CONTEXT_ITEM) != 0) {
            	//LOG.debug("single step mode: " + expr.pprint());
				if(r.getLength() == 0)
                    r = expr.eval( null, null );
                else {
                	values = null;
                	if(r.getLength() > 1)
                		values = new ValueSequence();
                	int pos = 0;
					context.setContextPosition(0);
                	for ( SequenceIterator iterInner = r.iterate(); iterInner.hasNext(); pos++ ) {
                		context.setContextPosition(pos);
                		current = iterInner.nextItem();
                		if(values == null)
                			values = expr.eval(r, current);
                		else
                			values.addAll( expr.eval(r, current) );
                	}
	                r = values;
            	}
            } else
            	r = expr.eval( r );
        }
        return r;
    }

	public StaticContext getContext() {
		return context;
	}
	
    public DocumentSet getDocumentSet() {
        return inputDocumentSet;
    }

    public Expression getExpression( int pos ) {
        return (Expression) steps.get( pos );
    }

	public Expression getLastExpression() {
		if(steps.size() == 0)
			return null;
		return (Expression) steps.getLast();
	}
	
    public int getLength() {
        return steps.size();
    }

    public String pprint() {
        StringBuffer buf = new StringBuffer();
        buf.append('(');
        Expression next;
        for ( Iterator iter = steps.iterator(); iter.hasNext();  ) {
        	next = (Expression) iter.next();
            if ( buf.length() > 1 && next instanceof Step)
                buf.append( '/' );
            buf.append( next.pprint() );
        }
        buf.append( ')' );
        return buf.toString();
    }

    public int returnsType() {
    	if( steps.size() == 0 )
    		return Type.NODE;
    	int rtype = ((Expression) steps.getLast()).returnsType();
    	return rtype;
    }

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		Expression next;
		int deps = 0;
		for(Iterator i = steps.iterator(); i.hasNext(); ) {
			next = (Expression)i.next();
			deps = deps | next.getDependencies();
		}
		return deps;
	}
	
    public void setDocumentSet( DocumentSet docs ) {
        this.inputDocumentSet = docs;
    }

    public void setFirstExpression( Expression s ) {
        steps.addFirst( s );
    }
    
    public void replaceLastExpression( Expression s ) {
    	if(steps.size() == 0)
    		return;
    	else {
    		steps.removeLast();
    		steps.addLast(s);
    	}
    }
    
    public String getLiteralValue() {
    	if(steps.size() == 0)
    		return "";
    	Expression next = (Expression)steps.get(0);
    	if(next instanceof LiteralValue)
    		try {
				return ((LiteralValue)next).getValue().getStringValue();
			} catch (XPathException e) {
			}
    	if(next instanceof PathExpr)
    		return ((PathExpr)next).getLiteralValue();
    	return "";
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#setInPredicate(boolean)
	 */
	public void setInPredicate(boolean inPredicate) {
		this.inPredicate = inPredicate;
		if(steps.size() > 0)
			((Expression)steps.get(0)).setInPredicate(inPredicate);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#setPrimaryAxis(int)
	 */
	public void setPrimaryAxis(int axis) {
		if(steps.size() > 0)
			((Expression)steps.get(0)).setPrimaryAxis(axis);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#resetState()
	 */
	public void resetState() {
		for(Iterator i = steps.iterator(); i.hasNext(); ) {
			((Expression)i.next()).resetState();
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.CompiledExpression#reset()
	 */
	public void reset() throws XMLDBException {
		resetState();
	}

}