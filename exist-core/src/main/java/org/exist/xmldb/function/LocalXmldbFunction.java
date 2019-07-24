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
package org.exist.xmldb.function;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import com.evolvedbinary.j8fu.function.BiFunctionE;
import org.exist.xquery.XPathException;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import java.io.IOException;

/**
 * Specialisation of BiFunctionE which deals with
 * local XMLDB operations; Predominantly converts exceptions
 * from the database into XMLDBException types
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@FunctionalInterface
public interface LocalXmldbFunction<R> extends BiFunctionE<DBBroker, Txn, R, XMLDBException> {

    @Override
    default R apply(final DBBroker broker, final Txn transaction) throws XMLDBException {
        try {
            return applyXmldb(broker, transaction);
        } catch(final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch(final IOException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        } catch(final XPathException | EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    /**
     * Signature for lambda function which takes a broker and transaction
     *
     * @param broker The database broker for the XMLDB function
     * @param transaction The transaction for the XMLDB function
     *
     * @return the result of the function
     *
     * @throws XMLDBException if an error occurs whilst applying the function
     * @throws PermissionDeniedException if the user has insufficient permissions
     * @throws IOException if an IO error occurs
     * @throws XPathException if an error occurs whilst executing an XPath
     * @throws EXistException if any other error occurs
     */
    R applyXmldb(final DBBroker broker, final Txn transaction)
            throws XMLDBException, PermissionDeniedException, IOException, XPathException, EXistException;
}
