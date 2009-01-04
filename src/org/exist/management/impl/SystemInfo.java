/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 The eXist Project
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
package org.exist.management.impl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Properties;

import org.apache.log4j.Logger;

/**
 * Class SystemInfo
 * 
 * @author wessels
 */
public class SystemInfo implements SystemInfoMBean {

    private final static Logger LOG = Logger.getLogger(SystemInfo.class);
    private Properties sysProperties = new Properties();

    public SystemInfo() {

        try {
            sysProperties.load(SystemInfo.class.getClassLoader().getResourceAsStream("org/exist/system.properties"));
        } catch (IOException e) {
            LOG.debug("Unable to load system.properties from class loader");
        }
    }

    public String getExistVersion() {
        return sysProperties.getProperty("product-version","unknown");
    }

    public String getExistBuild() {
        return sysProperties.getProperty("product-build","unknown");
    }

    public String getSvnRevision() {
        return sysProperties.getProperty("svn-revision","unknown");
    }

    public String getDefaultLocale() {
        return Locale.getDefault().toString();
    }

    public String getDefaultEncoding() {
        return new InputStreamReader(System.in).getEncoding();
    }

}


