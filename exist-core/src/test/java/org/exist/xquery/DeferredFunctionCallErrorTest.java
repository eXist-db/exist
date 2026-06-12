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
import org.exist.dom.QName;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Regression test: an error raised inside a deferred (tail-call) function must be logged once, not
 * re-logged on every {@link Sequence} accessor.
 *
 * <p>{@link DeferredFunctionCall} runs its body lazily and caches any exception, re-throwing it on
 * every later access. Its non-throwing {@code Sequence} accessor methods can't propagate the checked
 * {@link XPathException}, so they catch it and return a default — but they also logged it every time,
 * so a consumer that touches the same deferred value through several accessors (the serializer and the
 * html-templating engine both do) produced one log line per accessor. This asserts the body runs once
 * and the failure is logged once, however many accessors are called.</p>
 */
public class DeferredFunctionCallErrorTest {

    private static final QName FN = new QName("boom", "http://exist-db.org/test");
    private static final String DEFERRED_LOGGER = "org.exist.xquery.DeferredFunctionCall";

    private CountingAppender appender;

    @Before
    public void attachAppender() {
        appender = new CountingAppender();
        appender.start();
        // The test log4j2 config sets the root logger to OFF, which would filter these events before
        // any appender sees them. Install a dedicated LoggerConfig for DeferredFunctionCall at level
        // ALL with our counting appender so the errors it logs are captured.
        final Logger logger = (Logger) LogManager.getLogger(DEFERRED_LOGGER);
        final LoggerContext ctx = logger.getContext();
        final Configuration config = ctx.getConfiguration();
        config.addAppender(appender);
        final LoggerConfig loggerConfig = LoggerConfig.newBuilder()
                .withLoggerName(DEFERRED_LOGGER)
                .withLevel(Level.ALL)
                .withAdditivity(false)
                .withConfig(config)
                .build();
        loggerConfig.addAppender(appender, Level.ALL, null);
        config.addLogger(DEFERRED_LOGGER, loggerConfig);
        ctx.updateLoggers();
    }

    @After
    public void detachAppender() {
        final Logger logger = (Logger) LogManager.getLogger(DEFERRED_LOGGER);
        final LoggerContext ctx = logger.getContext();
        ctx.getConfiguration().removeLogger(DEFERRED_LOGGER);
        ctx.updateLoggers();
        appender.stop();
    }

    @Test
    public void errorIsComputedOnceAndLoggedOnce() throws XPathException {
        final AtomicInteger executeCount = new AtomicInteger();
        final DeferredFunctionCall dfc = new DeferredFunctionCall(new FunctionSignature(FN)) {
            @Override
            protected Sequence execute() throws XPathException {
                executeCount.incrementAndGet();
                throw new XPathException((Expression) null, ErrorCodes.FOER0000, "boom");
            }

            @Override
            public boolean containsReference(final Item item) {
                return false;
            }

            @Override
            public boolean contains(final Item item) {
                return false;
            }
        };

        // Touch the deferred value through several non-throwing accessors, as a consumer
        // (e.g. the serializer or the templating engine) does while inspecting a result sequence.
        dfc.getItemCountLong();
        dfc.isEmpty();
        dfc.hasOne();
        dfc.hasMany();
        dfc.getCardinality();
        dfc.itemAt(0);
        dfc.getItemType();

        assertEquals("deferred body must run exactly once", 1, executeCount.get());
        assertEquals("the error must be logged once, not once per accessor", 1, appender.count.get());

        // the failure must still surface to the caller through a throwing accessor — the fix de-dupes
        // the logging, it does not hide the error.
        try {
            dfc.iterate();
            fail("the deferred error should surface through a throwing accessor");
        } catch (final XPathException expected) {
            // expected: realize() re-throws the cached exception
        }
        assertEquals("the body must still run only once", 1, executeCount.get());
        assertEquals("surfacing the error must not add a log line", 1, appender.count.get());
    }

    private static final class CountingAppender extends AbstractAppender {
        private final AtomicInteger count = new AtomicInteger();

        CountingAppender() {
            super("deferred-fn-counter", null, null, false, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(final LogEvent event) {
            if (event.getMessage().getFormattedMessage().contains("Exception in deferred function")) {
                count.incrementAndGet();
            }
        }
    }
}
