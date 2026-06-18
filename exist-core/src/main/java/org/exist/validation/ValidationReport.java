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
package org.exist.validation;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Report containing all validation info (errors, warnings).
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 * @see org.xml.sax.ErrorHandler
 */
public class ValidationReport implements ErrorHandler {

    private final List<ValidationReportItem> validationReport = new ArrayList<>();

    private ValidationReportItem lastItem;

    private long duration = -1L;
    private long start = -1L;

    private Throwable throwed = null;
    private String namespaceUri = null;

    private ValidationReportItem createValidationReportItem(final int type, final SAXParseException exception) {

        final ValidationReportItem vri = new ValidationReportItem();
        vri.setType(type);
        vri.setLineNumber(exception.getLineNumber());
        vri.setColumnNumber(exception.getColumnNumber());
        vri.setMessage(exception.getMessage());
        vri.setPublicId(exception.getPublicId());
        vri.setSystemId(exception.getSystemId());
        return vri;
    }

    private void addItem(final ValidationReportItem newItem) {
        if (lastItem == null) {
            // First reported item
            validationReport.add(newItem);
            lastItem = newItem;

        } else if (lastItem.getMessage().equals(newItem.getMessage())) {
            // Message is repeated
            lastItem.increaseRepeat();

        } else {
            // Received new message
            validationReport.add(newItem);

            // Swap reported item
            lastItem = newItem;
        }
    }

    /**
     * Receive notification of a recoverable error.
     *
     * @param exception The warning information encapsulated in a
     *                  SAX parse exception.
     * @throws SAXException Any SAX exception, possibly wrapping another
     *                      exception.
     */
    public void error(final SAXParseException exception) throws SAXException {
        addItem(createValidationReportItem(ValidationReportItem.ERROR, exception));
    }

    /**
     * Receive notification of a non-recoverable error.
     *
     * @param exception The warning information encapsulated in a
     *                  SAX parse exception.
     * @throws SAXException Any SAX exception, possibly wrapping another
     *                      exception.
     */
    public void fatalError(final SAXParseException exception) throws SAXException {
        addItem(createValidationReportItem(ValidationReportItem.FATAL, exception));
    }

    /**
     * Receive notification of a warning.
     *
     * @param exception The warning information encapsulated in a
     *                  SAX parse exception.
     * @throws SAXException Any SAX exception, possibly wrapping another
     *                      exception.
     */
    public void warning(final SAXParseException exception) throws SAXException {
        addItem(createValidationReportItem(ValidationReportItem.WARNING, exception));
    }


    public void setException(final Throwable ex) {
        this.throwed = ex;
    }

    /**
     * Give validation information of the XML document.
     *
     * @return FALSE if no errors and warnings occurred.
     */
    public boolean isValid() {
        return (validationReport.isEmpty() && (throwed == null));
    }

    public List<ValidationReportItem> getValidationReportItemList() {
        return validationReport;
    }

    /**
     * Discard previously recorded errors/warnings so the report can be
     * reused for a second validation pass (e.g. retrying with a different
     * validator), while keeping start/duration/namespace tracking intact.
     */
    public void clear() {
        validationReport.clear();
        lastItem = null;
    }

    public List<String> getTextValidationReport() {

        final List<String> textReport = new ArrayList<>();

        if (isValid()) {
            textReport.add("Document is valid.");
        } else {
            textReport.add("Document is not valid.");
        }

        if (throwed != null) {
            textReport.add("Exception: " + throwed.getMessage());
        }

        textReport.addAll(validationReport.stream().map(ValidationReportItem::toString).toList());

        textReport.add("Validated in " + duration + " millisec.");
        return textReport;
    }

    public String[] getValidationReportArray() {

        final List<String> vr = getTextValidationReport();
        final String[] report = new String[vr.size()];

        return vr.toArray(report);
    }

    public long getValidationDuration() {
        return duration;
    }

    public void setValidationDuration(final long time) {
        duration = time;
    }

    @Override
    public String toString() {

        final StringBuilder sb = new StringBuilder();

        for (final String line : getTextValidationReport()) {
            sb.append(line);
            sb.append("\n");
        }

        return sb.toString();
    }

    public void start() {
        start = System.currentTimeMillis();
    }

    public void stop() {
        if (getValidationDuration() == -1L) { // not already stopped
            final long stop = System.currentTimeMillis();
            setValidationDuration(stop - start);
        }
    }

    public Throwable getThrowable() {
        return throwed;
    }

    public void setThrowable(final Throwable throwable) {
        throwed = throwable;
    }

    public String getNamespaceUri() {
        return namespaceUri;
    }

    public void setNamespaceUri(final String namespace) {
        namespaceUri = namespace;
    }

    public String getStackTrace() {

        if (throwed == null) {
            return null;
        }

        final UnsynchronizedByteArrayOutputStream baos = UnsynchronizedByteArrayOutputStream.builder().get();
        final PrintStream ps = new PrintStream(baos);
        throwed.printStackTrace(ps);
        return baos.toString(UTF_8);
    }
}
