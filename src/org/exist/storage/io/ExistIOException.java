/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *
 * $Id: ExistIOException.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.storage.io;


/**
 * A subclass of ExistIOException that adds the constructors for specifying
 * a cause to the ExistIOException class (missing in ExistIOException until Java 6).
 *
 * @author Chris Offerman
 */
public class ExistIOException extends java.io.IOException {
    
    /**
     * Constructs an <code>ExistIOException</code> with <code>null</code>
     * as its error detail message.  No underlying cause is set;
     * <code>getCause</code> will return <code>null</code>.
     */
    public ExistIOException() {
        super();
    }
    
    /**
     * Constructs an <code>ExistIOException</code> with a given message
     * <code>String</code>.  No underlying cause is set;
     * <code>getCause</code> will return <code>null</code>.
     * 
     * 
     * @param message the error message.
     * @see #getMessage
     */
    public ExistIOException(String message) {
        super(message);
    }
    
    /**
     * Constructs an <code>ExistIOException</code> with <code>null</code>
     * as its error detail message and a <code>Throwable</code> that was its
     * underlying cause.
     * 
     * 
     * @param cause the <code>Throwable</code> (<code>Error</code> or
     * <code>Exception</code>) that caused this exception to occur.
     * @see #getCause
     */
    public ExistIOException(Throwable cause) {
        super();
        initCause(cause);
    }
    
    /**
     * Constructs an <code>ExistIOException</code> with a given message
     * <code>String</code> and a <code>Throwable</code> that was its
     * underlying cause.
     * 
     * 
     * @param message the error message.
     * @param cause the <code>Throwable</code> (<code>Error</code> or
     * <code>Exception</code>) that caused this exception to occur.
     * @see #getCause
     * @see #getMessage
     */
    public ExistIOException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
