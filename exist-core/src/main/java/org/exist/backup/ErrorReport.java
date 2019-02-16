/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.backup;

import org.exist.xmldb.XmldbURI;


public class ErrorReport {
    public final static int INCORRECT_NODE_ID = 0;
    public final static int INCORRECT_NODE_TYPE = 1;
    public final static int NODE_HIERARCHY = 2;
    public final static int ACCESS_FAILED = 3;
    public final static int CHILD_COLLECTION = 4;
    public final static int RESOURCE_ACCESS_FAILED = 5;
    public final static int DOM_INDEX = 6;
    public final static int CONFIGURATION_FAILD = 7;

    public final static String[] ERRCODES = {
            "ERR_NODE_ID", "ERR_NODE_TYPE", "ERR_NODE_HIERARCHY", "ERR_ACCESS",
            "ERR_CHILD_COLLECTION", "RESOURCE_ACCESS_FAILED", "ERR_DOM_INDEX"
    };

    private final int code;

    private String message = null;

    private Throwable exception = null;

    public ErrorReport(final int code, final String message) {
        this.code = code;
        this.message = message;
    }


    public ErrorReport(final int code, final String message, final Throwable exception) {
        this.code = code;
        this.message = message;
        this.exception = exception;
    }

    public int getErrcode() {
        return (code);
    }


    public String getErrcodeString() {
        return (ERRCODES[code]);
    }


    public String getMessage() {
        return (message);
    }


    public void setMessage(final String message) {
        this.message = message;
    }


    public Throwable getException() {
        return (exception);
    }


    public void setException(final Throwable exception) {
        this.exception = exception;
    }


    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(ERRCODES[code]).append(":\n");

        if (message != null) {
            sb.append(message);
        }
        return (sb.toString());
    }

    public static class ResourceError extends ErrorReport {
        private int documentId = -1;

        public ResourceError(final int code, final String message) {
            super(code, message);
        }


        public ResourceError(final int code, final String message, final Throwable exception) {
            super(code, message, exception);
        }

        public int getDocumentId() {
            return (documentId);
        }


        public void setDocumentId(final int documentId) {
            this.documentId = documentId;
        }


        public String toString() {
            return (super.toString() + "\nDocument ID: " + documentId);
        }
    }


    public static class CollectionError extends ErrorReport {
        private int collectionId = -1;

        private XmldbURI collectionURI = null;

        public CollectionError(final int code, final String message) {
            super(code, message);
        }


        public CollectionError(final int code, final String message, final Throwable exception) {
            super(code, message, exception);
        }

        public int getCollectionId() {
            return (collectionId);
        }


        public void setCollectionId(final int collectionId) {
            this.collectionId = collectionId;
        }

        public XmldbURI getCollectionURI() {
            return (collectionURI);
        }

        public void setCollectionURI(final XmldbURI collectionURI) {
            this.collectionURI = collectionURI;
        }

        public String toString() {
            return (super.toString() + "\nCollection ID: " + collectionId);
        }
    }


    public static class IndexError extends ErrorReport {
        private int documentId = -1;

        public IndexError(final int code, final String message, final int documentId) {
            super(code, message);
            this.documentId = documentId;
        }


        public IndexError(final int code, final String message, final Throwable exception, final int documentId) {
            super(code, message, exception);
            this.documentId = documentId;
        }

        public int getDocumentId() {
            return (documentId);
        }


        public String toString() {
            return (super.toString() + "\nDocument ID: " + documentId);
        }
    }
}
