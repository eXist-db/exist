/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.xquery.pragmas;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.logging.log4j.Level;
import org.exist.xquery.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;
import java.util.Optional;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;

/**
 * An XQuery Pragma that will record the execution
 * time of the associated expression via logging.
 *
 * <pre>{@code
 * (# exist:time verbose=yes logger-name=MyLog logging-level=TRACE log-message-prefix=LOOK-HERE #) {
 *     (: the XQuery Expression that you wish to time goes here :)
 * }
 * }</pre>
 *
 * The following optional configuration options may be given to the Time Pragma via the pragma's contents:
 *     * verbose - Set to 'true' if you want the associated expression to be logged to. You may also use 'yes' instead of 'true' but its use is deprecated and may be removed in the future.
 *     * logger-name - The name of the logger to use, if omitted the logger for {@link TimePragma} will be used.
 *     * logging-level - The Slf4j level at which the timing should be logged, e.g. Trace, Debug, Info, Warn, Error, etc. If omitted this defaults to 'Trace' level.
 *     * log-message-prefix - An optional prefix to append to the start of the log message to help you identify it.
 *     * measurement-mode - indicates whether we should measure a single invocation of the additional expression or multiple invocations 'single' or 'multiple'. The default is 'single'.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class TimePragma extends AbstractPragma {

    public static final String TIME_PRAGMA_LOCAL_NAME = "time";
    public static final QName TIME_PRAGMA = new QName(TIME_PRAGMA_LOCAL_NAME, Namespaces.EXIST_NS, "exist");
    public static final String DEPRECATED_TIMER_PRAGMA_LOCAL_NAME = "timer";

    private final Options options;
    @Nullable private Timing timing = null;

    public TimePragma(final Expression expression, final QName qname, final String contents) throws XPathException {
        super(expression, qname, contents);
        this.options = parseOptions(getContents());
    }

    @Override
    public void before(final XQueryContext context, final Expression expression, final Sequence contextSequence) throws XPathException {
        if (timing == null) {
            this.timing = new Timing();
        }
        timing.setStartTimestamp(System.nanoTime());
    }

    @Override
    public void after(final XQueryContext context, final Expression expression) throws XPathException {
        timing.setEndTimestamp(System.nanoTime());

        if (options.measurementMode == MeasurementMode.SINGLE) {
            logSingleMeasurement(expression);
            this.timing.reset();
        }
    }

    @Override
    public void resetState(final boolean postOptimization) {
        if (timing != null) {
            if (options.measurementMode == MeasurementMode.MULTIPLE) {
                logMultipleMeasurement();
            }
            this.timing.reset();
        }
    }

    /**
     * Return the nanoseconds as either
     * nanoseconds or milliseconds.
     *
     * @param nanoseconds the time in milliseconds.
     *
     * @return the time.
     */
    private static Tuple2<Long, String> nsOrMs(final long nanoseconds)  {
        final long timeValue;
        final String timeUnit;
        if (nanoseconds > 999_999) {
            timeValue = nanoseconds / 1_000_000;
            timeUnit = "ms";
        } else {
            timeValue = nanoseconds;
            timeUnit = "ns";
        }
        return Tuple(timeValue, timeUnit);
    }

    /**
     * Return the nanoseconds as either
     * nanoseconds or milliseconds.
     *
     * @param nanoseconds the time in milliseconds.
     *
     * @return the time.
     */
    private static String nsOrMsStr(final long nanoseconds)  {
        final Tuple2<Long, String> time = nsOrMs(nanoseconds);
        return String.format("%d %s", time._1, time._2);
    }

    /**
     * Return the nanoseconds as either
     * nanoseconds or milliseconds.
     *
     * @param nanoseconds the time in milliseconds.
     *
     * @return the time.
     */
    private static Tuple2<Double, String> nsOrMsDbl(final double nanoseconds)  {
        final double timeValue;
        final String timeUnit;
        if (nanoseconds > 999_999) {
            timeValue = nanoseconds / 1_000_000;
            timeUnit = "ms";
        } else {
            timeValue = nanoseconds;
            timeUnit = "ns";
        }
        return Tuple(timeValue, timeUnit);
    }

    /**
     * Return the nanoseconds as either
     * nanoseconds or milliseconds.
     *
     * @param nanoseconds the time in milliseconds.
     *
     * @return the time.
     */
    private static String nsOrMsStrDbl(final double nanoseconds)  {
        final Tuple2<Double, String> time = nsOrMsDbl(nanoseconds);
        return String.format("%.2f %s", time._1, time._2);
    }

    private void logSingleMeasurement(final Expression expression) {
        final long elapsedTime = timing.getTotalElapsed();
        @Nullable final String humaneElapsedTime = elapsedTime > (1_000_000 * 999) ? formatHumaneElapsedTime(elapsedTime) : null;

        final String displayTime = nsOrMsStr(elapsedTime);

        if (options.logger.isEnabled(options.loggingLevel)) {

            if (options.logMessagePrefix != null) {
                if (options.verbose) {
                    if (humaneElapsedTime != null) {
                        options.logger.log(options.loggingLevel, "{} Elapsed: {} ({}) for expression: {}", options.logMessagePrefix, displayTime, humaneElapsedTime, ExpressionDumper.dump(expression));
                    } else {
                        options.logger.log(options.loggingLevel, "{} Elapsed: {} for expression: {}", options.logMessagePrefix, displayTime, ExpressionDumper.dump(expression));
                    }
                } else {
                    if (humaneElapsedTime != null) {
                        options.logger.log(options.loggingLevel, "{} Elapsed: {} ({}).", options.logMessagePrefix, displayTime, humaneElapsedTime);
                    } else {
                        options.logger.log(options.loggingLevel, "{} Elapsed: {}.", options.logMessagePrefix, displayTime);
                    }
                }
            } else {
                if (options.verbose) {
                    if (humaneElapsedTime != null) {
                        options.logger.log(options.loggingLevel, "Elapsed: {} ({}) for expression: {}", displayTime, humaneElapsedTime, ExpressionDumper.dump(expression));
                    } else {
                        options.logger.log(options.loggingLevel, "Elapsed: {} for expression: {}", displayTime, ExpressionDumper.dump(expression));
                    }
                } else {
                    if (humaneElapsedTime != null) {
                        options.logger.log(options.loggingLevel, "Elapsed: {} ({}).", displayTime, humaneElapsedTime);
                    } else {
                        options.logger.log(options.loggingLevel, "Elapsed: {}.", displayTime);
                    }
                }
            }
        }
    }

    private void logMultipleMeasurement() {
        final long elapsedTime = timing.getTotalElapsed();
        @Nullable final String humaneElapsedTime = elapsedTime > 999 ? formatHumaneElapsedTime(elapsedTime) : null;

        final String displayTime = nsOrMsStr(elapsedTime);

        if (options.logMessagePrefix != null) {
            if (humaneElapsedTime != null) {
                options.logger.log(options.loggingLevel, "{} Elapsed: {} ({}) [iterations={} first={}, min={}, avg={}, max={}, last={}].", options.logMessagePrefix, displayTime, humaneElapsedTime, timing.getIterations(), nsOrMsStr(timing.getFirstElapsed()), nsOrMsStr(timing.getMinElapsed()), nsOrMsStrDbl(timing.getAvgElapsed()), nsOrMsStr(timing.getMaxElapsed()), nsOrMsStr(timing.getLastElapsed()));
            } else {
                options.logger.log(options.loggingLevel, "{} Elapsed: {} [iterations={} first={}, min={}, avg={}, max={}, last={}].", options.logMessagePrefix, displayTime, timing.getIterations(), nsOrMsStr(timing.getFirstElapsed()), nsOrMsStr(timing.getMinElapsed()), nsOrMsStrDbl(timing.getAvgElapsed()), nsOrMsStr(timing.getMaxElapsed()), nsOrMsStr(timing.getLastElapsed()));
            }
        } else {
            if (humaneElapsedTime != null) {
                options.logger.log(options.loggingLevel, "Elapsed: {} ({}) [iterations={} first={}, min={}, avg={}, max={}, last={}].", displayTime, humaneElapsedTime, timing.getIterations(), nsOrMsStr(timing.getFirstElapsed()), nsOrMsStr(timing.getMinElapsed()), nsOrMsStrDbl(timing.getAvgElapsed()), nsOrMsStr(timing.getMaxElapsed()), nsOrMsStr(timing.getLastElapsed()));
            } else {
                options.logger.log(options.loggingLevel, "Elapsed: {} [iterations={} first={}, min={}, avg={}, max={}, last={}].", displayTime, timing.getIterations(), nsOrMsStr(timing.getFirstElapsed()), nsOrMsStr(timing.getMinElapsed()), nsOrMsStrDbl(timing.getAvgElapsed()), nsOrMsStr(timing.getMaxElapsed()), nsOrMsStr(timing.getLastElapsed()));
            }
        }
    }

    /**
     * Format the elapsed time in a humane manner.
     *
     * @param elapsedTime the elapsed time in nanoseconds.
     *
     * @return a string of the duration which is suitable for consumptions by humans.
     */
    private static String formatHumaneElapsedTime(final long elapsedTime) {
        final long nanoseconds = elapsedTime % 1_000_000;

        final double ms = elapsedTime / 1_000_000;
        final long milliseconds = (long)(ms % 1_000);

        final double s = ms / 1_000;
        final long seconds = (long)(s % 60);

        final double m = s / 60;
        final long minutes = (long)(m % 60);

        final double h = m / 60;
        final long hours = (long)(h % 60);

        final StringBuilder humane = new StringBuilder();

        if (hours > 0) {
            humane.append(hours).append(" hour");
            if (hours > 1) {
                humane.append('s');
            }
        }

        if (minutes > 0) {
            if (!humane.isEmpty()) {
                humane.append(", ");
                if (seconds == 0 && milliseconds == 0 && nanoseconds == 0) {
                    humane.append("and ");
                }
            }

            humane.append(minutes).append(" minute");
            if (minutes > 1) {
                humane.append('s');
            }
        }

        if (seconds > 0) {
            if (!humane.isEmpty()) {
                humane.append(", ");
                if (milliseconds == 0 && nanoseconds == 0) {
                    humane.append("and ");
                }
            }

            humane.append(seconds).append(" second");
            if (seconds > 1) {
                humane.append('s');
            }
        }

        if (milliseconds > 0) {
            if (!humane.isEmpty()) {
                humane.append(", ");
                if (nanoseconds == 0) {
                    humane.append("and ");
                }
            }

            humane.append(milliseconds).append(" ms");
        }

        if (nanoseconds > 0) {
            if (!humane.isEmpty()) {
                humane.append(", and ");
            }

            humane.append(nanoseconds).append(" ns");
        }

        return humane.toString();
    }

    /**
     * Extract any options for the TimePragma from the Pragma Contents.
     *
     * @param contents the pragma contents.
     *
     * @return the options.
     */
    private static Options parseOptions(@Nullable final String contents) throws XPathException {
        boolean verbose = false;
        @Nullable String loggerName = null;
        @Nullable String loggingLevelName = null;
        @Nullable String logMessagePrefix = null;
        @Nullable String measurementModeStr = null;

        if (contents != null && !contents.isEmpty()) {
            final String[] options = Option.tokenize(contents);
            for (final String option : options) {
                @Nullable final String[] param = Option.parseKeyValuePair(option);
                if (param == null) {
                    throw new XPathException((Expression) null, "Invalid content found for pragma " + TIME_PRAGMA.getStringValue() + ": " + contents);
                }

                switch (param[0]) {
                    case "verbose":
                        verbose = "true".equals(param[1]) || "yes".equals(param[1]);
                        break;

                    case "logger-name":
                        loggerName = param[1];
                        break;

                    case "logging-level":
                        loggingLevelName = param[1];
                        break;

                    case "log-message-prefix":
                        logMessagePrefix = param[1];
                        break;

                    case "measurement-mode":
                        measurementModeStr = param[1];
                        break;
                }
            }
        }

        final Logger logger = Optional.ofNullable(loggerName).flatMap(s -> Optional.ofNullable(LogManager.getLogger(s))).orElseGet(() -> LogManager.getLogger(TimePragma.class));
        final Level loggingLevel = Optional.ofNullable(loggingLevelName).flatMap(s -> Optional.ofNullable(Level.getLevel(s))).orElse(Level.TRACE);
        final MeasurementMode measurementMode = Optional.ofNullable(measurementModeStr).map(String::toUpperCase).map(s -> {
            try {
                return MeasurementMode.valueOf(s);
            } catch (final IllegalArgumentException e) {
                return MeasurementMode.SINGLE;
            }
        }).orElse(MeasurementMode.SINGLE);

        return new Options(verbose, logger, loggingLevel, logMessagePrefix, measurementMode);
    }

    /**
     * Holds the options for the Timer Pragma.
     */
    private static class Options {
        final boolean verbose;
        final Logger logger;
        final Level loggingLevel;
        @Nullable final String logMessagePrefix;
        final MeasurementMode measurementMode;

        private Options(final boolean verbose, final Logger logger, final Level loggingLevel, @Nullable final String logMessagePrefix, final MeasurementMode measurementMode) {
            this.verbose = verbose;
            this.logger = logger;
            this.loggingLevel = loggingLevel;
            this.logMessagePrefix = logMessagePrefix;
            this.measurementMode = measurementMode;
        }
    }

    /**
     * The mode of measurement.
     */
    private enum MeasurementMode {
        SINGLE,
        MULTIPLE
    }

    /**
     * Holds the timings for multiple iterations of the Pragma's additional expression.
     */
    private static class Timing {
        private static final int UNSET = -1;

        private long startTimestamp = UNSET;

        private long lastStartTimestamp = UNSET;

        private long firstElapsed = UNSET;
        private long minElapsed = UNSET;
        private long maxElapsed = UNSET;
        private long lastElapsed = UNSET;
        private long totalElapsed = 0;

        private int iterations = 0;

        /**
         * Set the start timestamp of an iteration.
         *
         * @param startTimestamp the starting timestamp of an iteration.
         */
        public void setStartTimestamp(final long startTimestamp) {
            if (this.startTimestamp == UNSET) {
                this.startTimestamp = startTimestamp;
            }
            this.lastStartTimestamp = startTimestamp;
        }

        /**
         * Set the end timestamp of an iteration.
         *
         * @param endTimestamp the end timestamp of an iteration.
         */
        public void setEndTimestamp(final long endTimestamp) {
            this.iterations++;

            final long elapsed = endTimestamp - lastStartTimestamp;
            if (firstElapsed == UNSET) {
                this.firstElapsed = elapsed;
            }
            if (minElapsed == UNSET || elapsed < minElapsed) {
                minElapsed = elapsed;
            }
            if (elapsed > maxElapsed) {
                maxElapsed = elapsed;
            }
            this.lastElapsed = elapsed;
            this.totalElapsed += elapsed;
        }

        /**
         * Get the number of iterations that have been recorded.
         *
         * @return the number of iterations
         */
        public int getIterations() {
            return iterations;
        }

        /**
         * Get the elapsed time of the first iteration.
         *
         * @return the elapsed time of the first iteration.
         */
        public long getFirstElapsed() {
            return firstElapsed;
        }

        /**
         * Get the elapsed time of the shortest iteration, i.e. the least time spent on an iteration.
         *
         * @return the elapsed time of the shortest iteration.
         */
        public long getMinElapsed() {
            return minElapsed;
        }

        /**
         * Get the average elapsed time of all iterations.
         *
         * @return the average elapsed time of all iterations.
         */
        public double getAvgElapsed() {
            return getTotalElapsed() / (double) iterations;
        }

        /**
         * Get the elapsed time of the longest iteration, i.e. the most time spent on an iteration.
         *
         * @return the elapsed time of the longest iteration.
         */
        public long getMaxElapsed() {
            return maxElapsed;
        }

        /**
         * Get the elapsed time of the last iteration.
         *
         * @return the elapsed time of the last iteration.
         */
        public long getLastElapsed() {
            return lastElapsed;
        }

        /**
         * Get the total elapsed time of all iterations.
         *
         * @return the total elapsed time of all iterations.
         */
        public long getTotalElapsed() {
            return totalElapsed;
        }

        /**
         * Reset the class for next use.
         */
        public void reset() {
            this.startTimestamp = UNSET;

            this.lastStartTimestamp = UNSET;

            this.firstElapsed = UNSET;
            this.minElapsed = UNSET;
            this.maxElapsed = UNSET;
            this.lastElapsed = UNSET;
            this.totalElapsed = 0;

            this.iterations = 0;
        }
    }
}
