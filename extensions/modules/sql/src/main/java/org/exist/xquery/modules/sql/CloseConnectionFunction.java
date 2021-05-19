/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.xquery.modules.sql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

import java.sql.Connection;
import java.sql.SQLException;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.modules.sql.SQLModule.functionSignature;


/**
 * SQL Module Extension function for XQuery to explicitly close a connection.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class CloseConnectionFunction extends BasicFunction {

    private static final Logger LOGGER = LogManager.getLogger(GetConnectionFunction.class);

    private static final String FN_CLOSE_CONNECTION = "close-connection";
    public static final FunctionSignature FS_CLOSE_CONNECTION = functionSignature(
            FN_CLOSE_CONNECTION,
            "Closes a connection to a SQL Database, or if the connection was taken from a connection pool then it is returned to the pool",
            returns(Type.BOOLEAN, "true if the connection was closed, false if there was no such connection"),
            param("connection-handle", Type.LONG, "an xs:long representing the connection handle")
    );

    public CloseConnectionFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    /**
     * Evaluate the call to the xquery close-connection().
     *
     * @param args arguments from the close-connection() function call
     * @param contextSequence the Context Sequence to operate on (not used here internally!)
     *
     * @return An empty sequence
     *
     * @throws XPathException if an error occurs.
     */
    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final IntegerValue connectionHandle = (IntegerValue) args[0].itemAt(0);
        final long connectionUid = connectionHandle.toJavaObject(long.class);

        final Connection connection = SQLModule.removeConnection(context, connectionUid);
        if (connection == null) {
            return BooleanValue.FALSE;
        }

        try {
            if (connection.isClosed()) {
                LOGGER.warn("sql:close-connection() Cannot close connection with handle: {}, as it is already closed!", connectionUid);
                return BooleanValue.FALSE;
            }

            connection.close();
            return BooleanValue.TRUE;

        } catch (final SQLException e) {
            throw new XPathException(this, "Unable to close connection with handle: " + connectionUid + ". " + e.getMessage());
        }
    }
}
