/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.util.sanity;

/**
 * Exception indicating a failed assertion. Used by
 * class {@link org.exist.util.sanity.SanityCheck} to print
 * a stack trace.
 * 
 * @author wolf
 */
public class AssertFailure extends RuntimeException {

	private static final long serialVersionUID = -4753385398634599386L;

    public AssertFailure() {
        super();
    }

    /**
     * @param message the error message
     */
    public AssertFailure(String message) {
        super(message);
    }

    /**
     * @param message the error message
     * @param cause the cause of the error
     */
    public AssertFailure(String message, Throwable cause) {
        super(message, cause);
    }

}
