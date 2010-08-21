/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
package org.exist.config;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface ConfigurationEvent {

	public int ADD_PROPERTY = 1;
	public int CHANGE_PROPERTY = 2;
	public int REMOVE_PROPERTY = 3;
	
	public int ADD_CONFIGURATION = 11;
	public int CHANGE_CONFIGURATION = 12;
	public int REMOVE_CONFIGURATION = 13;

	public int LOAD = 21;
	public int SAVE = 22;
	public int RELOAD = 23;

	public int getType();
}
