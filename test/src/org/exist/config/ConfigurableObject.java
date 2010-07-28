/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2010 The eXist Project
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
import org.exist.config.annotation.ConfigurationField;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("instance")
public class ConfigurableObject implements Configurable {
	
	@ConfigurationField("valueString")
	protected String some = "default";

	@ConfigurationField("valueInteger")
	protected Integer someInteger = 7;

	@ConfigurationField("valueInt")
	protected int simpleInteger = 5;

	@ConfigurationField("value")
	protected int defaultInteger = 3;

	@ConfigurationField("valueboolean")
	protected boolean someboolean = false;

	@ConfigurationField("valueBoolean")
	protected Boolean someBoolean = true;

	private Configuration configuration;
	
	public ConfigurableObject(Configuration config) {
		configuration = Configurator.configure(this, config);
	}

	/* (non-Javadoc)
	 * @see org.exist.config.Configurable#isConfigured()
	 */
	@Override
	public boolean isConfigured() {
		return (configuration == null);
	}

	@Override
	public Configuration getConfiguration() {
		return configuration;
	}

}
