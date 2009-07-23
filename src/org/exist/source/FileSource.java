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

import java.io.*;

import org.exist.storage.DBBroker;


/**
 * A source implementation reading from the file system.
 * 
 * @author wolf
 */
public class FileSource extends AbstractSource {

    private String filePath;
    private long lastModified;
    private String encoding;
    private boolean checkEncoding = false;
    
    public FileSource(File file, String encoding, boolean checkXQEncoding) {
        this.filePath = file.getAbsolutePath();
        this.lastModified = file.lastModified();
        this.encoding = encoding;
        this.checkEncoding = checkXQEncoding;
    }
    
    /* (non-Javadoc)
     * @see org.exist.source.Source#getKey()
     */
    public Object getKey() {
        return filePath;
    }
    
    public String getFilePath() {
    	return filePath;
    }

    /* (non-Javadoc)
     * @see org.exist.source.Source#isValid()
     */
    public int isValid(DBBroker broker) {
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
        checkEncoding();
        return new InputStreamReader(new FileInputStream(filePath), encoding);
    }

    public InputStream getInputStream() throws IOException {
        return new FileInputStream(filePath);
    }

    /* (non-Javadoc)
     * @see org.exist.source.Source#getContent()
     */
    public String getContent() throws IOException {
        checkEncoding();
        FileInputStream is = new FileInputStream(new File(filePath));
		try {
			Reader reader = new InputStreamReader(is, encoding);
			char[] chars = new char[1024];
			StringBuilder buf = new StringBuilder();
			int read;
			while((read = reader.read(chars)) > -1)
				buf.append(chars, 0, read);
			return buf.toString();
		} finally {
			is.close();
		}
    }
    
    private void checkEncoding() throws IOException {
        if (checkEncoding) {
            FileInputStream is = new FileInputStream(filePath);
            try {
                String checkedEnc = guessXQueryEncoding(is);
                if (checkedEnc != null)
                    encoding = checkedEnc;
            } finally {
                is.close();
            }
        }
    }
    
    public String toString() {
    	return filePath;
    }
}
