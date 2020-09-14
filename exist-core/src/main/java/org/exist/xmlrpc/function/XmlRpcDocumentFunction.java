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
package org.exist.xmlrpc.function;

import org.exist.EXistException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import com.evolvedbinary.j8fu.function.TriFunction2E;
import org.xml.sax.SAXException;

import java.io.IOException;

/**
 * Specialisation of FunctionE which deals with
 * XML-RPC server operations; Predominantly converts exceptions
 * from the database into EXistException types
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@FunctionalInterface
public interface XmlRpcDocumentFunction<R> extends TriFunction2E<DocumentImpl, DBBroker, Txn, R, EXistException, PermissionDeniedException> {

    @Override
    default R apply(final DocumentImpl document, final DBBroker broker, final Txn transaction) throws EXistException, PermissionDeniedException {
        try {
            return applyXmlRpc(document, broker, transaction);
        } catch(final SAXException | IOException e) {
            throw new EXistException(e);
        }
    }

    /**
     * Signature for lambda function which takes a document
     *
     * @param document The database collection
     * @param broker the database broker
     * @param transaction the database transaction

     * @return the result of the function
     *
     * @throws EXistException if an error occurs with the database
     * @throws PermissionDeniedException if the caller has insufficient priviledges
     * @throws SAXException if a SAX error occurs
     * @throws IOException if an I/O error occurs
     */
    R applyXmlRpc(final DocumentImpl document, final DBBroker broker, final Txn transaction) throws EXistException, PermissionDeniedException, SAXException, IOException;
}
