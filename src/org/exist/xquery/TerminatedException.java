/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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

package org.exist.xquery;

/**
 * @author wolf
 */
public class TerminatedException extends XPathException {

	private static final long serialVersionUID = 6055587317214098592L;

    public TerminatedException(String message) {
        super(message);
    }

    public TerminatedException(int line, int column, String message) {
        super(line, column, message);
    }

    public final static class TimeoutException extends TerminatedException {
        
		private static final long serialVersionUID = 1193758368058763151L;

		public TimeoutException(int line, int column, String message) {
            super(line, column, message);
        }
    }
    
    public final static class SizeLimitException extends TerminatedException {
        
		private static final long serialVersionUID = -697205233217384556L;

		public SizeLimitException(int line, int column, String message) {
            super(line, column, message);
        }
    }
}
