/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2006-2010 The eXist Project
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
package org.exist.backup;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Properties;


/**
 * Helper interface for writing backups. Serves as an abstraction for writing to different targets like directories or zip files.
 */
public interface BackupWriter extends Closeable
{
    Writer newContents() throws IOException;


    void closeContents() throws IOException;


    OutputStream newEntry( String name ) throws IOException;


    OutputStream newBlobEntry(String blobId) throws IOException;


    void closeEntry() throws IOException;


    void newCollection( String name ) throws IOException;


    void closeCollection();


    void setProperties( Properties properties ) throws IOException;
}
