/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.config.mapping;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.config.annotation.NewClass;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("instance")
public class ConfigurableObject implements Configurable {
	
	@ConfigurationFieldAsElement("mappedConfig")
	@NewClass(
		name = "org.exist.config.mapping.MappedClass",
		mapper = "org/exist/config/mapping/Mapping.xml")
	protected MappedClass subclasses;

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