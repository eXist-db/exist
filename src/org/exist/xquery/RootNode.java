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
 *  $Id$
 */
package org.exist.xquery;

import java.util.Iterator;

import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.util.LockException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Represents the document root node in an expression.
 * 
 * @author Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class RootNode extends Step {

    private Sequence cached = null;

    private DocumentSet cachedDocs = null;

    /** Constructor for the RootNode object */
    public RootNode(XQueryContext context) {
        super(context, Constants.SELF_AXIS);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        DocumentSet ds = context.getStaticallyKnownDocuments();
        if (ds == null || ds.getLength() == 0) return Sequence.EMPTY_SEQUENCE;
        
        try {
            // wait for pending updates
            ds.lock(false);
            
            if (cachedDocs != null && cachedDocs.equals(ds)) return cached;
	        NodeSet result = new ArraySet(ds.getLength());
	        DocumentImpl d;
	        for (Iterator i = ds.iterator(); i.hasNext();) {
	            d = (DocumentImpl) i.next();
	            if(d.getResourceType() == DocumentImpl.XML_FILE) // skip binary resources
	            	result.add(new NodeProxy(d, -1));
	        }
	        cached = result;
	        cachedDocs = ds;
	        return result;
        } catch (LockException e) {
            throw new XPathException(getASTNode(), "Failed to acquire lock on the context document set");
        } finally {
	        ds.unlock(false);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Step#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("/ROOT");
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.Step#returnsType()
     */
    public int returnsType() {
        return Type.NODE;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.Step#resetState()
     */
    public void resetState() {
        cachedDocs = null;
        cached = null;
    }
}