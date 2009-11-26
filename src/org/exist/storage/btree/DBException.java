package org.exist.storage.btree;

/*
 * dbXML License, Version 1.0
 *
 *
 * Copyright (c) 1999-2001 The dbXML Group, L.L.C.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by
 *        The dbXML Group (http://www.dbxml.com/)."
 *    Alternately, this acknowledgment may appear in the software
 *    itself, if and wherever such third-party acknowledgments normally
 *    appear.
 *
 * 4. The names "dbXML" and "The dbXML Group" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    info@dbxml.com.
 *
 * 5. Products derived from this software may not be called "dbXML",
 *    nor may "dbXML" appear in their name, without prior written
 *    permission of The dbXML Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE DBXML GROUP OR ITS CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * $Id$
 */

/**
 * A DBException is thrown by the database if an exception occurs in the
 * managing (creating, dropping) database objects such as Collections, Indexes,
 * and XMLObjects.
 */

public class DBException extends Exception {
	
	private static final long serialVersionUID = 952318931514282901L;
	
	public int faultCode;

	public DBException() {
		this(0, "");
	}

	public DBException(int faultCode) {
		this(faultCode, "");
	}

	public DBException(String message) {
		this(0, message);
	}

	public DBException(int faultCode, String message) {
		super(message);
		this.faultCode = faultCode;
	}

}
