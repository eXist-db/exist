/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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

import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("spice")
public class Spice implements Configurable {
	
	@ConfigurationFieldAsAttribute("name")
	protected String name;
	
	protected ConfigurableObject parent = null;
	
	private Configuration configuration = null;
	
	public Spice(ConfigurableObject parent, Configuration _config_) {
		this.parent = parent;
		
		configuration = Configurator.configure(this, _config_);
	}

	public Spice(String name) {
		this.name = name;
	}

	@Override
	public boolean isConfigured() {
		return configuration != null;
	}

	@Override
	public Configuration getConfiguration() {
		return configuration;
	}
}
