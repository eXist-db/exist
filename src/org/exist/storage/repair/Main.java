/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * $Id$
 */
package org.exist.storage.repair;

import org.exist.EXistException;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;

import java.util.List;

public class Main {

    protected static BrokerPool startDB() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (DatabaseConfigurationException e) {
            System.err.println("ERROR: Failed to open database: " + e.getMessage());
        } catch (EXistException e) {
            System.err.println("ERROR: Failed to open database: " + e.getMessage());
        }
        return null;
    }

    public static void main(String[] args) {
        BrokerPool pool = startDB();
        if (pool == null) {
            System.exit(1);
        }
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            ConsistencyCheck checker = new ConsistencyCheck(broker);
            List errors = checker.checkDocuments(null);
            if (errors != null) {
                for (int i = 0; i < errors.size(); i++) {
                    ErrorReport report = (ErrorReport) errors.get(i);
                    System.err.println(report.toString());
                }
            }
        } catch (EXistException e) {
            System.err.println("ERROR: Failed to retrieve database broker: " + e.getMessage());
        } finally {
            pool.release(broker);
            BrokerPool.stopAll(false);
        }
    }
}
