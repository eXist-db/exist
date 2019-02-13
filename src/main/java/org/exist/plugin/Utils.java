/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2012 The eXist Project
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
package org.exist.plugin;

import java.io.File;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Utils {

    public static boolean fileCanContainClasses(File file) {
        boolean can      = false;
        final String  fileName = file.getPath();

        if (file.exists()) {
            can = (isJar(fileName) ||
                   isZip(fileName) ||
                   file.isDirectory());
        }

        return can;
    }
    
    public static boolean isJar(String fileName) {
        return fileName.toLowerCase().endsWith(".jar");
    }

    public static boolean isZip(String fileName) {
        return fileName.toLowerCase().endsWith(".zip");
    }
}
