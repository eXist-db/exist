/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001/2002 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
 *  http://exist.sourceforge.net
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.exist.xpath;

import java.util.Iterator;

import org.exist.EXistException;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;

/**
 *  xpath-library function: match-keywords(XPATH, arg1, arg2 ...)
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    7. Oktober 2002
 */
public class FunKeywordMatch extends Function {

    /**  Constructor for the FunKeywordMatch object */
    public FunKeywordMatch(BrokerPool pool) {
        super( pool, "match-keywords" );
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
        Expression path = getArgument( 0 );
        NodeSet nodes = (NodeSet) path.eval( docs, context, null ).getNodeList();

        String[] terms = new String[getArgumentCount() - 1];
        for ( int i = 1; i < getArgumentCount(); i++ )
            terms[i - 1] = getArgument( i ).eval( docs, context, node ).getStringValue();
        DBBroker broker = null;
        NodeSet[][] hits = new NodeSet[terms.length][];
        try {
            broker = pool.get();
            for ( int j = 0; j < terms.length; j++ ) {
                String t[] = {terms[j]};
                hits[j] = broker.getNodesContaining( docs, t, DBBroker.MATCH_REGEXP );
            }
        } catch ( EXistException e ) {
            e.printStackTrace();
        } finally {
            pool.release( broker );
        }
        long pid;
        NodeProxy current;
        NodeProxy parent;
        NodeSet temp;
        long start = System.currentTimeMillis();
        for ( int j = 0; j < hits.length; j++ ) {
            temp = new ArraySet( 100 );
            for ( int k = 0; k < hits[j].length; k++ ) {
                if ( hits[j][k] == null )
                    continue;
                for ( Iterator i = hits[j][k].iterator(); i.hasNext();  ) {
                    current = (NodeProxy) i.next();
                    parent = nodes.parentWithChild( current, false, true );
                    if ( parent != null && ( !temp.contains( current.doc, parent.gid ) ) )
                        temp.add( parent );
                }
            }
            hits[j][0] = temp;
        }

        // merge results
        NodeSet t0 = null;

        // merge results
        NodeSet t1;
        for ( int j = 0; j < hits.length; j++ ) {
            t1 = hits[j][0];
            /*
             *  for(int k = 1; k < hits[j].length; k++)
             *  t1 = t1.union(hits[j][k]);
             */
            if ( t0 == null )
                t0 = t1;
            else
                t0 = ( getOperatorType() == Constants.FULLTEXT_AND ) ?
                    t0.intersection( t1 ) : t0.union( t1 );
        }
        if ( t0 == null )
            t0 = new ArraySet( 1 );
        return new ValueNodeSet( t0 );
    }


    /**
     *  Gets the operatorType attribute of the FunKeywordMatch object
     *
     *@return    The operatorType value
     */
    protected int getOperatorType() {
        return Constants.FULLTEXT_AND;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String pprint() {
        StringBuffer buf = new StringBuffer();
        buf.append( "match-keywords(" );
        buf.append( getArgument( 0 ).pprint() );
        for ( int i = 1; i < getArgumentCount(); i++ ) {
            buf.append( ", " );
            buf.append( getArgument( i ).pprint() );
        }
        buf.append( ')' );
        return buf.toString();
    }


    /**
     *  Description of the Method
     *
     *@param  in_docs  Description of the Parameter
     *@return          Description of the Return Value
     */
    public DocumentSet preselect( DocumentSet in_docs ) {
        return in_docs;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public int returnsType() {
        return Constants.TYPE_NODELIST;
    }
}

