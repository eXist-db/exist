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

import java.io.InputStream;
import java.util.Map;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for {@link ConfigurationImpl#getPropertyMap(String)}.
 *
 * <p>Covers <a href="https://github.com/eXist-db/exist/issues/5904">GH-5904</a>: an
 * {@code &lt;account&gt;} (or {@code &lt;group&gt;}) config element carrying exactly one
 * {@code &lt;metadata key="..."&gt;} entry had that entry mis-cached by
 * {@link ConfigurationImpl#cache()} as a plain scalar property, discarding its
 * {@code key} attribute and making {@code sm:get-account-metadata} return an empty
 * sequence instead of the stored value. The fix makes {@code getPropertyMap()} never consult
 * that scalar cache at all -- it always resolves a map-typed property via its own element scan,
 * since {@code Configurator} only calls it for a field already known (by its declared Java
 * type) to be {@code Map}-typed.</p>
 */
public class ConfigurationImplPropertyMapTest {

    private static final String NAME_PERSON = "http://axschema.org/namePerson";
    private static final String EMAIL = "http://axschema.org/email";

    private static Configuration parseAccount(final String metadataElements) throws Exception {
        final String xml =
                "<account xmlns='http://exist-db.org/Configuration' id='15'>" +
                "<group name='nogroup'/>" +
                "<password>{RIPEMD160}q2VXP75jMi+d8E5VAsEr6pD8V5w=</password>" +
                "<expired>false</expired>" +
                "<enabled>true</enabled>" +
                "<umask>022</umask>" +
                metadataElements +
                "<name>user1</name>" +
                "</account>";
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(xml.getBytes(UTF_8))) {
            return Configurator.parse(is);
        }
    }

    @Test
    public void singleMetadataEntry_isNotLost() throws Exception {
        final Configuration config = parseAccount("<metadata key='" + NAME_PERSON + "'>User 1</metadata>");

        final Map<String, String> metadata = config.getPropertyMap("metadata");

        assertEquals("User 1", metadata.get(NAME_PERSON));
    }

    @Test
    public void multipleMetadataEntries_areAllReadable() throws Exception {
        final Configuration config = parseAccount(
                "<metadata key='" + NAME_PERSON + "'>User 1</metadata>" +
                "<metadata key='" + EMAIL + "'>user1@example.com</metadata>");

        final Map<String, String> metadata = config.getPropertyMap("metadata");

        assertEquals("User 1", metadata.get(NAME_PERSON));
        assertEquals("user1@example.com", metadata.get(EMAIL));
    }

    @Test
    public void noMetadataEntries_returnsEmptyMap() throws Exception {
        final Configuration config = parseAccount("");

        assertTrue(config.getPropertyMap("metadata").isEmpty());
    }

    @Test
    public void otherScalarProperties_areUnaffected() throws Exception {
        final Configuration config = parseAccount("<metadata key='" + NAME_PERSON + "'>User 1</metadata>");

        assertEquals("true", config.getProperty("enabled"));
        assertEquals("022", config.getProperty("umask"));
        assertEquals("user1", config.getProperty("name"));
    }

    /**
     * A malformed, key-less {@code &lt;metadata&gt;} element sitting alongside a well-formed
     * one used to reach {@link ConfigurationImpl#getPropertyMap(String)}'s element-scanning
     * loop, where a {@code continue} on the malformed element skipped advancing to the next
     * sibling and spun forever. The JUnit timeout fails the build instead of hanging the test
     * run; the returned value also must not hide the well-formed sibling, regardless of
     * ordering.
     */
    @Test(timeout = 10_000)
    public void malformedEntryBeforeWellFormed_wellFormedEntryStillReadable() throws Exception {
        final Configuration config = parseAccount(
                "<metadata>orphan-no-key</metadata>" +
                "<metadata key='" + NAME_PERSON + "'>User 1</metadata>");

        assertEquals("User 1", config.getPropertyMap("metadata").get(NAME_PERSON));
    }

    @Test(timeout = 10_000)
    public void malformedEntryAfterWellFormed_wellFormedEntryStillReadable() throws Exception {
        final Configuration config = parseAccount(
                "<metadata key='" + NAME_PERSON + "'>User 1</metadata>" +
                "<metadata>orphan-no-key</metadata>");

        assertEquals("User 1", config.getPropertyMap("metadata").get(NAME_PERSON));
    }

    /**
     * A single key-less {@code &lt;metadata&gt;}, with no well-formed sibling to force the
     * duplicate-name cache eviction in {@link ConfigurationImpl#cache()}, used to get scalar
     * cached and returned as {@code {"metadata": "orphan"}} -- the same map-field corruption
     * GH-5904 reported, just triggered by a missing {@code key} attribute instead of a lone
     * well-formed entry.
     */
    @Test
    public void loneMalformedEntry_isIgnoredNotScalarCached() throws Exception {
        final Configuration config = parseAccount("<metadata>orphan-no-key</metadata>");

        assertTrue(config.getPropertyMap("metadata").isEmpty());
    }

    /**
     * A well-formed entry that also happens to carry an incidental extra attribute must still
     * be resolved by its {@code key} attribute alone, not discarded for having more than one
     * attribute.
     */
    @Test
    public void entryWithExtraAttribute_stillReadableByKey() throws Exception {
        final Configuration config = parseAccount(
                "<metadata key='" + NAME_PERSON + "' extra='x'>User 1</metadata>");

        assertEquals("User 1", config.getPropertyMap("metadata").get(NAME_PERSON));
    }
}
