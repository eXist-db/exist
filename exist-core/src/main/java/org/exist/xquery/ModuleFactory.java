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
package org.exist.xquery;

/**
 * SPI interface for automatic XQuery module registration.
 *
 * <p>A JAR that bundles an XQuery module can self-register by:
 * <ol>
 *   <li>Providing an implementation of this interface (conventionally a static
 *       inner class named {@code Factory} on the module class).</li>
 *   <li>Listing the implementation's fully-qualified class name in
 *       {@code META-INF/services/org.exist.xquery.ModuleFactory}.</li>
 * </ol>
 *
 * <p>At startup, {@code Configuration} discovers all {@code ModuleFactory}
 * implementations on the classpath via {@link java.util.ServiceLoader} and
 * pre-populates the module registry before processing {@code conf.xml} entries.
 * A {@code conf.xml} entry always wins over an SPI-discovered entry for the
 * same namespace URI; {@code enabled="no"} suppresses an SPI-discovered module.
 */
public interface ModuleFactory {

    /**
     * The namespace URI that uniquely identifies the module.
     * Must match the value returned by {@link Module#getNamespaceURI()} for
     * the module class returned by {@link #getModuleClass()}.
     *
     * @return namespace URI
     */
    String getNamespaceURI();

    /**
     * The concrete {@link Module} implementation class to register.
     * The class must have a public constructor accepting
     * {@code Map<String, List<?>> parameters}.
     *
     * @return module implementation class
     */
    Class<? extends Module> getModuleClass();
}
