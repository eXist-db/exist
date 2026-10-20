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

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.Configuration;
import org.exist.xquery.functions.util.UtilModule;
import org.exist.xquery.functions.xmldb.XMLDBModule;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Modules registered only through the {@link ModuleFactory} service, with no entry in conf.xml,
 * have no configuration parameters. Each new query context must still load them, rather than
 * log the failed instantiation and leave them out, which made their functions unknown (XPST0081).
 */
public class ModuleParametersContextTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String QUERY = """
            util:system-time() instance of xs:time and xmldb:collection-available('/db')
            """;

    @Test
    @SuppressWarnings("unchecked")
    void modulesWithoutParametersEntry() throws EXistException, PermissionDeniedException, XPathException {
        final Configuration configuration = existEmbeddedServer.getBrokerPool().getConfiguration();
        final Map<String, Map<String, List<?>>> moduleParameters =
                (Map<String, Map<String, List<?>>>) configuration.getProperty(XQueryContext.PROPERTY_MODULE_PARAMETERS);
        final Map<String, List<?>> utilParameters = moduleParameters.remove(UtilModule.NAMESPACE_URI);
        final Map<String, List<?>> xmldbParameters = moduleParameters.remove(XMLDBModule.NAMESPACE_URI);
        try {
            assertEquals("true", execute(QUERY + " (: no parameters entry :)"),
                    "util and xmldb functions must be available without a parameters entry");
        } finally {
            moduleParameters.put(UtilModule.NAMESPACE_URI, utilParameters);
            moduleParameters.put(XMLDBModule.NAMESPACE_URI, xmldbParameters);
        }
    }

    @Test
    void noModuleParametersAtAll() throws EXistException, PermissionDeniedException, XPathException {
        final Configuration configuration = existEmbeddedServer.getBrokerPool().getConfiguration();
        final Object moduleParameters = configuration.getProperty(XQueryContext.PROPERTY_MODULE_PARAMETERS);
        configuration.removeProperty(XQueryContext.PROPERTY_MODULE_PARAMETERS);
        try {
            assertEquals("true", execute(QUERY + " (: no module parameters :)"),
                    "util and xmldb functions must be available without any module parameters");
        } finally {
            configuration.setProperty(XQueryContext.PROPERTY_MODULE_PARAMETERS, moduleParameters);
        }
    }

    private static String execute(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Sequence result = pool.getXQueryService().execute(broker, query, null);
            return result.getStringValue();
        }
    }
}
