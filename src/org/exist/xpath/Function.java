
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
 */
package org.exist.xpath;

import java.lang.reflect.Constructor;
import java.util.Iterator;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.BrokerPool;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 *@created    7. Oktober 2002
 */
public abstract class Function extends PathExpr {

    protected String name;


    /**
     *  Constructor for the Function object
     *
     *@param  name  Description of the Parameter
     */
    public Function( String name ) {
        super();
        this.name = name;
    }


    /**  Constructor for the Function object */
    public Function() {
        super();
    }


    /**
     *  Description of the Method
     *
     *@param  name    Description of the Parameter
     *@return         Description of the Return Value
     */
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


    /**
     *  Adds a feature to the Argument attribute of the Function object
     *
     *@param  expr  The feature to be added to the Argument attribute
     */
    public void addArgument( Expression expr ) {
        if ( expr == null )
            return;
        steps.add( expr );
    }


    /**
     *  Description of the Method
     *
     *@param  docs     Description of the Parameter
     *@param  context  Description of the Parameter
     *@param  node     Description of the Parameter
     *@return          Description of the Return Value
     */
    public abstract Value eval( StaticContext context, DocumentSet docs, NodeSet contextSet,
    	NodeProxy contextNode) throws XPathException;


    /**
     *  Gets the argument attribute of the Function object
     *
     *@param  pos  Description of the Parameter
     *@return      The argument value
     */
    public Expression getArgument( int pos ) {
        return getExpression( pos );
    }


    /**
     *  Gets the argumentCount attribute of the Function object
     *
     *@return    The argumentCount value
     */
    public int getArgumentCount() {
        return steps.size();
    }


    /**
     *  Gets the name attribute of the Function object
     *
     *@return    The name value
     */
    public String getName() {
        return name;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
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
}

