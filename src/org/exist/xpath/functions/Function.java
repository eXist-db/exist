
/*
 *  eXist Native XML Database
 *  Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
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
package org.exist.xpath.functions;

import java.lang.reflect.Constructor;
import java.util.Iterator;

import org.exist.dom.DocumentSet;
import org.exist.storage.BrokerPool;
import org.exist.xpath.Dependency;
import org.exist.xpath.Expression;
import org.exist.xpath.PathExpr;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;

public abstract class Function extends PathExpr {

    protected String name;

    public Function( String name ) {
        super();
        this.name = name;
    }

    public Function() {
        super();
    }
    
    public static Function createFunction(String name) {
        try {
            if ( name == null )
                throw new RuntimeException( "insufficient arguments" );
            Class constructorArgs[] = new Class[0];
            Class fclass = Class.forName( name );
            if ( fclass == null )
                throw new RuntimeException( "class not found" );
            Constructor construct = fclass.getConstructor( constructorArgs );
            if ( construct == null )
                throw new RuntimeException( "constructor not found" );
            Object initArgs[] = new Object[0];
            Object obj = construct.newInstance( initArgs );
            if ( obj instanceof Function )
                return (Function) obj;
            else
                throw new RuntimeException( "function object does not implement interface function" );
        } catch ( Exception e ) {
            System.out.println( e );
            e.printStackTrace();
            throw new RuntimeException( "function " + name + " not found" );
        }
    }

    public void addArgument( Expression expr ) {
        if ( expr == null )
            return;
        steps.add( expr );
    }

    public abstract Sequence eval( StaticContext context, DocumentSet docs, Sequence contextSequence,
    	Item contextItem) throws XPathException;

    public Expression getArgument( int pos ) {
        return getExpression( pos );
    }

    public int getArgumentCount() {
        return steps.size();
    }

    public String getName() {
        return name;
    }

    public String pprint() {
        StringBuffer buf = new StringBuffer();
        buf.append( getName() );
        buf.append( '(' );
        for ( Iterator i = steps.iterator(); i.hasNext();  ) {
            Expression e = (Expression) i.next();
            buf.append( e.pprint() );
            buf.append( ',' );
        }
        buf.deleteCharAt( buf.length() - 1 );
        buf.append( ')' );
        return buf.toString();
    }
    
    /* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_ITEM | Dependency.CONTEXT_SET;
	}
}

