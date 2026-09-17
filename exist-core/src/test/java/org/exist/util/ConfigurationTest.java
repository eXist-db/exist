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
package org.exist.util;

import org.exist.indexing.IndexManager;
import org.exist.xquery.ModuleRegistration;
import org.exist.xquery.XQueryContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class ConfigurationTest {

    private static Element elem(final String attrName, final String attrValue) throws Exception {
        final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        final Element e = doc.createElement("config");
        if (attrName != null) {
            e.setAttribute(attrName, attrValue == null ? "" : attrValue);
        }
        return e;
    }
    @Test
    void testConfigurationConstructors() throws Exception {
        assertThatNoException().isThrownBy(Configuration::new);
        assertThatNoException().isThrownBy(() -> new Configuration(null));
    }

    @Test
    void testConfigurationConstructorWithClasspathConf(@TempDir Path existHomeDir) throws Exception {
        assertThatNoException().isThrownBy(() -> new Configuration("conf.xml"));
        assertThatNoException().isThrownBy(() -> new Configuration("conf.xml", Optional.empty()));
        assertThatNoException().isThrownBy(() -> new Configuration("conf.xml", Optional.of(existHomeDir)));
    }

    @Test
    void testConfigurationConstructorWithAbsoluteConf(@TempDir Path existHomeDir) throws Exception {
        final Path conf = existHomeDir.resolve("test-conf.xml");
        try (InputStream in = getClass().getResourceAsStream("/conf.xml")) {
            Files.copy(in, conf);
        }
        assertThatNoException().isThrownBy(() -> new Configuration(conf.toString(), Optional.of(existHomeDir)));
    }

    // parseBooleanAttribute — #6001: unified boolean config attribute parsing
    @Test
    void parseBooleanAttributeYes() throws Exception {
        assertThat(Configuration.parseBooleanAttribute(elem("case", "yes"), "case", false)).isTrue();
        assertThat(Configuration.parseBooleanAttribute(elem("case", "yes"), "case", true)).isTrue();
    }

    @Test
    void parseBooleanAttributeTrue() throws Exception {
        assertThat(Configuration.parseBooleanAttribute(elem("store", "true"), "store", false)).isTrue();
        assertThat(Configuration.parseBooleanAttribute(elem("store", "TRUE"), "store", false)).isTrue();
    }

    @Test
    void parseBooleanAttributeNo() throws Exception {
        assertThat(Configuration.parseBooleanAttribute(elem("case", "no"), "case", true)).isFalse();
        assertThat(Configuration.parseBooleanAttribute(elem("case", "no"), "case", false)).isFalse();
    }

    @Test
    void parseBooleanAttributeFalse() throws Exception {
        assertThat(Configuration.parseBooleanAttribute(elem("binary", "false"), "binary", true)).isFalse();
    }

    @Test
    void parseBooleanAttributeEmptyUsesDefault() throws Exception {
        assertThat(Configuration.parseBooleanAttribute(elem("case", ""), "case", true)).isTrue();
        assertThat(Configuration.parseBooleanAttribute(elem("case", ""), "case", false)).isFalse();
    }

    @Test
    void parseBooleanAttributeMissingUsesDefault() throws Exception {
        final Element e = elem("other", "x");
        assertThat(Configuration.parseBooleanAttribute(e, "case", true)).isTrue();
        assertThat(Configuration.parseBooleanAttribute(e, "case", false)).isFalse();
    }

    @Test
    void parseBooleanAttributeInvalidTreatsAsFalse() throws Exception {
        assertThat(Configuration.parseBooleanAttribute(elem("case", "n"), "case", true)).isFalse();
        assertThat(Configuration.parseBooleanAttribute(elem("case", "y"), "case", true)).isFalse();
        assertThat(Configuration.parseBooleanAttribute(elem("case", "1"), "case", false)).isFalse();
        assertThat(Configuration.parseBooleanAttribute(elem("case", "0"), "case", true)).isFalse();
    }

    // #6563: enabled="no"-suppressed built-in modules/indexes are retained (not discarded) in a
    // reporting-only registry, so system:get-registered-modules()/get-registered-indexes() can
    // still see them, while the active classMap/PROPERTY_INDEXER_MODULES stay exactly as before.
    private static Configuration withDisabledModuleAndIndexStub(final Path existHomeDir) throws Exception {
        final String canonical;
        try (InputStream in = ConfigurationTest.class.getResourceAsStream("/conf.xml")) {
            canonical = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        final String withDisabledModule = canonical.replace("</builtin-modules>",
                "<module uri=\"http://exist-db.org/xquery/test/disabled-module\" "
                        + "class=\"org.exist.xquery.functions.util.UtilModule\" enabled=\"no\"/>\n</builtin-modules>");
        final String withDisabledIndex = withDisabledModule.replace("</modules>",
                "<module id=\"test-disabled-index\" class=\"org.exist.indexing.range.RangeIndex\" enabled=\"no\"/>\n</modules>");

        final Path conf = existHomeDir.resolve("test-conf.xml");
        Files.writeString(conf, withDisabledIndex, StandardCharsets.UTF_8);
        return new Configuration(conf.toString(), Optional.of(existHomeDir));
    }

    @Test
    @SuppressWarnings("unchecked")
    void disabledModuleIsRetainedInRegistryButNotInActiveClassMap(@TempDir final Path existHomeDir) throws Exception {
        final Configuration configuration = withDisabledModuleAndIndexStub(existHomeDir);

        final List<ModuleRegistration> registrations =
                (List<ModuleRegistration>) configuration.getProperty(XQueryContext.PROPERTY_MODULE_REGISTRATIONS);
        assertThat(registrations)
                .filteredOn(r -> r.uri().equals("http://exist-db.org/xquery/test/disabled-module"))
                .singleElement()
                .satisfies(r -> assertThat(r.enabled()).isFalse());

        final var activeClassMap = (java.util.Map<String, Class<?>>) configuration.getProperty(XQueryContext.PROPERTY_BUILT_IN_MODULES);
        assertThat(activeClassMap).doesNotContainKey("http://exist-db.org/xquery/test/disabled-module");
    }

    @Test
    @SuppressWarnings("unchecked")
    void disabledIndexIsRetainedInRegistryButNotInActiveList(@TempDir final Path existHomeDir) throws Exception {
        final Configuration configuration = withDisabledModuleAndIndexStub(existHomeDir);

        final List<Configuration.IndexModuleConfig> registry =
                (List<Configuration.IndexModuleConfig>) configuration.getProperty(IndexManager.PROPERTY_INDEXER_MODULES_REGISTRY);
        assertThat(registry)
                .filteredOn(m -> m.id().equals("test-disabled-index"))
                .singleElement()
                .satisfies(m -> assertThat(m.enabled()).isFalse());

        final Configuration.IndexModuleConfig[] active =
                (Configuration.IndexModuleConfig[]) configuration.getProperty(IndexManager.PROPERTY_INDEXER_MODULES);
        assertThat(active).extracting(Configuration.IndexModuleConfig::id).doesNotContain("test-disabled-index");
    }
}
