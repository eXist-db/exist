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

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

import org.exist.ExistSystemProperties;

/**
 * Class SystemInfo
 * 
 * @author wessels
 * @author ljo
 */
public class SystemInfo implements SystemInfoMXBean {

    public SystemInfo() {
    }

    @Override
    public String getExistVersion() {
        return ExistSystemProperties.getInstance().getExistSystemProperty(ExistSystemProperties.PROP_PRODUCT_VERSION,"unknown");
    }

    @Override
    public String getExistBuild() {
        return ExistSystemProperties.getInstance().getExistSystemProperty(ExistSystemProperties.PROP_PRODUCT_BUILD,"unknown");
    }

    @Override
    public String getSvnRevision() {
        return getGitCommit();
    }

    @Override
    public String getGitCommit() {
        return ExistSystemProperties.getInstance().getExistSystemProperty(ExistSystemProperties.PROP_GIT_COMMIT, "unknown Git commit ID");
    }

    @Override
    public String getDefaultLocale() {
        return Locale.getDefault().toString();
    }

    @Override
    public String getDefaultEncoding() {
        try(final InputStreamReader isr = new InputStreamReader(System.in)) {
            return isr.getEncoding();
        } catch(final IOException ioe) {
            ioe.printStackTrace();
            return null;
        }
    }

    @Override
    public String getOperatingSystem() {
         return System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch");
    }
}
