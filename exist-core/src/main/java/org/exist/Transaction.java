/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist;

import org.exist.storage.txn.TransactionException;

/**
 * Represents an atomic Transaction on the database
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public interface Transaction extends AutoCloseable {
    
    /**
     * Performs an atomic commit of the transaction
     *
     * @throws org.exist.storage.txn.TransactionException if an error occurred
     *   during writing any part of the transaction
     */
    void commit() throws TransactionException;

    /**
     * Performs an atomic abort of the transaction
     */
    void abort();

    /**
     * Closes the transaction
     *
     * If the transaction has not been committed then
     * it will be auto-aborted.
     */
    @Override
    void close();
}
