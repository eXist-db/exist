/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.security.internal;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Principal;
import org.exist.security.Subject;
import org.exist.security.realm.Realm;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("")
public abstract class AbstractPrincipal implements Principal {

	protected Realm realm;
	
	@ConfigurationFieldAsElement("name")
	protected final String name;
	
	@ConfigurationFieldAsAttribute("id")
	protected final int id;
	
	protected Configuration configuration = null;
	
	public AbstractPrincipal(AbstractRealm realm, Collection collection, int id, String name) throws ConfigurationException {
		this.realm = realm;
		this.id = id;
		this.name = name;
		
		if (collection != null) {
			BrokerPool database;
			try {
				database = BrokerPool.getInstance();
			} catch (EXistException e) {
				throw new ConfigurationException(e);
			} 
			
			DBBroker broker = null;
			try {
				broker = database.get(null);

				Configuration _config_ = Configurator.parse(this, broker, collection, XmldbURI.create(name+".xml"));
				configuration = Configurator.configure(this, _config_);
			} catch (EXistException e) {
				throw new ConfigurationException(e);
			} finally {
				database.release(broker);
			}
		}
	}
	
	protected void save() throws PermissionDeniedException, EXistException {
		if (configuration != null)
			configuration.save();
	}
	
	public final String getName() {
		return name;
	}

	public final int getId() {
		return id;
	}

	public final String getRealmId() {
		return realm.getId();
	}

	@Override
	public final boolean isConfigured() {
		return (configuration != null);
	}

	@Override
	public final Configuration getConfiguration() {
		return configuration;
	}

	public final void setCollection(DBBroker broker, Collection collection) throws ConfigurationException {
		if (collection != null) {
			Configuration _config_ = Configurator.parse(this, broker, collection, XmldbURI.create(name+".xml"));
			configuration = Configurator.configure(this, _config_);
		}
	}

}
