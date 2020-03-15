/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.ManagedLocks;
import org.exist.dom.persistent.DocumentSet;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.util.LockException;
import org.exist.xquery.*;
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
    
    
    @Override
    public Cardinality getCardinality() {
        return getArgument(1).getCardinality();
    }
    
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Function#returnsType()
     */
    public int returnsType() {
        return getArgument(1).returnsType();
    }
}
