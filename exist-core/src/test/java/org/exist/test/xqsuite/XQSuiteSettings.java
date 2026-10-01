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

import org.junit.platform.commons.JUnitException;
import org.junit.platform.engine.ConfigurationParameters;

import java.time.Duration;
import java.util.function.Function;

/**
 * Settings of the {@link XQSuiteTestEngine}, read from the JUnit configuration parameters
 * (so they can be set with a system property, in junit-platform.properties, or by the launcher).
 */
final class XQSuiteSettings {

    /** how many test files of a parallel suite run at once */
    static final String PARALLELISM = "exist.xqsuite.parallelism";

    /** how long a running test file may report nothing before it is considered hung, in minutes (may be fractional) */
    static final String HANG_THRESHOLD_MINUTES = "exist.xqsuite.hang.threshold.minutes";

    /** how often running test files are checked for being hung, in seconds */
    static final String HANG_WATCHER_INTERVAL_SECONDS = "exist.xqsuite.hang.watcher.interval.seconds";

    /** how long to wait for the thread of a hung test file to stop once it has been interrupted, in seconds */
    static final String HANG_GRACE_SECONDS = "exist.xqsuite.hang.grace.seconds";

    private static final int MIN_PARALLELISM = 2;
    private static final int MAX_PARALLELISM = 32;
    private static final double DEFAULT_HANG_THRESHOLD_MINUTES = 5.0;
    private static final long DEFAULT_HANG_GRACE_SECONDS = 60;

    private final ConfigurationParameters parameters;

    XQSuiteSettings(final ConfigurationParameters parameters) {
        this.parameters = parameters;
    }

    /**
     * @param brokerPoolMax the most brokers (concurrent database users) the database allows
     */
    int parallelism(final int brokerPoolMax) {
        final Integer configured = parse(PARALLELISM, Integer::parseInt);
        if (configured != null) {
            if (configured < 1) {
                throw invalid(PARALLELISM, String.valueOf(configured), "must be at least 1");
            }
            return configured;
        }
        final int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(MIN_PARALLELISM, Math.min(MAX_PARALLELISM, Math.min(processors, brokerPoolMax)));
    }

    Duration hangThreshold() {
        final Double minutes = parse(HANG_THRESHOLD_MINUTES, Double::parseDouble);
        final double value = minutes != null ? minutes : DEFAULT_HANG_THRESHOLD_MINUTES;
        if (!(value > 0)) {
            throw invalid(HANG_THRESHOLD_MINUTES, String.valueOf(value), "must be greater than 0");
        }
        return Duration.ofMillis((long) (value * 60_000));
    }

    /**
     * Unless set, a tenth of the hang threshold, but at least a second and at most 30 seconds.
     */
    Duration hangWatcherInterval() {
        final Long seconds = parse(HANG_WATCHER_INTERVAL_SECONDS, Long::parseLong);
        if (seconds != null) {
            if (seconds < 1) {
                throw invalid(HANG_WATCHER_INTERVAL_SECONDS, String.valueOf(seconds), "must be at least 1");
            }
            return Duration.ofSeconds(seconds);
        }
        final Duration tenth = hangThreshold().dividedBy(10);
        return tenth.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1)
                : tenth.compareTo(Duration.ofSeconds(30)) > 0 ? Duration.ofSeconds(30) : tenth;
    }

    Duration hangGrace() {
        final Long seconds = parse(HANG_GRACE_SECONDS, Long::parseLong);
        return Duration.ofSeconds(seconds != null ? Math.max(0, seconds) : DEFAULT_HANG_GRACE_SECONDS);
    }

    private <T> T parse(final String key, final Function<String, T> parser) {
        final String value = parameters.get(key).map(String::trim).filter(v -> !v.isEmpty()).orElse(null);
        if (value == null) {
            return null;
        }
        try {
            return parser.apply(value);
        } catch (final NumberFormatException e) {
            throw invalid(key, value, "is not a number");
        }
    }

    private static JUnitException invalid(final String key, final String value, final String problem) {
        return new JUnitException("Invalid value '" + value + "' for configuration parameter " + key + ": " + problem);
    }
}
