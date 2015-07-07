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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */

package org.exist.installer;

import org.exist.EXistException;
import org.exist.repo.AutoDeploymentTrigger;
import org.exist.repo.ExistRepository;
import org.exist.security.Account;
import org.exist.storage.BrokerPool;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.UserManagementService;
import org.expath.pkg.repo.FileSystemStorage;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.UserInteractionStrategy;
import org.expath.pkg.repo.tui.BatchUserInteraction;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Initial database setup: called from the installer to set the admin password.
 */
public class Setup {

    private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
    private final static String URI = "xmldb:exist:///db";

    public static void main(String[] args) {
        //TODO: I think this will never happen with the current setup. Class needs a little more cleanup.
        if (args.length < 1) {
            System.err.println("No password specified. Admin password will be empty.");
            return;
        }
        String passwd = null;
        if (args[0].startsWith("pass:")) {
            passwd = args[0].substring(5);
        }
        System.setProperty(AutoDeploymentTrigger.AUTODEPLOY_PROPERTY, "off");
        initDb(passwd);
        shutdown(passwd);
    }

    private static void initDb(String adminPass) {
        System.out.println("--- Starting embedded database instance ---");
        try {
            final Class<?> cl = Class.forName(DRIVER);
            final Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            final Collection root = DatabaseManager.getCollection(URI, "admin", null);
            if (adminPass != null) {
                final UserManagementService service =
                        (UserManagementService) root.getService("UserManagementService", "1.0");
                final Account admin = service.getAccount("admin");
                admin.setPassword(adminPass);
                System.out.println("Setting admin user password...");
                service.updateAccount(admin);
            }
        } catch (final Exception e) {
            System.err.println("Caught an exception while initializing db: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void shutdown(String adminPass) {
        System.out.println("--- Initialization complete. Shutdown embedded database instance ---");
        try {
            final Collection root = DatabaseManager.getCollection(URI, "admin", adminPass);
            final DatabaseInstanceManager manager = (DatabaseInstanceManager)
                    root.getService("DatabaseInstanceManager", "1.0");
            manager.shutdown();
        } catch (final XMLDBException e) {
            System.err.println("Caught an exception while initializing db: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
