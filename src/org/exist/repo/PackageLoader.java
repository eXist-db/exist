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
import java.io.IOException;

/**
 * Interface for resolving package dependencies. Implementations may load
 * packages e.g. from a public server or the file system.
 */
public interface PackageLoader {

    /**
     * Wrapper for the different version schemes supported by
     * the expath spec.
     */
    public static class Version {

        String min = null;
        String max = null;
        String semVer = null;
        String version = null;

        public String getMin() {
            return min;
        }

        public String getMax() {
            return max;
        }

        public String getSemVer() {
            return semVer;
        }

        public String getVersion() {
            return version;
        }

        public Version(String version, boolean semver) {
            if (semver)
                this.semVer = version;
            else
                this.version = version;
        }

        public Version(String min, String max) {
            this.min = min;
            this.max = max;
        }
    }

    /**
     * Locate the expath package identified by name.
     *
     * @param name unique name of the package
     * @param version the version to install
     * @return a file containing the package or null if not found
     */
    public File load(String name, Version version) throws IOException;
}
