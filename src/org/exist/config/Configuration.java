/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.exist.dom.ElementAtExist;
import org.exist.security.PermissionDeniedException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface Configuration {
	
	public String NS = "http://exist-db.org/Configuration";
	
	public String ID = "id";
	
	public Configuration getConfiguration(String name);
	
	public List<Configuration> getConfigurations(String name);

	public Set<String> getProperties();
	
	public boolean hasProperty(String name);

	public String getProperty(String property);

	public Map<String, String> getPropertyMap(String property);

	public Integer getPropertyInteger(String property);
	public Long getPropertyLong(String property);
	public Boolean getPropertyBoolean(String property);
	public Class<?> getPropertyClass(String propertySecurityClass);

	public void setProperty(String property, String value);
	public void setProperty(String property, Integer value);

	public Object putObject(String name, Object object);
	public Object getObject(String name);

	public String getName();

	public String getValue();

	public ElementAtExist getElement();

	public void checkForUpdates(ElementAtExist document);

	public void save() throws PermissionDeniedException;

	public boolean equals(Object obj, String uniqField);
}