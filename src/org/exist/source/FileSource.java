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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;


/**
 * @author wolf
 */
public class FileSource extends AbstractSource {

    private String filePath;
    private long lastModified;
    private String encoding;
    
    public FileSource(File file, String encoding) {
        this.filePath = file.getAbsolutePath();
        this.lastModified = file.lastModified();
        this.encoding = encoding;
    }
    
    /* (non-Javadoc)
     * @see org.exist.source.Source#getKey()
     */
    public Object getKey() {
        return filePath;
    }

    /* (non-Javadoc)
     * @see org.exist.source.Source#isValid()
     */
    public int isValid() {
        File f = new File(filePath);
        if(f.lastModified() > lastModified)
            return INVALID;
        else
            return VALID;
    }

    /* (non-Javadoc)
     * @see org.exist.source.Source#isValid(org.exist.source.Source)
     */
    public int isValid(Source other) {
        return INVALID;
    }

    /* (non-Javadoc)
     * @see org.exist.source.Source#getReader()
     */
    public Reader getReader() throws IOException {
        return new FileReader(new File(filePath));
    }

    /* (non-Javadoc)
     * @see org.exist.source.Source#getContent()
     */
    public String getContent() throws IOException {
        FileInputStream is = new FileInputStream(new File(filePath));
		try {
			Reader reader = new InputStreamReader(is, encoding);
			char[] chars = new char[1024];
			StringBuffer buf = new StringBuffer();
			int read;
			while((read = reader.read(chars)) > -1)
				buf.append(chars, 0, read);
			return buf.toString();
		} finally {
			is.close();
		}
    }

}
