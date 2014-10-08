/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.security.internal;

import org.exist.EventListener;
import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.dom.QName;
import org.exist.security.Subject;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("authentication")
public class EventAuthentication implements EventListener<Subject>, Configurable {
	
	protected final static QName functionName = new QName("authentication", SMEvents.NAMESPACE_URI);

	//XXX: @ConfigurationFieldAsText
	private String script = "";
	
	private SMEvents em;
	
	private Configuration configuration = null;
	
	public EventAuthentication(SMEvents em, Configuration config) {
		this.em = em;
		
		configuration = Configurator.configure(this, config);
	}

	@Override
	public void onEvent(Subject subject) {
		em.runScript(subject, null, script, functionName, null);
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
