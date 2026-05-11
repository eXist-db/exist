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
package org.exist.xquery;

import org.exist.xquery.ErrorCodes.ErrorCode;

import java.io.Serial;

public class StaticXQueryException extends XPathException
{
    @Serial
    private static final long serialVersionUID = -8229758099980343418L;

    /**
     * Static-error exceptions default to XPST0003 (static syntax error) per
     * the W3C XQuery 3.1 spec, rather than the generic {@link ErrorCodes#ERROR}
     * inherited from {@link XPathException}. If a more specific error code was
     * propagated from the cause (via {@link XPathErrorProvider}), it is preserved.
     */
    @Override
    public ErrorCode getErrorCode() {
        final ErrorCode code = super.getErrorCode();
        return code == ErrorCodes.ERROR ? ErrorCodes.XPST0003 : code;
    }

	public StaticXQueryException(String message) {
        this(null, message);
    }

	public StaticXQueryException(final Expression expression, String message) {
		super(expression, message);
	}

	public StaticXQueryException(int line, int column, String message) {
		super(line, column, message);
	}
	
	public StaticXQueryException(Throwable cause) {
        this((Expression) null, cause);
    }
	
	public StaticXQueryException(final Expression expression, Throwable cause) {
		super(expression, cause);
	}

	public StaticXQueryException(String message, Throwable cause) {
        this(null, message, cause);
	}

	public StaticXQueryException(final Expression expression, String message, Throwable cause) {
		super(expression, message, cause);
	}

        //TODO add in ErrorCode and ErrorVal
	public StaticXQueryException(int line, int column, String message, Throwable cause) {
		super(line, column, message, cause);
	}
}
