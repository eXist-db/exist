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
package org.exist.xquery.functions.fn;

import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that functions which map case or match names do not depend on the JVM default locale.
 * The Turkish locale is used because it maps "i" and "I" to the dotted and dotless forms.
 */
public class XQueryDefaultLocaleTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    @ResourceLock(Resources.LOCALE)
    public void upperAndLowerCase() throws Exception {
        assertEquals("TITLE", executeUnderTurkishLocale("fn:upper-case('title')"));
        assertEquals("title", executeUnderTurkishLocale("fn:lower-case('TITLE')"));
    }

    @Test
    @ResourceLock(Resources.LOCALE)
    public void formatDateNameCase() throws Exception {
        assertEquals("FRIDAY",
                executeUnderTurkishLocale("fn:format-date(xs:date('2024-01-05'), '[FN]', 'en', (), ())"));
    }

    @Test
    @ResourceLock(Resources.LOCALE)
    public void collationWithoutLanguage() throws Exception {
        // "u" and "u" with a diaeresis are different letters at primary strength in Turkish
        assertEquals("0",
                executeUnderTurkishLocale("fn:compare('Düsseldorf', 'Dusseldorf', '?strength=primary')"));
    }

    @Test
    @ResourceLock(Resources.LOCALE)
    public void formatIntegerWordsInUnsupportedLanguage() throws Exception {
        assertEquals("one", executeUnderTurkishLocale("fn:format-integer(1, 'w', 'nonsense')"));
    }

    private String executeUnderTurkishLocale(final String query) throws Exception {
        final Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
            final XQuery xquery = brokerPool.getXQueryService();
            try (final DBBroker broker = brokerPool.get(
                    Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
                final XQueryContext context = new XQueryContext(brokerPool);
                final CompiledXQuery compiled = xquery.compile(context, new StringSource(query));
                final Sequence result = xquery.execute(broker, compiled, null);
                return result.getStringValue();
            }
        } finally {
            Locale.setDefault(previous);
        }
    }
}
