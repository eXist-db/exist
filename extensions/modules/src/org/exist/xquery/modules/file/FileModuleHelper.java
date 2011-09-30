/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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
package org.exist.xquery.modules.file;

import java.io.File;
import java.net.URI;
import org.exist.xquery.XPathException;

/**
 *  Helper class for FileModule
 * 
 * @author Dannes Wessels (dannes@exist-db.org)
 */


public class FileModuleHelper {

    private FileModuleHelper() {
        // no instance
    }
    
    /**
     *  Convert path (URL, file path) to a File object.
     * 
     * @param path Path written as OS specific path or as URL
     * @return File object
     * @throws XPathException Thrown when the URL cannot be used.
     */
    public static File getFile(String path) throws XPathException{
        File baseDir = null;
        if(path.startsWith("file:")){
            try {
                baseDir = new File( new URI(path) );
            } catch (Exception ex) { // catch all (URISyntaxException)
                throw new XPathException(path + " is not a valid URI: '"+ ex.getMessage() +"'");
            }
        } else {
            baseDir = new File(path);
        }
        return baseDir;
    }
    
}
