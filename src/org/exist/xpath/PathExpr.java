
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
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.Type;
import org.exist.xpath.value.ValueSequence;

public class PathExpr extends AbstractExpression {
	
    protected static Logger LOG = Logger.getLogger( PathExpr.class );
    protected DocumentSet docs = new DocumentSet();
    protected boolean keepVirtual = false;
    protected LinkedList steps = new LinkedList();
	protected boolean inPredicate = false;
	
    public PathExpr() {
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

    public Sequence eval( StaticContext context, DocumentSet docs, 
    	Sequence contextSequence, Item contextItem) throws XPathException {
        if ( docs.getLength() == 0 )
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
            if ( expr.returnsType() != Type.NODE ) {
				if(r.getLength() == 0)
                    r = expr.eval( context, docs, null, null );
                else {
                	values = null;
                	if(r.getLength() > 1)
                		values = new ValueSequence();
                	for ( SequenceIterator iterInner = r.iterate(); iterInner.hasNext(); ) {
                		current = iterInner.nextItem();
                		if(values == null)
                			values = expr.eval(context, docs, r, current);
                		else
                			values.addAll( expr.eval(context, docs, r, current) );
                	}
	                r = values;
            	}
            } else
            	r = expr.eval( context, docs, r );
        }
        return r;
    }

    public DocumentSet getDocumentSet() {
        return docs;
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
        for ( Iterator iter = steps.iterator(); iter.hasNext();  ) {
            if ( buf.length() > 1 )
                buf.append( '/' );
            buf.append( ( (Expression) iter.next() ).pprint() );
        }
        buf.append( ')' );
        return buf.toString();
    }

    public DocumentSet preselect(StaticContext context) throws XPathException {
        return preselect( docs, context );
    }

    public DocumentSet preselect( DocumentSet in_docs, StaticContext context) throws XPathException {
        DocumentSet docs = in_docs;
        for ( Iterator iter = steps.iterator(); iter.hasNext();  )
            docs = ( (Expression) iter.next() ).preselect( docs, context );
        return docs;
    }
		
    public int returnsType() {
    	if( steps.size() == 0 )
    		return Type.NODE;
    	return ((Expression) steps.getLast()).returnsType();
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
        this.docs = docs;
    }

    public void setFirstExpression( Expression s ) {
        steps.addFirst( s );
    }
    
    public String getLiteralValue() {
    	if(steps.size() == 0)
    		return "";
    	Expression next = (Expression)steps.get(0);
    	if(next instanceof Literal)
    		return ((Literal)next).getLiteral();
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

}