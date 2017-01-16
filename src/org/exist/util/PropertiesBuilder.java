/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2016 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.util;

import java.util.Properties;

/**
 * Simple fluent builder pattern for {@link Properties}
 */
public class PropertiesBuilder {

    private final Properties properties;

    private PropertiesBuilder(final Properties properties) {
        this.properties = properties;
    }

    public static PropertiesBuilder propertiesBuilder() {
        return new PropertiesBuilder(new Properties());
    }

    public PropertiesBuilder set(final String key, final String value) {
        properties.setProperty(key, value);
        return this;
    }

    public PropertiesBuilder put(final String key, final Object value) {
        properties.put(key, value);
        return this;
    }

    public Properties build() {
        return properties;
    }
}
