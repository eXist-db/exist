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
package org.exist.test.xqsuite;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.JUnitException;
import org.junit.platform.engine.ConfigurationParameters;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XQSuiteSettingsTest {

    private static XQSuiteSettings settings(final Map<String, String> values) {
        return new XQSuiteSettings(new ConfigurationParameters() {
            @Override
            public Optional<String> get(final String key) {
                return Optional.ofNullable(values.get(key));
            }

            @Override
            public Optional<Boolean> getBoolean(final String key) {
                return get(key).map(Boolean::parseBoolean);
            }

            @Override
            public Set<String> keySet() {
                return values.keySet();
            }
        });
    }

    @Test
    void defaults() {
        final XQSuiteSettings settings = settings(Map.of());
        assertEquals(Duration.ofMinutes(5), settings.hangThreshold());
        assertEquals(Duration.ofSeconds(30), settings.hangWatcherInterval());
        assertEquals(Duration.ofSeconds(60), settings.hangGrace());
    }

    @Test
    void defaultParallelismFollowsTheProcessorsAndTheBrokerPoolWithinBounds() {
        final XQSuiteSettings settings = settings(Map.of());
        assertEquals(2, settings.parallelism(1), "never fewer than two");
        assertEquals(Math.max(2, Math.min(32, Math.min(Runtime.getRuntime().availableProcessors(), 8))), settings.parallelism(8));
        assertTrue(settings.parallelism(1000) <= 32, "never more than 32");
    }

    @Test
    void settingsOverrideTheDefaults() {
        final XQSuiteSettings settings = settings(Map.of(
                XQSuiteSettings.PARALLELISM, "3",
                XQSuiteSettings.HANG_THRESHOLD_MINUTES, "0.5",
                XQSuiteSettings.HANG_WATCHER_INTERVAL_SECONDS, "7",
                XQSuiteSettings.HANG_GRACE_SECONDS, "2"));
        assertEquals(3, settings.parallelism(1), "an explicit value is used as given");
        assertEquals(Duration.ofSeconds(30), settings.hangThreshold());
        assertEquals(Duration.ofSeconds(7), settings.hangWatcherInterval());
        assertEquals(Duration.ofSeconds(2), settings.hangGrace());
    }

    @Test
    void watcherIntervalIsATenthOfTheThresholdWithinBounds() {
        assertEquals(Duration.ofSeconds(1), settings(Map.of(XQSuiteSettings.HANG_THRESHOLD_MINUTES, "0.04")).hangWatcherInterval());
        assertEquals(Duration.ofSeconds(6), settings(Map.of(XQSuiteSettings.HANG_THRESHOLD_MINUTES, "1")).hangWatcherInterval());
        assertEquals(Duration.ofSeconds(30), settings(Map.of(XQSuiteSettings.HANG_THRESHOLD_MINUTES, "60")).hangWatcherInterval());
    }

    @Test
    void invalidValuesAreRejectedAndNamed() {
        assertEquals(true, assertThrows(JUnitException.class, () -> settings(Map.of(XQSuiteSettings.PARALLELISM, "many")).parallelism(8))
                .getMessage().contains(XQSuiteSettings.PARALLELISM));
        assertThrows(JUnitException.class, () -> settings(Map.of(XQSuiteSettings.PARALLELISM, "0")).parallelism(8));
        assertThrows(JUnitException.class, () -> settings(Map.of(XQSuiteSettings.HANG_THRESHOLD_MINUTES, "0")).hangThreshold());
        assertThrows(JUnitException.class, () -> settings(Map.of(XQSuiteSettings.HANG_WATCHER_INTERVAL_SECONDS, "0")).hangWatcherInterval());
    }
}
