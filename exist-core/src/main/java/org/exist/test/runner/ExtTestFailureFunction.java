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

import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.junit.ComparisonFailure;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
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

    @Nullable
    private final Path sourcePath;

    public ExtTestFailureFunction(final XQueryContext context, final String parentName, final RunNotifier notifier) {
        this(context, parentName, notifier, null);
    }

    public ExtTestFailureFunction(final XQueryContext context, final String parentName, final RunNotifier notifier, @Nullable final Path sourcePath) {
        super("ext-test-failure-function",
                params(
                        param("name", Type.STRING, "name of the test"),
                        param("expected", Type.MAP_ITEM, "expected result of the test"),
                        param("actual", Type.MAP_ITEM, "actual result of the test")
                ), context, parentName, notifier);
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

        final Description description = createTestDescription(name);

        // notify JUnit
        try {
            final String fileName = getFileNameFromActual(actual);
            final int lineNumber = getLineFromActual(actual);
            // Short one-line for logs (filename only)
            final String shortFileName = fileName != null ? lastPathSegment(fileName) : null;
            final String shortLocation = shortFileName != null ? shortFileName + (lineNumber > 0 ? ":" + lineNumber : "") : null;
            String oneLine = "XQuery failure: " + (shortLocation != null ? shortLocation + " " : "") + name;
            if (shortFileName != null && lineNumber > 0) {
                oneLine += "\n\tat (" + shortFileName + ":" + lineNumber + ")";
            }
            XQueryFailureLog.log(oneLine);
            final AssertionError failureReason = new ComparisonFailure(oneLine, expectedToString(expected), actualToString(actual));

            // Stack trace for IDE navigation. IntelliJ linkifies short "filename:line" in stack traces
            // but not absolute paths; use short filename so the stack line becomes clickable.
            if (shortFileName != null) {
                failureReason.setStackTrace(new StackTraceElement[]{
                    new StackTraceElement(" ", " ", shortFileName, lineNumber > 0 ? lineNumber : 1)
                });
            } else {
                failureReason.setStackTrace(new StackTraceElement[0]);
            }

            notifier.fireTestFailure(new Failure(description, failureReason));
        } catch (final XPathException | SAXException | IOException | IllegalStateException e) {
            //signal internal failure
            notifier.fireTestFailure(new Failure(description, e));
        }

        return Sequence.EMPTY_SEQUENCE;
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

    private String expectedToString(final MapType expected) throws XPathException, SAXException, IOException {
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

    private String seqToString(final Sequence seq) throws IOException, XPathException, SAXException {
        try(final StringWriter writer = new StringWriter()) {
            final XQuerySerializer xquerySerializer = new XQuerySerializer(context.getBroker(), new Properties(), writer);
            xquerySerializer.serialize(seq);
            return writer.toString();
        }
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
