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

/**
 * Helper class for validation (error) messages.
 */
public class ValidationReportItem {

    public static final int WARNING = 1;
    public static final int ERROR = 2;
    public static final int FATAL = 4;

    private int type = -1;
    private int lineNumber = -1;
    private int columnNumber = -1;
    private String publicId;
    private String systemId;
    private String message = "";
    private int repeat = 1;

    public int getType() {
        return type;
    }

    public void setType(final int type) {
        this.type = type;
    }

    public int getLineNumber() {
        return this.lineNumber;
    }

    public void setLineNumber(final int nr) {
        this.lineNumber = nr;
    }

    public int getColumnNumber() {
        return this.columnNumber;
    }

    public void setColumnNumber(final int nr) {
        this.columnNumber = nr;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public String getPublicId() {
        return this.publicId;
    }

    public void setPublicId(final String publicId) {
        this.publicId = publicId;
    }

    public String getSystemId() {
        return this.systemId;
    }

    public void setSystemId(final String systemId) {
        this.systemId = systemId;
    }

    public String getTypeText() {

        return switch (type) {
            case WARNING -> "Warning";
            case ERROR -> "Error";
            case FATAL -> "Fatal";
            default -> "Unknown Error type";
        };
    }

    public String toString() {

        final String reportType = getTypeText();

        return reportType
                + " (" + lineNumber + "," + columnNumber + ") : " + message;
    }

    public void increaseRepeat() {
        repeat++;
    }

    public int getRepeat() {
        return repeat;
    }
}

