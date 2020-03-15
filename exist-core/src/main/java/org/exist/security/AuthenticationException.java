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
package org.exist.security;

/**
 * Authentication process error.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class AuthenticationException extends Exception {

	private static final long serialVersionUID = 8966739840703820248L;
	
	public static final int UNNOWN_EXCEPTION = -1; 

	public static final int ACCOUNT_NOT_FOUND = 0; 
	public static final int WRONG_PASSWORD = 1; 
	public static final int ACCOUNT_LOCKED = 2;
	
	public static final int SESSION_NOT_FOUND = 3;

	private int type;

	public AuthenticationException(int type, String message) {
		super(message);
		
		this.type = type;
	}

	public AuthenticationException(int type, String message, Throwable cause) {
		super(message, cause);

		this.type = type;
	}
	
	public AuthenticationException(int type, Throwable cause) {
		super(cause);

		this.type = type;
	}

	public int getType() {
		return type;
	}
}
