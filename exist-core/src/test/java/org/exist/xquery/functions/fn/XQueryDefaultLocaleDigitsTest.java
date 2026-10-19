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

import org.exist.backup.BackupDirectory;
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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that numbers and dates written into results, index keys and file names use ASCII digits
 * and the Gregorian calendar whatever the JVM default locale is.
 * The Egyptian Arabic locale is used because it formats numbers with Arabic-Indic digits.
 */
public class XQueryDefaultLocaleDigitsTest {

    private static final Locale ARABIC_DIGITS = Locale.forLanguageTag("ar-EG");

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @TempDir
    Path tempDir;

    @Test
    @ResourceLock(Resources.LOCALE)
    public void path() throws Exception {
        assertEquals("Q{http://www.w3.org/2005/xpath-functions}root()/Q{}b[1]/text()[1]",
                executeUnderArabicLocale("fn:path(<a><b>x</b></a>/b/text())"));
    }

    @Test
    @ResourceLock(Resources.LOCALE)
    public void formatTimeFractionAndTimezone() throws Exception {
        assertEquals("10:20:30.500 +05:30",
                executeUnderArabicLocale("fn:format-time(xs:time('10:20:30.5+05:30'), '[H01]:[m01]:[s01].[f001] [Z]', 'en', (), ())"));
    }

    @Test
    @ResourceLock(Resources.LOCALE)
    public void backupDirectoryNames() {
        final Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(ARABIC_DIGITS);
            final String name = new BackupDirectory(tempDir).createBackup(false, false).getFileName().toString();
            assertTrue(name.matches(BackupDirectory.FILE_REGEX), name + " should match " + BackupDirectory.FILE_REGEX);
        } finally {
            Locale.setDefault(previous);
        }
    }

    private String executeUnderArabicLocale(final String query) throws Exception {
        final Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(ARABIC_DIGITS);
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
