/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2011 The eXist Project
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

import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.config.annotation.ConfigurationFieldClassMask;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("instance")
public class ConfigurableObject implements Configurable {
	
	@ConfigurationFieldAsAttribute("valueString")
	protected String some = "default";

	@ConfigurationFieldAsElement("valueInteger")
	protected Integer someInteger = 7;

	@ConfigurationFieldAsAttribute("valueInt")
	protected int simpleInteger = 5;

	@ConfigurationFieldAsAttribute("value")
	protected int defaultInteger = 3;

	@ConfigurationFieldAsAttribute("valueboolean")
	protected boolean someboolean = false;

	@ConfigurationFieldAsAttribute("valueBoolean")
	protected Boolean someBoolean = true;

	@ConfigurationFieldAsElement("spice")
	@ConfigurationFieldClassMask("org.exist.config.Spice")
	protected List<Spice> spices = null;

	private Configuration configuration;
	
	public ConfigurableObject(Configuration config) {
		configuration = Configurator.configure(this, config);
	}
	
	public List<Spice> getSpices() {
		return spices;
	}
	
	public void addSpice(String name) {
		spices.add(new Spice(name));
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
