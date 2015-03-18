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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;
import org.exist.util.ReadOnlyException;

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

    private final static Logger LOG = LogManager.getLogger(DOMTransaction.class);

    private Object ownerObject;
    private DOMFile file;
    private DocumentImpl document = null;
    private int mode;

    /**
     * @deprecated : use other constructors
     */
    public DOMTransaction(Object owner, DOMFile file) {
        this.ownerObject = owner;
        this.file = file;
        this.mode = Lock.READ_LOCK;
    }

    /**
     * Creates a new <code>DOMTransaction</code> instance.
     *
     * @param owner an <code>Object</code> value
     * @param file a <code>DOMFile</code> value
     * @param mode an <code>int</code> value
     */
    public DOMTransaction(Object owner, DOMFile file, int mode) {
        this(owner, file);
        this.mode = mode;
    }

    /**
     * Creates a new <code>DOMTransaction</code> instance.
     *
     * @param owner an <code>Object</code> value
     * @param file a <code>DOMFile</code> value
     * @param mode an <code>int</code> value
     * @param doc a <code>DocumentImpl</code> value
     */
    public DOMTransaction(Object owner, DOMFile file, int mode, DocumentImpl doc) {
        this(owner, file, mode);
        this.document = doc;
    }

    /**
     * The method <code>start</code>
     *
     * @return an <code>Object</code> value
     * @exception ReadOnlyException if an error occurs
     */
    public abstract Object start() throws ReadOnlyException;

    /**
     * The method <code>run</code>
     *
     * @return an <code>Object</code> value
     */
    public Object run() {
        final Lock lock = file.getLock();
        try {
            // try to acquire a lock on the file
            try {
                lock.acquire( mode );
            } catch( final LockException e ) {
                LOG.error("Failed to acquire read lock on " + file.getFile().getName(), e);
                return null;
            }
            file.setOwnerObject(ownerObject);
            file.setCurrentDocument(document);
            return start();
        } catch(final ReadOnlyException e) {
            LOG.error(e.getMessage(), e);
        } finally {
            lock.release(mode);
        }
        return null;
    }
}
