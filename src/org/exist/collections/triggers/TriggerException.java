/*
 *  TriggerException.java - eXist Open Source Native XML Database
 *  Copyright (C) 2003 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
 *  http://exist.sourceforge.net
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
 * $Id$
 *
 */
package org.exist.collections.triggers;

import org.xml.sax.SAXException;

/**
 * @author wolf
 */
public class TriggerException extends SAXException {

	/**
	 * 
	 */
	public TriggerException() {
		super();
	}

	/**
	 * @param message
	 */
	public TriggerException(String message) {
		super(message);
	}

	/**
	 * @param cause
	 */
	public TriggerException(Exception cause) {
		super(cause);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public TriggerException(String message, Exception cause) {
		super(message, cause);
	}

}
