/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-12 The eXist Project
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
package org.exist.repo;

import java.io.File;

/**
 * Interface for resolving package dependencies. Implementations may load
 * packages e.g. from a public server or the file system.
 */
public interface PackageLoader {

    /**
     * Locate the expath package identified by name.
     *
     * @param name unique name of the package
     * @return a file containing the package or null if not found
     */
    public File load(String name);
}
