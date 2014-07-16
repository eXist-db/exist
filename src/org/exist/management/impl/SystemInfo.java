/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2014 The eXist-db Project
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
 */
package org.exist.management.impl;

import java.io.InputStreamReader;
import java.util.Locale;

import org.exist.SystemProperties;

/**
 * Class SystemInfo
 * 
 * @author wessels
 * @author ljo
 */
public class SystemInfo implements SystemInfoMBean {

    public SystemInfo() {
    }

    @Override
    public String getExistVersion() {
        return SystemProperties.getInstance().getSystemProperty("product-version","unknown");
    }

    @Override
    public String getExistBuild() {
        return SystemProperties.getInstance().getSystemProperty("product-build","unknown");
    }

    @Override
    public String getSvnRevision() {
        return getGitCommit();
    }

    @Override
    public String getGitCommit() {
        return SystemProperties.getInstance().getSystemProperty("git-commit", "unknown Git commit ID");
    }

    @Override
    public String getDefaultLocale() {
        return Locale.getDefault().toString();
    }

    @Override
    public String getDefaultEncoding() {
        return new InputStreamReader(System.in).getEncoding();
    }

    @Override
    public String getOperatingSystem() {
         return System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch");
    }
}
