/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  $Id$
 */
package org.exist.http.servlets;

import java.util.Enumeration;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public interface SessionWrapper {
	
	public Object getAttribute(String arg0);
	
	public Enumeration<String> getAttributeNames();
	
	public long getCreationTime();
	
	public String getId();
	
	public long getLastAccessedTime();
	
	public int getMaxInactiveInterval();
	
	public void invalidate();
	
	public boolean isNew();
	
	public void removeAttribute(String arg0);
	
	public void setAttribute(String arg0, Object arg1);
	
	public void setMaxInactiveInterval(int arg0);
}