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

package org.exist.validation;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 *  A set of helper methods for the validation tests.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class TestTools {
    
    public final static String VALIDATION_HOME="/db/validationtest";
    public final static String VALIDATION_DTD=VALIDATION_HOME+"/dtd";
    public final static String VALIDATION_XSD=VALIDATION_HOME+"/xsd";
    public final static String VALIDATION_TMP=VALIDATION_HOME+"/tmp";
    
    public TestTools() {
        // --
    }
    
    // Transfer bytes from in to out
    public static void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[1024];
        int len;
        while ((len = is.read(buf)) > 0) {
            os.write(buf, 0, len);
        }
    }
    
    /**
     *
     * @param file     File to be uploaded
     * @param target  Target URL (e.g. xmldb:exist:///db/collection/document.xml)
     * @throws java.lang.Exception  Oops.....
     */
    static void insertDocumentToURL(String file, String target) throws Exception{
        
        InputStream is = new FileInputStream(file);
        
        URL url = new URL(target);
        URLConnection connection = url.openConnection();
        OutputStream os = connection.getOutputStream();
        
        TestTools.copyStream(is, os);
        
        is.close();
        os.close();
    }
    
}
