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
package org.exist.security;

import org.exist.Database;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.config.annotation.ConfigurationFieldAsElement;
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

    private Realm realm;

    @ConfigurationFieldAsElement("name")
    protected final String name;

    @ConfigurationFieldAsAttribute("id")
    protected final int id;

    //XXX: this must be under org.exist.security.internal to make it protected
    public boolean removed = false;

    protected Configuration configuration = null;

    protected AbstractPrincipal(final DBBroker broker, final Realm realm, final Collection collection, final int id, final String name)
            throws ConfigurationException {
        this.realm = realm;
        this.id = id;
        this.name = name;
        if(collection != null) {
            try {
                final Configuration _config_ = Configurator.parse(this, broker, collection, XmldbURI.create(name + ".xml"));
                configuration = Configurator.configure(this, _config_);
            } catch (final EXistException e) {
                throw new ConfigurationException(e);
            }
        }
    }

    public AbstractPrincipal(final AbstractRealm realm, final Configuration _config_) throws ConfigurationException {
        this.realm = realm;

        configuration = Configurator.configure(this, _config_);
        
        if (configuration == null) {
            throw new ConfigurationException("Configuration can't be NULL ["+_config_+"]");
        }

        this.id = configuration.getPropertyInteger("id");
        this.name = configuration.getProperty("name");

    }

    @Override
    public void save() throws ConfigurationException, PermissionDeniedException {
        if (configuration != null) {
            configuration.save();
        }
    }

    @Override
    public void save(DBBroker broker) throws ConfigurationException, PermissionDeniedException {
        if (configuration != null) {
            configuration.save(broker);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public final int getId() {
        return id;
    }

    @Override
    public Realm getRealm() {
        return realm;
    }

    @Override
    public String getRealmId() {
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
            Configurator.unregister(configuration);
            final Configuration _config_ = Configurator.parse(this, broker, collection, XmldbURI.create(name + ".xml"));
            configuration = Configurator.configure(this, _config_);
        }
    }

    public final void setCollection(DBBroker broker, Collection collection, XmldbURI uri) throws ConfigurationException {
        if (collection != null) {
            Configurator.unregister(configuration);
            final Configuration _config_ = Configurator.parse(this, broker, collection, uri);
            configuration = Configurator.configure(this, _config_);
        }
    }

    protected Database getDatabase() {
        return realm.getDatabase();
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }
}
