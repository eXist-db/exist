/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xslt;

import java.io.InputStream;
import java.io.Reader;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.xml.sax.InputSource;

/**
 * {@link org.xml.sax.InputSource} identifying a document within the eXist database.
 *
 * @author <a href="mailto:Paul.L.Merchant.Jr@dartmouth.edu">Paul Merchant, Jr.</a>
 */

public class EXistDbInputSource extends InputSource {
    private final DBBroker broker;
    private final DocumentImpl doc;
    
    public EXistDbInputSource(DBBroker broker, DocumentImpl doc) {
        super();
        
        this.broker = broker;
        this.doc = doc;
    }
    
    public DBBroker getBroker() {
        return this.broker;
    }
    
    public DocumentImpl getDocument() {
        return this.doc;
    }
    
    @Override
    public void setByteStream(InputStream stream) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCharacterStream(Reader stream) {
        throw new UnsupportedOperationException();
    }    
}
