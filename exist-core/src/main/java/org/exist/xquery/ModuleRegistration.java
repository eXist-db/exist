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
 * Provenance record for a built-in (Java) XQuery module, as discovered
 * during {@code Configuration} startup. Unlike the internal module class
 * map that actually drives module instantiation, this record is retained
 * for both active and {@code enabled="no"}-suppressed modules, so that
 * {@code system:get-registered-modules()} can report what is loaded and why.
 *
 * @param uri namespace URI of the module
 * @param className fully-qualified implementation class name
 * @param source how the module was registered: {@code "built-in"} (always
 *     present, e.g. {@code fn:}), {@code "spi"} (auto-discovered via
 *     {@link ModuleFactory}), or {@code "conf.xml"} (explicit {@code <module>}
 *     entry)
 * @param enabled whether the module is currently active ({@code false} if
 *     suppressed via {@code enabled="no"})
 */
public record ModuleRegistration(String uri, String className, String source, boolean enabled) {

    public static final String SOURCE_BUILT_IN = "built-in";
    public static final String SOURCE_SPI = "spi";
    public static final String SOURCE_CONF_XML = "conf.xml";
}
