/*
 *  eXist Open Source Native XML Database
 * 
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

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    7. Oktober 2002
 */
public abstract class Step implements Expression {

    protected int axis = -1;
    protected BrokerPool pool = null;
    protected ArrayList predicates = new ArrayList();
    protected NodeTest test;


    /**
     *  Constructor for the Step object
     *
     *@param  axis  Description of the Parameter
     */
    public Step( BrokerPool pool, int axis ) {
        super();
        this.axis = axis;
        this.pool = pool;
    }


    /**
     *  Constructor for the Step object
     *
     *@param  axis  Description of the Parameter
     *@param  test  Description of the Parameter
     */
    public Step( BrokerPool pool, int axis, NodeTest test ) {
        this( pool, axis );
        this.test = test;
    }


    /**
     *  Adds a feature to the Predicate attribute of the Step object
     *
     *@param  expr  The feature to be added to the Predicate attribute
     */
    public void addPredicate( Expression expr ) {
        predicates.add( expr );
    }


    /**
     *  Description of the Method
     *
     *@param  docs     Description of the Parameter
     *@param  context  Description of the Parameter
     *@param  node     Description of the Parameter
     *@return          Description of the Return Value
     */
    public abstract Value eval( DocumentSet docs, NodeSet context, NodeProxy node );


    /**
     *  Gets the axis attribute of the Step object
     *
     *@return    The axis value
     */
    public int getAxis() {
        return axis;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String pprint() {
        StringBuffer buf = new StringBuffer();
        if ( axis > 0 )
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


    /**
     *  Description of the Method
     *
     *@param  in_docs  Description of the Parameter
     *@return          Description of the Return Value
     */
    public DocumentSet preselect( DocumentSet in_docs ) {
        DocumentSet out_docs = in_docs;
        if ( predicates.size() > 0 )
            for ( Iterator i = predicates.iterator(); i.hasNext();  )
                out_docs = ( (Predicate) i.next() ).preselect( out_docs );

        return out_docs;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public int returnsType() {
        return Constants.TYPE_NODELIST;
    }


    /**
     *  Sets the axis attribute of the Step object
     *
     *@param  axis  The new axis value
     */
    public void setAxis( int axis ) {
        this.axis = axis;
    }


    /**
     *  Sets the test attribute of the Step object
     *
     *@param  test  The new test value
     */
    public void setTest( NodeTest test ) {
        this.test = test;
    }

    /*
     *  protected final static boolean nodeHasParent(DocumentImpl doc, long gid,
     *  exist.NodeSet parents,
     *  boolean directParent) {
     *  return nodeHasParent(doc, gid, parents, directParent, false);
     *  }
     *  protected final static boolean nodeHasParent(DocumentImpl doc, long gid,
     *  exist.NodeSet parents,
     *  boolean directParent,
     *  boolean includeSelf) {
     *  if(gid < 2)
     *  return false;
     *  if(includeSelf && parents.contains(doc, gid))
     *  return true;
     *  int level = doc.getTreeLevel(gid);
     *  / calculate parent's gid
     *  long pid = (gid - doc.getLevelStartPoint(level)) /
     *  doc.getTreeLevelOrder(level)
     *  + doc.getLevelStartPoint(level - 1);
     *  /long pid = (gid - 2) / ((DocumentImpl)doc).getOrder() + 1;
     *  if(parents.contains(doc, pid))
     *  return true;
     *  else if(directParent)
     *  return false;
     *  else
     *  return nodeHasParent(doc, pid, parents, directParent);
     *  }
     *  protected final static
     *  long parentWithChild(DocumentImpl doc, long gid, exist.NodeSet parents,
     *  boolean directParent) {
     *  return parentWithChild(doc, gid, parents, directParent, false);
     *  }
     *  protected final static
     *  long parentWithChild(DocumentImpl doc, long gid, exist.NodeSet parents,
     *  boolean directParent, boolean includeSelf) {
     *  if(gid < 2)
     *  return -1;
     *  if(includeSelf && parents.contains(doc, gid))
     *  return gid;
     *  int level = doc.getTreeLevel(gid);
     *  / calculate parent's gid
     *  long pid = (gid - doc.getLevelStartPoint(level)) /
     *  doc.getTreeLevelOrder(level)
     *  + doc.getLevelStartPoint(level - 1);
     *  /long pid = (gid - 2) / ((DocumentImpl)doc).getOrder() + 1;
     *  if(parents.contains(doc, pid))
     *  return pid;
     *  else if(directParent)
     *  return -1;
     *  else
     *  return parentWithChild(doc, pid, parents, directParent);
     *  }
     */
}

