
/*
 *  eXist Open Source Native XML Database
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
 *  You should have received a copy of the GNU Library General Public
 *  License along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id:
 */
package org.exist.xpath;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeIDSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.VirtualNodeSet;
import org.exist.storage.BrokerPool;

/**
 *  Handles predicate expressions.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    22. September 2002
 */
public class Predicate extends PathExpr {

    protected static Logger LOG = Logger.getLogger( Predicate.class );

    public Predicate(BrokerPool pool) {
        super(pool);
    }

    public Value eval( DocumentSet docs, NodeSet context, NodeProxy node ) {
    	long start = System.currentTimeMillis();
        ArraySet result = new ArraySet( 100 );
        //NodeIDSet result = new NodeIDSet();
        Expression first = getExpression( 0 );
        if ( first == null )
            return new ValueNodeSet( context );
        switch ( first.returnsType() ) {
            case Constants.TYPE_NODELIST:
            {
                NodeSet nodes =
                    (NodeSet) super.eval( docs, context, null ).getNodeList();
                start = System.currentTimeMillis();
                NodeProxy l;
                NodeProxy parent;
                long pid;
                for ( Iterator i = nodes.iterator(); i.hasNext();  ) {
                    l = (NodeProxy) i.next();
                    parent =
                        context.parentWithChild( l, false, true );
                    if ( parent != null) {
                    	if(result.contains(parent.doc, parent.gid))
                    		parent.addMatches(l.matches);
                    	else {
                    		parent.addMatches(l.matches);
                    		result.add( parent );
                        }
                    }
                }
                break;
            }
            case Constants.TYPE_BOOL:
            case Constants.TYPE_STRING:
            {
                //string has no special meaning

                NodeProxy p;
                NodeSet set;
                DocumentSet dset;
                Value v;
                for ( Iterator i = context.iterator(); i.hasNext();  ) {
                    p = (NodeProxy) i.next();
                    set = new ArraySet( 1 );
                    set.add( p );
                    dset = new DocumentSet();
                    dset.add( p.doc );
                    v = first.eval( dset, set, p );
                    if ( v.getBooleanValue() )
                        result.add( p );
                }
                break;
            }
            case Constants.TYPE_NUM:
            {
                NodeProxy p;
                NodeProxy n;
                NodeSet set;
                int level;
                int count;
                double pos;
                long pid;
                long last_pid = 0;
                long f_gid;
                long e_gid;
                DocumentImpl doc;
                DocumentImpl last_doc = null;
                // evaluate predicate expression for each context node
                for ( Iterator i = context.iterator(); i.hasNext();  ) {
                    p = (NodeProxy) i.next();
                    set = new ArraySet( 1 );
                    set.add( p );
                    pos = first.eval( docs, context, p ).getNumericValue();
                    doc = (DocumentImpl) p.getDoc();
                    level = doc.getTreeLevel( p.getGID() );
                    pid = ( p.getGID() - doc.getLevelStartPoint( level ) ) /
                        doc.getTreeLevelOrder( level )
                         + doc.getLevelStartPoint( level - 1 );
                    if ( pid == last_pid &&
                        last_doc != null &&
                        doc.getDocId() == last_doc.getDocId() )
                        continue;
                    last_pid = pid;
                    last_doc = doc;
                    f_gid = ( pid - doc.getLevelStartPoint( level - 1 ) ) *
                        doc.getTreeLevelOrder( level ) +
                        doc.getLevelStartPoint( level );
                    e_gid = f_gid + doc.getTreeLevelOrder( level );

                    count = 1;
                    set = context.getRange( doc, f_gid, e_gid );
                    for ( Iterator j = set.iterator(); j.hasNext(); count++ ) {
                        n = (NodeProxy) j.next();
                        if ( count == pos ) {
                            result.add( n );
                            break;
                        }
                    }
                }
            }
        }
        LOG.debug("predicate expression found " + result.getLength() + " in " + 
        	(System.currentTimeMillis() - start) + "ms.");
        return new ValueNodeSet( result );
    }

    public DocumentSet preselect( DocumentSet in_docs ) {
        DocumentSet docs = in_docs;
        for ( Iterator iter = steps.iterator(); iter.hasNext();  )
            docs = ( (Expression) iter.next() ).preselect( docs );
        return docs;
    }
}

