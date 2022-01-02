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
package org.exist.util;


public class MimeType {
    
    public final static int XML = 0;
    public final static int BINARY = 1;
    
    public final static MimeType BINARY_TYPE =
        new MimeType("application/octet-stream", BINARY);
    
    public final static MimeType XML_TYPE =
        new MimeType("application/xml", XML);
    //public final static MimeType XML_APPLICATION_TYPE =
    //    new MimeType("application/xml", XML);
    public final static MimeType XML_CONTENT_TYPE =
        new MimeType("application/xml; charset=UTF-8", XML);
    public final static MimeType XML_LEGACY_TYPE =
    	new MimeType("text/xml", XML);
    public final static MimeType XSL_TYPE =
        new MimeType("text/xsl", XML); 
    public final static MimeType XSLT_TYPE =
        new MimeType("application/xslt+xml", XML);
    public final static MimeType XQUERY_TYPE =
        new MimeType("application/xquery", BINARY);
    public final static MimeType XPROC_TYPE =
        new MimeType("application/xml+xproc", XML);
    public final static MimeType CSS_TYPE =
        new MimeType("text/css", BINARY);
    public final static MimeType HTML_TYPE =
        new MimeType("text/html", BINARY);
    public final static MimeType TEXT_TYPE =
        new MimeType("text/plain", BINARY);
    public final static MimeType URL_ENCODED_TYPE =
    	new MimeType("application/x-www-form-urlencoded", BINARY);
    public final static MimeType EXPATH_PKG_TYPE =
        new MimeType("application/expath+xar", BINARY);


    private final String name;
    private String description;
    private final int type;
 
    public MimeType(final String name, final int type) {
        this.name = name;
        this.type = type;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getName() {
        return name;
    }
    public int getType() {
        return type;
    }
    
    public String getXMLDBType() {
        return isXMLType() ? "XMLResource" : "BinaryResource";
    }
    
    public boolean isXMLType() {
        return type == XML;
    }

    @Override
    public String toString() {
        return name + ": " + description;
    }
}