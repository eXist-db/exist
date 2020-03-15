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
package org.exist.security.realm.ldap;

import java.util.ArrayList;
import java.util.List;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.security.realm.TransformationContext;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;

/**
 * @author aretter
 */
@ConfigurationClass("transformation")
public class LDAPTransformationContext implements TransformationContext, Configurable {

    @ConfigurationFieldAsElement("add-group")
    //protected List<String> addGroup = new ArrayList<String>();
    protected String addGroup; //TODO convert to list

    private final Configuration configuration;

    public LDAPTransformationContext(final Configuration config) {
        this.configuration = Configurator.configure(this, config);
    }

    @Override
    public List<String> getAdditionalGroups() {
        final List<String> additionalGroups = new ArrayList<>();
        additionalGroups.add(addGroup);
        return additionalGroups;
    }

    @Override
    public boolean isConfigured() {
        return (configuration != null);
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }
}