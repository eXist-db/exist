/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2015 The eXist-db Project
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
 *  $Id: Restore.java 15109 2011-08-09 13:03:09Z deliriumsky $
 */
package org.exist.backup.restore.listener;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class LogRestoreListener extends AbstractRestoreListener {

    public final static Logger LOG = LogManager.getLogger(LogRestoreListener.class );

    @Override
    public void info(String message) {
        LOG.info(message);
    }

    @Override
    public void warn(String message) {
        super.warn(message);
        LOG.warn(message);
    }

    @Override
    public void error(String message) {
        super.error(message);
        LOG.error(message);
    }
}