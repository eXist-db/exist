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
package org.exist.xquery.modules.file;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

/**
 *  Helper class for FileModule
 * 
 * @author <a href="mailto:dannes@exist-db.org">Dannes Wessels</a>
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
    public static Path getFile(String path, final Expression expression) throws XPathException {
        if(path.startsWith("file:")){
            try {
                return Paths.get(new URI(path));
            } catch (Exception ex) { // catch all (URISyntaxException)
                throw new XPathException(expression, path + " is not a valid URI: '"+ ex.getMessage() +"'");
            }
        } else {
            return Paths.get(path);
        }
    }
    
}
