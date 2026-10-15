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

import org.exist.xquery.functions.util.UtilModule;
import org.exist.xquery.functions.xmldb.XMLDBModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A module registered only through the {@link ModuleFactory} service, with no entry in
 * conf.xml, has no configuration parameters, and {@code XQueryContext} passes it null.
 * Modules that read a parameter in their constructor must still be instantiable then;
 * otherwise the module is silently left out of every context.
 */
public class AbstractInternalModuleParametersTest {

    @Test
    public void utilModuleWithoutParameters() {
        final UtilModule module = assertDoesNotThrow(() -> new UtilModule(null),
                "UtilModule must be instantiable without configuration parameters");
        assertEquals(UtilModule.NAMESPACE_URI, module.getNamespaceURI());
    }

    @Test
    public void xmldbModuleWithoutParameters() {
        final XMLDBModule module = assertDoesNotThrow(() -> new XMLDBModule(null),
                "XMLDBModule must be instantiable without configuration parameters");
        assertEquals(XMLDBModule.NAMESPACE_URI, module.getNamespaceURI());
    }
}
