/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.xmlrpc.function;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import com.evolvedbinary.j8fu.function.BiFunction2E;
import org.xml.sax.SAXException;

import java.io.IOException;

/**
 * Specialisation of BiFunctionE which deals with
 * XML-RPC server operations; Predominantly converts exceptions
 * from the database into EXistException types
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@FunctionalInterface
public interface XmlRpcFunction<R> extends BiFunction2E<DBBroker, Txn, R, EXistException, PermissionDeniedException> {

    @Override
    default R apply(final DBBroker broker, final Txn transaction) throws EXistException, PermissionDeniedException {
        try {
            return applyXmlRpc(broker, transaction);
        } catch(final LockException | IOException | SAXException e) {
            throw new EXistException(e);
        }
    }

    /**
     * Signature for lambda function which takes a broker and transaction
     *
     * @param broker The database broker for the XML-RPC function
     * @param transaction The transaction for the XML-RPC function
     *
     * @return the result of the function
     *
     * @throws EXistException if an error occurs with the database
     * @throws PermissionDeniedException if the caller has insufficient priviledges
     * @throws IOException if an I/O error occurs
     * @throws SAXException if a SAX error occurs
     * @throws LockException if a lock error occurs
     */
    R applyXmlRpc(final DBBroker broker, final Txn transaction) throws EXistException, PermissionDeniedException, LockException, SAXException, IOException;
}
