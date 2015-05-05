/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
 *  $Id$
 */
package org.exist.validation.resolver.unstable;

import java.io.InputStream;
import java.io.Reader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.ls.LSInput;

/**
 * eXistLSInput provides a way for applications to redirect
 * references to external resource.
 * 
 * @author Dizzzz (dizzzz@exist-db.org)
 */
public class eXistLSInput implements LSInput {

    @SuppressWarnings("unused")
	private final static Logger LOG = LogManager.getLogger(eXistLSInput.class);

    private Reader characterStream;

    public Reader getCharacterStream() {
        return characterStream;
    }

    public void setCharacterStream(Reader characterStream) {
        this.characterStream=characterStream;
    }

    private InputStream byteStream;

    public InputStream getByteStream() {
        return byteStream;
    }

    public void setByteStream(InputStream byteStream) {
        this.byteStream=byteStream;
    }


    private String stringData;

    public String getStringData() {
        return stringData;
    }

    public void setStringData(String stringData) {
        this.stringData=stringData;
    }


    private String systemId;

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId=systemId;
    }


    private String publicId;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId=publicId;
    }


    private String baseURI;

    public String getBaseURI() {
        return baseURI;
    }

    public void setBaseURI(String baseURI) {
        this.baseURI=baseURI;
    }


    private String encoding;

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding=encoding;
    }


    private boolean certifiedText=false;

    public boolean getCertifiedText() {
        return certifiedText;
    }

    public void setCertifiedText(boolean certifiedText) {
        this.certifiedText=certifiedText;
    }


}
