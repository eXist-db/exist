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

package org.exist.test.runner;

import org.apache.commons.lang3.StringUtils;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.junit.jupiter.api.AssertionFailureBuilder;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Properties;

import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.params;

public class ExtTestFailureFunction extends JUnitIntegrationFunction {

    /** the most characters of an expected or an actual value that go into a failure */
    static final int MAX_VALUE_LENGTH = 4000;

    @Nullable
    private final Path sourcePath;

    public ExtTestFailureFunction(final XQueryContext context, final String parentName, final TestEvents events) {
        this(context, parentName, events, null);
    }

    public ExtTestFailureFunction(final XQueryContext context, final String parentName, final TestEvents events, @Nullable final Path sourcePath) {
        super("ext-test-failure-function",
                params(
                        param("name", Type.STRING, "name of the test"),
                        param("expected", Type.MAP_ITEM, "expected result of the test"),
                        param("actual", Type.MAP_ITEM, "actual result of the test")
                ), context, parentName, events);
        this.sourcePath = sourcePath;
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        final Sequence arg1 = getCurrentArguments()[0];
        final String name = arg1.itemAt(0).getStringValue();

        final Sequence arg2 = getCurrentArguments()[1];
        final MapType expected = (MapType)arg2.itemAt(0);

        final Sequence arg3 = getCurrentArguments()[2];
        final MapType actual = (MapType)arg3.itemAt(0);


        // notify JUnit
        try {
            final String fileName = getFileNameFromActual(actual);
            final int lineNumber = getLineFromActual(actual);
            // Short one-line for logs (filename only)
            final String shortFileName = fileName != null ? lastPathSegment(fileName) : null;
            final String shortLocation = shortFileName != null ? shortFileName + (lineNumber > 0 ? ":" + lineNumber : "") : null;
            final String oneLine = "XQuery failure: " + (shortLocation != null ? shortLocation + " " : "") + name;
            XQueryFailureLog.log(oneLine);

            // The builder puts the two values into the message too ("... ==> expected: <x> but was: <y>"),
            // because the surefire report and the console keep only the message and the stack trace, and drop
            // the values that an AssertionFailedError carries separately (which only an IDE shows).
            final AssertionError failureReason = AssertionFailureBuilder.assertionFailure()
                    .message(oneLine)
                    .expected(abbreviate(expectedToString(expected)))
                    .actual(abbreviate(actualToString(actual)))
                    .build();

            // Stack trace for IDE navigation. IntelliJ linkifies short "filename:line" in stack traces
            // but not absolute paths; use short filename so the stack line becomes clickable.
            if (shortFileName != null) {
                failureReason.setStackTrace(new StackTraceElement[]{
                    new StackTraceElement(" ", " ", shortFileName, lineNumber > 0 ? lineNumber : 1)
                });
            } else {
                failureReason.setStackTrace(new StackTraceElement[0]);
            }

            events.failed(name, failureReason);
        } catch (final XPathException | SAXException | IOException | IllegalStateException e) {
            //signal internal failure
            events.failed(name, e);
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    /**
     * @param value an expected or actual value as a string
     *
     * @return the value, or its start and how long it was in all if it is longer than {@link #MAX_VALUE_LENGTH},
     * so that one very large result cannot flood the report
     */
    static String abbreviate(final String value) {
        if (value.length() <= MAX_VALUE_LENGTH) {
            return value;
        }
        return StringUtils.abbreviate(value, "... [truncated, " + value.length() + " characters in all]", MAX_VALUE_LENGTH);
    }

    /**
     * Last path segment for short display in failure message and stack trace (short name makes IDE stack trace link clickable).
     */
    private static String lastPathSegment(final String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        try {
            final Path p = Path.of(path);
            return p.getFileName() != null ? p.getFileName().toString() : path;
        } catch (final Exception ignored) {
            return path;
        }
    }

    /** Source from inspect can be full path or short identifier (implementation-dependent); we use last segment for IDE links. */
    private String getFileNameFromActual(final MapType actual) throws XPathException {
        final Sequence seqSource = actual.get(new StringValue(this, "source"));
        if (!seqSource.isEmpty()) {
            final String s = seqSource.itemAt(0).getStringValue();
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        if (sourcePath != null) {
            return sourcePath.getFileName() != null ? sourcePath.getFileName().toString() : sourcePath.toString();
        }
        return null;
    }

    private int getLineFromActual(final MapType actual) throws XPathException {
        final Sequence seqLine = actual.get(new StringValue(this, "line"));
        if (!seqLine.isEmpty()) {
            final Item item = seqLine.itemAt(0);
            if (item instanceof IntegerValue value) {
                return (int) value.getLong();
            }
            try {
                return Integer.parseInt(item.getStringValue());
            } catch (final NumberFormatException ignored) {
                // fall through to 0
            }
        }
        return 0;
    }

    private String expectedToString(final MapType expected) throws XPathException {
        final Sequence seqExpectedValue = expected.get(new StringValue(this, "value"));
        if(!seqExpectedValue.isEmpty()) {
            return seqToString(seqExpectedValue);
        }

        final Sequence seqExpectedXPath = expected.get(new StringValue(this, "xpath"));
        if(!seqExpectedXPath.isEmpty()) {
            return "XPath: " + seqToString(seqExpectedXPath);
        }

        final Sequence seqExpectedError = expected.get(new StringValue(this, "error"));
        if(!seqExpectedError.isEmpty()) {
            return "Error: " + seqToString(seqExpectedError);
        }

        throw new IllegalStateException("Could not extract expected value");
    }

    private String actualToString(final MapType actual) throws XPathException, SAXException, IOException {
        final Sequence seqActualError = actual.get(new StringValue(this, "error"));
        if (!seqActualError.isEmpty()) {
            return errorMapToString(seqActualError);
        }

        final Sequence seqActualResult = actual.get(new StringValue(this, "result"));
        if (!seqActualResult.isEmpty()) {
            return seqToString(seqActualResult);
        } else {
            return "";  // empty-sequence()
        }
    }

    /**
     * The values reaching here have already been serialized to strings by the XQSuite layer
     * (xqsuite.xql's test:expected-strings / test:actual-strings, and the adaptive serialize()
     * calls beside them), so they are taken verbatim. Running them through a serializer a second
     * time would escape the markup they contain, and a result of {@code <doc/>} would reach the
     * failure message as {@code &lt;doc/&gt;}.
     *
     * @param seq the already-serialized value(s) to render into the failure message
     *
     * @return the concatenated string values
     *
     * @throws XPathException if a string value cannot be obtained
     */
    private String seqToString(final Sequence seq) throws XPathException {
        final StringBuilder builder = new StringBuilder();
        for (final SequenceIterator it = seq.iterate(); it.hasNext(); ) {
            builder.append(it.nextItem().getStringValue());
        }
        return builder.toString();
    }

    private String errorMapToString(final Sequence seqErrorMap) throws IOException, XPathException, SAXException {
        try(final StringWriter writer = new StringWriter()) {
            final Properties properties = new Properties();
            properties.setProperty(OutputKeys.METHOD, "adaptive");

            final XQuerySerializer xquerySerializer = new XQuerySerializer(context.getBroker(), properties, writer);
            xquerySerializer.serialize(seqErrorMap);
            return writer.toString();
        }
    }
}
