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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SchemaVersionTest {

    private static final String CAPTURE_LOGGER = "org.exist.util.SchemaVersionTest.capture";

    private CapturingAppender appender;

    @Before
    public void attachAppender() {
        appender = new CapturingAppender();
        appender.start();
        // The test log4j2 config sets the root logger to OFF, which would filter these events before
        // any appender sees them. Install a dedicated LoggerConfig at level ALL with our capturing
        // appender, mirroring DeferredFunctionCallErrorTest's pattern.
        final Logger logger = (Logger) LogManager.getLogger(CAPTURE_LOGGER);
        final LoggerContext ctx = logger.getContext();
        final Configuration config = ctx.getConfiguration();
        config.addAppender(appender);
        final LoggerConfig loggerConfig = LoggerConfig.newBuilder()
                .withLoggerName(CAPTURE_LOGGER)
                .withLevel(Level.ALL)
                .withAdditivity(false)
                .withConfig(config)
                .build();
        loggerConfig.addAppender(appender, Level.ALL, null);
        config.addLogger(CAPTURE_LOGGER, loggerConfig);
        ctx.updateLoggers();
    }

    @After
    public void detachAppender() {
        final Logger logger = (Logger) LogManager.getLogger(CAPTURE_LOGGER);
        final LoggerContext ctx = logger.getContext();
        ctx.getConfiguration().removeLogger(CAPTURE_LOGGER);
        ctx.updateLoggers();
        appender.stop();
    }

    @Test
    public void attributeNameIsSchemaVersion() {
        assertEquals("schemaVersion", SchemaVersion.ATTRIBUTE);
    }

    @Test
    public void logDocumentVersionAcceptsMatchingValue() throws Exception {
        final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        final var root = doc.createElement("exist");
        root.setAttribute(SchemaVersion.ATTRIBUTE, SchemaVersion.CONF);
        doc.appendChild(root);

        SchemaVersion.logDocumentVersion(LogManager.getLogger(CAPTURE_LOGGER),
                root, SchemaVersion.CONF, "test conf.xml");

        assertEquals(Level.DEBUG, appender.lastLevel);
        assertTrue(appender.lastMessage.contains("test conf.xml"));
        assertTrue(appender.lastMessage.contains(SchemaVersion.CONF));
    }

    @Test
    public void logDocumentVersionAcceptsMissingAttribute() throws Exception {
        final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        final var root = doc.createElement("exist");
        doc.appendChild(root);

        SchemaVersion.logDocumentVersion(LogManager.getLogger(CAPTURE_LOGGER),
                root, SchemaVersion.CONF, "legacy conf.xml");

        assertEquals(Level.DEBUG, appender.lastLevel);
        assertTrue(appender.lastMessage.contains("legacy conf.xml"));
        assertTrue(appender.lastMessage.contains("no " + SchemaVersion.ATTRIBUTE + " attribute"));
    }

    @Test
    public void logDocumentVersionWarnsOnMismatch() throws Exception {
        final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        final var root = doc.createElement("exist");
        root.setAttribute(SchemaVersion.ATTRIBUTE, "0.0.1");
        doc.appendChild(root);

        SchemaVersion.logDocumentVersion(LogManager.getLogger(CAPTURE_LOGGER),
                root, SchemaVersion.CONF, "outdated conf.xml");

        assertEquals(Level.WARN, appender.lastLevel);
        assertTrue(appender.lastMessage.contains("outdated conf.xml"));
        assertTrue(appender.lastMessage.contains("0.0.1"));
        assertTrue(appender.lastMessage.contains(SchemaVersion.CONF));
    }

    private static final class CapturingAppender extends AbstractAppender {

        private volatile Level lastLevel;
        private volatile String lastMessage;

        CapturingAppender() {
            super("schema-version-capture", null, null, false, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(final LogEvent event) {
            lastLevel = event.getLevel();
            lastMessage = event.getMessage().getFormattedMessage();
        }
    }
}
