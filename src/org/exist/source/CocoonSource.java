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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceValidity;


/**
 * @author wolf
 */
public class CocoonSource extends AbstractSource {
    
    private Source inputSource;
    private SourceValidity validity;
    
    /**
     * 
     */
    public CocoonSource(Source source) {
        inputSource = source;
        validity = source.getValidity();
    }

    /* (non-Javadoc)
     * @see org.exist.source.Source#isValid()
     */
    public int isValid() {
        int valid = validity.isValid();
        switch(valid) {
            case SourceValidity.UNKNOWN:
                return UNKNOWN;
            case SourceValidity.VALID:
                return VALID;
            default:
                return INVALID;
        }
    }

    
    /* (non-Javadoc)
     * @see org.exist.source.Source#isValid(org.exist.source.Source)
     */
    public int isValid(org.exist.source.Source other) {
        int valid = validity.isValid(((CocoonSource)other).validity);
        switch(valid) {
            case SourceValidity.UNKNOWN:
                return UNKNOWN;
            case SourceValidity.VALID:
                return VALID;
            default:
                return INVALID;
        }
    }
    
    /* (non-Javadoc)
     * @see org.exist.source.Source#getReader()
     */
    public Reader getReader() throws IOException {
        InputStream is = inputSource.getInputStream();
        return new InputStreamReader(is, "UTF-8");
    }

    public String getContent() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream((int) inputSource
				.getContentLength());
		byte[] t = new byte[512];
		InputStream is = inputSource.getInputStream();
		int count = 0;
		while ((count = is.read(t)) != -1) {
			os.write(t, 0, count);
		}
		return os.toString("UTF-8");
    }
    
    /* (non-Javadoc)
     * @see org.exist.source.Source#getKey()
     */
    public Object getKey() {
        return inputSource.getURI();
    }

}
