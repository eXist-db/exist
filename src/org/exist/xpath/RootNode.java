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
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id:
 */
package org.exist.xpath;

import java.util.Iterator;

import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.BrokerPool;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    02 August 2002
 */
public class RootNode extends Step {

    /**  Constructor for the RootNode object */
    public RootNode(BrokerPool pool) {
        super( pool, Constants.SELF_AXIS );
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
        ArraySet result = new ArraySet( docs.getLength() );
        DocumentImpl doc;
        NodeProxy n;
		for(Iterator i = docs.iterator(); i.hasNext(); ) {
            doc = (DocumentImpl) i.next();
            n = new NodeProxy( doc, -1 );
            //n = new NodeProxy( doc, doc.getDocumentElementId() );
            result.add( n );
        }
        return new ValueNodeSet( result );
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String pprint() {
        return "/";
    }
}

