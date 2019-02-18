/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xmldb;

import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import org.exist.scheduler.SystemTaskJob;
import org.exist.scheduler.impl.ShutdownTask;
import org.exist.scheduler.impl.SystemTaskJobImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * Local implementation of the DatabaseInstanceManager.
 */
public class LocalDatabaseInstanceManager extends AbstractLocalService implements DatabaseInstanceManager {
	
    public LocalDatabaseInstanceManager(final Subject user, final BrokerPool pool) {
        super(user, pool, null);
    }
    
    @Override
    public String getName() throws XMLDBException {
        return "DatabaseInstanceManager";
    }
    
    @Override
    public String getVersion() throws XMLDBException {
        return "1.0";
    }
	
    @Override
    public void shutdown() throws XMLDBException {
        brokerPool.shutdown();
    }

    @Override
    public void shutdown(final long delay) throws XMLDBException {
        if(!user.hasDbaRole()) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "only users in group dba may shut down the database");
        }

        final SystemTaskJob shutdownJob = new SystemTaskJobImpl("xmldb:local-api.shutdown", new ShutdownTask());
        brokerPool.getScheduler().createPeriodicJob(0, shutdownJob, delay, new Properties(), 0);
    }


    @Override
    public boolean enterServiceMode() throws XMLDBException {
        try {
            brokerPool.enterServiceMode(user);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
        return true;
    }

    @Override
    public void exitServiceMode() throws XMLDBException {
        try {
            brokerPool.exitServiceMode(user);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public DatabaseStatus getStatus() throws XMLDBException {
        return new DatabaseStatus(brokerPool);
    }

    @Override
    public boolean isLocalInstance() {
        return true;
    }

    @Override
    public String getProperty(final String name) throws XMLDBException {
        return null;
    }

    @Override
    public void setProperty(final String name, final String value) throws XMLDBException {
    }
}
