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

import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.XarSource;
import org.expath.pkg.repo.deps.DependencyVersion;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;

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

        public DependencyVersion getDependencyVersion() throws PackageException {
            return DependencyVersion.makeVersion(version, semVer, min, max);
        }

        public String toString() {
            StringBuilder v = new StringBuilder();
            if (min != null) {
                v.append("> ").append(min);
            }
            if (max != null) {
                v.append(" < ").append(max);
            }
            if (semVer != null) {
                v.append(semVer);
            }
            if (version != null) {
                v.append(version);
            }
            return v.toString();
        }

        public Version(String version, boolean semver) {
            if (semver)
                {this.semVer = version;}
            else
                {this.version = version;}
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
     * @throws IOException in case of an io error locating the package
     */
    @Nullable
    XarSource load(String name, Version version) throws IOException;
}
