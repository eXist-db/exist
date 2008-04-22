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
package org.exist.storage.repair;

public class ErrorReport {

    public final static int INCORRECT_NODE_ID = 0;
    public final static int INCORRECT_NODE_TYPE = 1;
    public final static int NODE_HIERARCHY = 2;
    public final static int ACCESS_FAILED = 3;

    public final static String[] ERRCODES = {
        "ERR_NODE_ID", "ERR_NODE_TYPE", "ERR_NODE_HIERARCHY", "ERR_ACCESS"
    };
    
    private int code;

    private String message = null;

    private Throwable exception = null;
    
    private int documentId = -1;

    private int collectionId = -1;

    public ErrorReport(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public ErrorReport(int code, String message, Throwable exception) {
        this.code = code;
        this.message = message;
        this.exception = exception;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    public int getDocumentId() {
        return documentId;
    }

    public void setDocumentId(int documentId) {
        this.documentId = documentId;
    }

    public int getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(int collectionId) {
        this.collectionId = collectionId;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(ERRCODES[code]).append(": ");
        if (message != null)
            sb.append(message);
        sb.append("\r\n");
        sb.append("Collection ID: ").append(collectionId).append("\r\n");
        sb.append("Document ID: ").append(documentId);
        return sb.toString();
    }
}