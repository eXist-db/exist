/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.collections;

/**
 * @author wolf
 */
public class CollectionConfigurationException extends Exception {

	private static final long serialVersionUID = 2646147244407222955L;

	/**
	 * 
	 */
	public CollectionConfigurationException() {
		super();
	}

	/**
	 * @param message of the exception
	 */
	public CollectionConfigurationException(String message) {
		super(message);
	}

	/**
	 * @param cause of the exception
	 */
	public CollectionConfigurationException(Throwable cause) {
		super(cause);
	}

	/**
	 * @param message of the exception
	 * @param cause of the exception
	 */
	public CollectionConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}

}
