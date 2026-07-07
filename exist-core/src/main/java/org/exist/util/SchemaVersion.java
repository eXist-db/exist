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

import org.apache.logging.log4j.Logger;
import org.w3c.dom.Element;

/**
 * Optional {@code schemaVersion} on native config instance documents — mirrors
 * {@code xs:schema/@version} on the paired XSD (native schema semver, not eXist product version).
 */
public final class SchemaVersion {

    public static final String ATTRIBUTE = "schemaVersion";

    /**
     * Paired {@code xs:schema/@version} values for canonical templates -- generated at build time
     * from {@code schema/*.xsd} itself (see {@link GeneratedSchemaVersions}), so these can never
     * drift from the schemas they describe.
     */
    public static final String CONF = GeneratedSchemaVersions.CONF;
    public static final String COLLECTION_XCONF = GeneratedSchemaVersions.COLLECTION_XCONF;
    public static final String DESCRIPTOR = GeneratedSchemaVersions.DESCRIPTOR;
    public static final String MIME_TYPES = GeneratedSchemaVersions.MIME_TYPES;
    public static final String CONTROLLER_CONFIG = GeneratedSchemaVersions.CONTROLLER_CONFIG;

    private SchemaVersion() {
    }

    /**
     * Log when {@code schemaVersion} is missing (legacy) or differs from the schema version this build expects.
     */
    public static void logDocumentVersion(final Logger log, final Element root,
            final String expectedVersion, final String documentDescription) {
        logDocumentVersion(log, root != null ? root.getAttribute(ATTRIBUTE) : "", expectedVersion, documentDescription);
    }

    /**
     * SAX variant when only the attribute value is available.
     */
    public static void logDocumentVersion(final Logger log, final String declaredVersion,
            final String expectedVersion, final String documentDescription) {
        if (declaredVersion == null || declaredVersion.isEmpty()) {
            log.debug("{} has no {} attribute (legacy document)", documentDescription, ATTRIBUTE);
            return;
        }
        if (!declaredVersion.equals(expectedVersion)) {
            log.warn("{} declares {}=\"{}\" but this eXist build expects \"{}\"",
                    documentDescription, ATTRIBUTE, declaredVersion, expectedVersion);
        } else {
            log.debug("{} {}=\"{}\"", documentDescription, ATTRIBUTE, declaredVersion);
        }
    }
}
