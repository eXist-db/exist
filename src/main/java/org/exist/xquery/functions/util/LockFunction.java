/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist-db.org
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
 * 
 *  $Id$
 */
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.ManagedLocks;
import org.exist.dom.persistent.DocumentSet;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.util.LockException;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;


public abstract class LockFunction extends Function {

	protected static final Logger logger = LogManager.getLogger(LockFunction.class);

	protected final boolean exclusive;
    
    protected LockFunction(XQueryContext context, FunctionSignature signature, boolean exclusive) {
        super(context, signature);
        this.exclusive = exclusive;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Function#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
    	
        final Sequence docsArg = getArgument(0).eval(contextSequence, contextItem);
        final DocumentSet docs = docsArg.getDocumentSet();
        try(final ManagedLocks<ManagedDocumentLock> managedLocks = docs.lock(context.getBroker(), exclusive)) {
            return getArgument(1).eval(contextSequence, contextItem);
        } catch (final LockException e) {
            throw new XPathException(this, "Could not lock document set", e);
        }
    }
    
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Function#getCardinality()
     */
    public int getCardinality() {
        return getArgument(1).getCardinality();
    }
    
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Function#returnsType()
     */
    public int returnsType() {
        return getArgument(1).returnsType();
    }
}
