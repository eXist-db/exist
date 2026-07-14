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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A caller which may EXECUTE but not READ a stored query must learn only that the execution
 * failed. {@link ErrorDisclosure#disclose(XQueryContext, XPathException)} is the filter which
 * enforces that, so this asserts it strips everything derived from the source and logs the real
 * error server-side under a correlation id the caller can quote.
 */
public class ErrorDisclosureTest {

    private static final String DISCLOSURE_LOGGER = "org.exist.xquery.ErrorDisclosure";

    /** the secrets which must never reach a read-blind caller */
    private static final String SECRET_MESSAGE = "unknown variable '$secretPassword' in module /db/secret/lib.xqm";
    private static final int SECRET_LINE = 42;
    private static final int SECRET_COLUMN = 13;

    private static final Pattern CORRELATION_ID = Pattern.compile("\\(ref ([0-9a-f-]+)\\)");

    private CapturingAppender appender;

    @Before
    public void attachAppender() {
        appender = new CapturingAppender();
        appender.start();
        // the test log4j2 config sets the root logger to OFF, which would filter these events before
        // any appender sees them, so install a dedicated LoggerConfig for ErrorDisclosure at level ALL
        final Logger logger = (Logger) LogManager.getLogger(DISCLOSURE_LOGGER);
        final LoggerContext ctx = logger.getContext();
        final Configuration config = ctx.getConfiguration();
        config.addAppender(appender);
        final LoggerConfig loggerConfig = LoggerConfig.newBuilder()
                .withLoggerName(DISCLOSURE_LOGGER)
                .withLevel(Level.ALL)
                .withAdditivity(false)
                .withConfig(config)
                .build();
        loggerConfig.addAppender(appender, Level.ALL, null);
        config.addLogger(DISCLOSURE_LOGGER, loggerConfig);
        ctx.updateLoggers();
    }

    @After
    public void detachAppender() {
        final Logger logger = (Logger) LogManager.getLogger(DISCLOSURE_LOGGER);
        final LoggerContext ctx = logger.getContext();
        ctx.getConfiguration().removeLogger(DISCLOSURE_LOGGER);
        ctx.updateLoggers();
        appender.stop();
    }

    @Test
    public void fullDisclosureReturnsTheOriginalError() {
        final XQueryContext context = new XQueryContext();
        assertEquals("FULL must be the default", ErrorDisclosure.FULL, context.getErrorDisclosure());

        final XPathException original = secretError();
        final XPathException disclosed = ErrorDisclosure.disclose(context, original);

        assertSame("a read-capable caller must see the error unchanged", original, disclosed);
        assertTrue("nothing to hide, so nothing to log", appender.events.isEmpty());
    }

    @Test
    public void genericDisclosureStripsEverythingDerivedFromTheSource() {
        final XQueryContext context = new XQueryContext();
        context.setErrorDisclosure(ErrorDisclosure.GENERIC);

        final XPathException original = secretError();
        final XPathException disclosed = ErrorDisclosure.disclose(context, original);

        assertNotEquals("a read-blind caller must not see the original error", original, disclosed);
        assertEquals(ErrorCodes.EXXQDY0010, disclosed.getErrorCode());

        final String message = disclosed.getMessage();
        assertTrue("the caller learns that the execution failed", message.contains("Query execution failed"));
        assertFalse("the message must not leak the original message", message.contains(SECRET_MESSAGE));
        assertFalse("the message must not leak the original error code", message.contains("XPST0003"));
        // NOTE: assert on the location itself rather than on the digits of SECRET_LINE appearing in the
        // message, which the hex of a random correlation id can contain by chance
        assertFalse("the message must not leak the location", message.contains("at line"));
        assertEquals("the line number must not be carried over", 0, disclosed.getLine());
        assertEquals("the column number must not be carried over", 0, disclosed.getColumn());
        assertNull("the cause chain must not be carried over", disclosed.getCause());
    }

    @Test
    public void genericDisclosureLogsTheFullErrorUnderTheCorrelationIdGivenToTheCaller() {
        final XQueryContext context = new XQueryContext();
        context.setErrorDisclosure(ErrorDisclosure.GENERIC);

        final XPathException original = secretError();
        final XPathException disclosed = ErrorDisclosure.disclose(context, original);

        final Matcher matcher = CORRELATION_ID.matcher(disclosed.getMessage());
        assertTrue("the caller must be given a correlation id to quote: " + disclosed.getMessage(), matcher.find());
        final String correlationId = matcher.group(1);

        assertEquals("the failure must be logged exactly once", 1, appender.events.size());
        final LogEvent event = appender.events.getFirst();
        assertEquals(Level.WARN, event.getLevel());

        final String logged = event.getMessage().getFormattedMessage();
        assertTrue("the log must carry the same correlation id as the caller's error",
                logged.contains(correlationId));
        assertTrue("the log must identify the real user", logged.contains("realUser="));
        assertTrue("the log must identify the effective user", logged.contains("effectiveUser="));
        assertSame("the full error must be logged for the owner/DBA", original, event.getThrown());
    }

    @Test
    public void eachFailureGetsItsOwnCorrelationId() {
        final XQueryContext context = new XQueryContext();
        context.setErrorDisclosure(ErrorDisclosure.GENERIC);

        final String first = ErrorDisclosure.disclose(context, secretError()).getMessage();
        final String second = ErrorDisclosure.disclose(context, secretError()).getMessage();

        assertNotEquals(first, second);
    }

    private static XPathException secretError() {
        final XPathException e = new XPathException((Expression) null, ErrorCodes.XPST0003, SECRET_MESSAGE,
                new IllegalStateException("internal cause"));
        e.setLocation(SECRET_LINE, SECRET_COLUMN);
        return e;
    }

    private static final class CapturingAppender extends AbstractAppender {
        private final List<LogEvent> events = new ArrayList<>();

        CapturingAppender() {
            super("error-disclosure-capture", null, null, false, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(final LogEvent event) {
            events.add(event.toImmutable());
        }
    }
}
