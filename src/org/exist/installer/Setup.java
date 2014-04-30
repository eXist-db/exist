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
        int offset = 0;
        String passwd = null;
        if (args[0].startsWith("pass:")) {
            passwd = args[0].substring(5);
            offset = 1;
        }
        System.setProperty(AutoDeploymentTrigger.AUTODEPLOY_PROPERTY, "off");
        final XQueryService query = initDb(passwd);
//        if (query != null) {
//            try {
//                installApps(query, args, offset);
//            } catch (EXistException e) {
//                System.err.println("An error occurred while installing apps: " + e.getMessage());
//            }
//        }
        shutdown(passwd);
    }

    private static void installApps(XQueryService query, String[] args, int offset) throws EXistException {
        final File home = getExistHome();
        final ExistRepository repository = getRepository(home);

        final List<String> uris = new ArrayList<String>();
        for (int i = offset; i < args.length; i++) {
            final String name = args[i];
            try {
                final File xar = findApp(home, name);
                if (xar != null) {
                    System.out.println("Installing app package " + xar.getName());
                    final UserInteractionStrategy interact = new BatchUserInteraction();
                    final Package pkg = repository.getParentRepo().installPackage(xar, true, interact);
                    final String pkgName = pkg.getName();
                    uris.add(pkgName);
                } else {
                    System.err.println("App package not found: " + name + ". Skipping it.");
                }
            } catch (final PackageException e) {
                System.err.println("Failed to install application package " + name + ": " + e.getMessage());
            }
        }

        System.out.println("\n=== Starting the installation process for each application... ===");
        System.out.println("\nPLEASE DO NOT ABORT\n");

        final String prolog =
            "import module namespace repo=\"http://exist-db.org/xquery/repo\" " +
            "at \"java:org.exist.xquery.modules.expathrepo.ExpathPackageModule\";\n";
        for (final String uri : uris) {
            final StringBuilder xquery = new StringBuilder(prolog);
            xquery.append(" repo:deploy(\"" + uri + "\")");
            System.out.print("Installing app package: " + uri + "... ");
            try {
                query.query(xquery.toString());
            } catch (final XMLDBException e) {
                e.printStackTrace();
                System.err.println("An error occurred while deploying application: " + uri +
                        ". You can install it later using the package repository.");
            }
            System.out.println("DONE.");
        }

        System.out.println("=== App installation completed. ===");
    }

    private static File getExistHome() throws EXistException {
        return BrokerPool.getInstance().getConfiguration().getExistHome();
    }

    private static File findApp(File home, String app) {
        final File apps = new File(home, "apps");
        System.out.println("Apps directory: " + apps.getAbsolutePath());
        if (apps.canRead() && apps.isDirectory()) {
            final File[] files = apps.listFiles();
            for (final File file : files) {
                if (file.getName().startsWith(app))
                    {return file;}
            }
        }
        return null;
    }

    private static ExistRepository getRepository(File home) throws EXistException {
        try {
            if (home != null){
                final File repo_dir = new File(home, "webapp/WEB-INF/expathrepo");
                // ensure the dir exists
                repo_dir.mkdir();
                final FileSystemStorage storage = new FileSystemStorage(repo_dir);
                return new ExistRepository(storage);
            }else{
                final File repo_dir = new File(System.getProperty("java.io.tmpdir") + "/expathrepo");
                // ensure the dir exists
                repo_dir.mkdir();
                final FileSystemStorage storage = new FileSystemStorage(repo_dir);
                return new ExistRepository(storage);
            }
        }
        catch ( final PackageException ex ) {
            // problem with pkg-repo.jar throwing exception
            throw new EXistException("Problem setting expath repository", ex);
        }
    }

    private static XQueryService initDb(String adminPass) {
        System.out.println("--- Starting embedded database instance ---");
        try {
            final Class<?> cl = Class.forName(DRIVER);
            final Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            Collection root = DatabaseManager.getCollection(URI, "admin", null);
            if (adminPass != null) {
                final UserManagementService service =
                        (UserManagementService) root.getService("UserManagementService", "1.0");
                final Account admin = service.getAccount("admin");
                admin.setPassword(adminPass);
                System.out.println("Setting admin user password...");
                service.updateAccount(admin);
                root = DatabaseManager.getCollection(URI, "admin", adminPass);
            }
            final XQueryService query = (XQueryService) root.getService("XQueryService", "1.0");
            return query;
        } catch (final Exception e) {
            System.err.println("Caught an exception while initializing db: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
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
