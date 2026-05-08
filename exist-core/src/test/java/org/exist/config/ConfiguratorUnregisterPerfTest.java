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
package org.exist.config;

import org.exist.storage.DBBroker;
import org.exist.xmldb.FullXmldbURI;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Test;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for GH-3557: Configurator.unregister must not call
 * Configuration.equals() on every entry in hotConfigs. Realm load calls
 * unregister once per principal, so an equals-driven scan turns startup
 * into O(N^2) and produces 12-minute startup with 50k users.
 */
public class ConfiguratorUnregisterPerfTest {

    private final List<FullXmldbURI> registeredKeys = new ArrayList<>();

    @After
    public void cleanup() {
        for (final FullXmldbURI key : registeredKeys) {
            Configurator.hotConfigs.remove(key);
        }
        registeredKeys.clear();
        EqualsCountingConfiguration.equalsCalls.set(0);
    }

    @Test
    public void unregisterDoesNotCallEqualsOnOtherEntries() {
        final int n = 200;
        final List<Configuration> configs = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            final Configuration cfg = new EqualsCountingConfiguration();
            final FullXmldbURI key = (FullXmldbURI) XmldbURI.create(
                    "xmldb:exist://", "/db/system/security/user-" + i + ".xml");
            Configurator.hotConfigs.put(key, cfg);
            registeredKeys.add(key);
            configs.add(cfg);
        }

        EqualsCountingConfiguration.equalsCalls.set(0);

        for (final Configuration cfg : configs) {
            Configurator.unregister(cfg);
        }

        // Reference equality lookup => zero equals() calls. Pre-fix the
        // ConcurrentHashMap.containsValue() precheck called equals() on every
        // surviving entry per call, totalling ~ N*(N-1)/2 = 19,900 for N=200.
        final long calls = EqualsCountingConfiguration.equalsCalls.get();
        assertTrue("unregister called Configuration.equals() " + calls
                        + " times for " + n + " principals; expected 0 (regression of GH-3557).",
                calls == 0);

        for (final Configuration cfg : configs) {
            assertNull(lookupKeyByValue(cfg));
        }
    }

    private static FullXmldbURI lookupKeyByValue(final Configuration cfg) {
        for (final Map.Entry<FullXmldbURI, Configuration> e : Configurator.hotConfigs.entrySet()) {
            if (e.getValue() == cfg) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * Stub Configuration whose equals() bumps a static counter. All other
     * methods throw because Configurator.unregister must never call them.
     */
    private static final class EqualsCountingConfiguration implements Configuration {

        static final AtomicLong equalsCalls = new AtomicLong();

        @Override
        public boolean equals(final Object obj) {
            equalsCalls.incrementAndGet();
            return this == obj;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        // -- unused -----------------------------------------------------------
        @Override public Configuration getConfiguration(final String name) { throw new UnsupportedOperationException(); }
        @Override public List<Configuration> getConfigurations(final String name) { throw new UnsupportedOperationException(); }
        @Override public Set<String> getProperties() { throw new UnsupportedOperationException(); }
        @Override public boolean hasProperty(final String name) { throw new UnsupportedOperationException(); }
        @Override public String getProperty(final String property) { throw new UnsupportedOperationException(); }
        @Override public Map<String, String> getPropertyMap(final String property) { throw new UnsupportedOperationException(); }
        @Override public Integer getPropertyInteger(final String property) { throw new UnsupportedOperationException(); }
        @Override public Long getPropertyLong(final String property) { throw new UnsupportedOperationException(); }
        @Override public Boolean getPropertyBoolean(final String property) { throw new UnsupportedOperationException(); }
        @Override public Object putObject(final String name, final Object object) { throw new UnsupportedOperationException(); }
        @Override public Object getObject(final String name) { throw new UnsupportedOperationException(); }
        @Override public String getName() { throw new UnsupportedOperationException(); }
        @Override public String getValue() { throw new UnsupportedOperationException(); }
        @Override public Element getElement() { throw new UnsupportedOperationException(); }
        @Override public void checkForUpdates(final Element document) { throw new UnsupportedOperationException(); }
        @Override public void save() { throw new UnsupportedOperationException(); }
        @Override public void save(final DBBroker broker) { throw new UnsupportedOperationException(); }
        @Override public boolean equals(final Object obj, final Optional<String> property) { throw new UnsupportedOperationException(); }
        @Override public void clearCache() { throw new UnsupportedOperationException(); }
    }
}
