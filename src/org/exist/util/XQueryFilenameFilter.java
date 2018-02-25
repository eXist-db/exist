/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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

package org.exist.util;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

public class XQueryFilenameFilter implements FilenameFilter {

    public static final String MEDIA_TYPE_APPLICATION_XQUERY = "application/xquery";

    @Override
    public boolean accept(final File dir, final String name) {
        final MimeTable mimetab = MimeTable.getInstance();
        final MimeType mime = mimetab.getContentTypeFor(name);

        return mime != null && !mime.isXMLType() && mime.getName().equals(MEDIA_TYPE_APPLICATION_XQUERY);
    }

    public static Predicate<Path> asPredicate() {
        final MimeTable mimetab = MimeTable.getInstance();
        return path -> {
            if(!Files.isDirectory(path)) {
                final MimeType mime = mimetab.getContentTypeFor(FileUtils.fileName(path));
                return mime != null && !mime.isXMLType() && mime.getName().equals(MEDIA_TYPE_APPLICATION_XQUERY);
            }
            return false;
        };
    }
}
