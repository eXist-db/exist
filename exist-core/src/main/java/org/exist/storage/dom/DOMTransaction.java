/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist team
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.storage.dom;

import com.evolvedbinary.j8fu.function.SupplierE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.ManagedLock;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
import org.exist.util.ReadOnlyException;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * DOMTransaction controls access to the DOM file
 * 
 * This implements a wrapper around the code passed in
 * method start(). The class acquires a lock on the
 * file, enters the locked code block and calls start.
 * 
 * @author wolf
 *
 */
public abstract class DOMTransaction {

    private static final Logger LOG = LogManager.getLogger(DOMTransaction.class);

    private final Object ownerObject;
    private final DOMFile file;
    private final SupplierE<ManagedLock<ReentrantLock>, LockException> acquireFn;
    private final DocumentImpl document;

    /**
     * Creates a new <code>DOMTransaction</code> instance.
     *
     * @param owner an <code>Object</code> value
     * @param file a <code>DOMFile</code> value
     * @param acquireFn a <code>Supplier</code> value
     */
    public DOMTransaction(final Object owner, final DOMFile file, final SupplierE<ManagedLock<ReentrantLock>, LockException> acquireFn) {
        this(owner, file, acquireFn, null);
    }

    /**
     * Creates a new <code>DOMTransaction</code> instance.
     *
     * @param owner an <code>Object</code> value
     * @param file a <code>DOMFile</code> value
     * @param acquireFn a <code>Supplier</code> value
     * @param doc a <code>DocumentImpl</code> value
     */
    public DOMTransaction(final Object owner, final DOMFile file, final SupplierE<ManagedLock<ReentrantLock>, LockException> acquireFn, final DocumentImpl doc) {
        this.ownerObject = owner;
        this.file = file;
        this.acquireFn = acquireFn;
        this.document = doc;
    }

    /**
     * The method <code>start</code>
     *
     * @return an <code>Object</code> value
     * @throws ReadOnlyException if an error occurs
     */
    public abstract Object start() throws ReadOnlyException;

    /**
     * The method <code>run</code>
     *
     * @return an <code>Object</code> value
     */
    public Object run() {
        // try to acquire a lock on the file
        try(final ManagedLock<ReentrantLock> domFileLock = acquireFn.get()) {
            file.setOwnerObject(ownerObject);
            file.setCurrentDocument(document);
            return start();
        } catch(final LockException e) {
            LOG.error("Failed to acquire read lock on " + FileUtils.fileName(file.getFile()), e);
            return null;
        } catch(final ReadOnlyException e) {
            LOG.error(e.getMessage(), e);
        }
        return null;
    }
}
