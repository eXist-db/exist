/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceValidity;
import org.exist.storage.DBBroker;
import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;

/**
 * A source that wraps around a Cocoon source object.
 * 
 * @author wolf
 */
public class CocoonSource extends AbstractSource {

    private Source inputSource;
    private SourceValidity validity;
    
    private boolean checkEncoding = false;
    
    private String encoding = "UTF-8";
    
    /**
     * 
     */
    public CocoonSource(Source source, boolean checkXQEncoding) {
        inputSource = source;
        validity = inputSource.getValidity();
        checkEncoding = checkXQEncoding;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.source.Source#isValid()
     */
    public int isValid(DBBroker broker) {
    	if (validity == null) {
    		return UNKNOWN;
    	}
        int valid = validity.isValid();
        switch (valid) {
            case SourceValidity.UNKNOWN:
                return UNKNOWN;
            case SourceValidity.VALID:
                return VALID;
            default:
                return INVALID;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.source.Source#isValid(org.exist.source.Source)
     */
    public int isValid(org.exist.source.Source other) {
    	SourceValidity validityOther = ((CocoonSource) other).inputSource.getValidity();
    	if (validity == null || validityOther == null) {
    		// if one of the validity objects is null, we fall back to comparing the content
    		try {
				if (getContent().equals(((CocoonSource) other).getContent()))
					return VALID;
				else
					return INVALID;
			} catch (IOException e) {
				return UNKNOWN;
			}
    	} else {
	        int valid = validity.isValid(validityOther);
	        switch (valid) {
	            case SourceValidity.UNKNOWN:
	                return UNKNOWN;
	            case SourceValidity.VALID:
	                return VALID;
	            default:
	                return INVALID;
	        }
    	}
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.source.Source#getReader()
     */
    public Reader getReader() throws IOException {
        checkEncoding();
        InputStream is = inputSource.getInputStream();
        return new InputStreamReader(is, encoding);
    }

    public InputStream getInputStream() throws IOException {
        return inputSource.getInputStream();
    }

    public String getContent() throws IOException {
        checkEncoding();
        int len = (int) inputSource.getContentLength();

        ByteArrayOutputStream os;
        if (len == -1)
            os = new ByteArrayOutputStream();
        else
            os = new ByteArrayOutputStream(len);

        byte[] t = new byte[512];
        InputStream is = inputSource.getInputStream();
        int count = 0;
        while ((count = is.read(t)) != -1) {
            os.write(t, 0, count);
        }
        return os.toString(encoding);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.source.Source#getKey()
     */
    public Object getKey() {
        return inputSource.getURI();
    }

    public Source getWrappedSource() {
    	return inputSource;
    }
    
    private void checkEncoding() throws IOException {
        if (checkEncoding) {
            String checkedEnc = guessXQueryEncoding(inputSource.getInputStream());
            if (checkedEnc != null)
                encoding = checkedEnc;
        }
    }
}
