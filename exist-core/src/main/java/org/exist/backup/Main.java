/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.backup;

import org.exist.client.ClientFrame;
import org.exist.start.StartException;
import org.exist.util.ConfigurationHelper;
import org.exist.util.NamedThreadFactory;
import org.exist.util.OSUtil;
import org.exist.util.SystemExitCodes;
import org.exist.xmldb.*;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import se.softhouse.jargo.*;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.prefs.Preferences;

import static org.exist.util.ArgumentUtil.getBool;
import static org.exist.util.ArgumentUtil.getOpt;
import static se.softhouse.jargo.Arguments.*;

/**
 * Main.java
 *
 * @author Wolfgang Meier
 */
public class Main {

    private static final String USER_PROP = "user";
    private static final String PASSWORD_PROP = "password";
    private static final String URI_PROP = "uri";
    private static final String CONFIGURATION_PROP = "configuration";
    private static final String DRIVER_PROP = "driver";
    private static final String CREATE_DATABASE_PROP = "create-database";
    private static final String BACKUP_DIR_PROP = "backup-dir";

    public static final String SSL_ENABLE = "ssl-enable";

    private static final String DEFAULT_USER = "admin";
    private static final String DEFAULT_PASSWORD = "";
    private static final String DEFAULT_URI = "xmldb:exist://";
    private static final String DEFAULT_DRIVER = "org.exist.xmldb.DatabaseImpl";
    private static final String DEFAULT_BACKUP_DIR = "backup";

    /* general arguments */
    private static final Argument<?> helpArg = helpArgument("-h", "--help");
    private static final Argument<Boolean> guiArg = optionArgument("-U", "--gui")
            .description("Start in GUI mode.")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> quietArg = optionArgument("-q", "--quiet")
            .description("Be quiet. Just print errors.")
            .defaultValue(false)
            .build();
    private static final Argument<Map<String, String>> optionArg = stringArgument("-o", "--option")
            .description("Specify extra options: property=value. For available properties see client.properties.")
            .asKeyValuesWithKeyParser(StringParsers.stringParser())
            .build();


    /* database connection arguments */
    private static final Argument<String> userArg = stringArgument("-u", "--user")
            .description("Set user.")
            .defaultValue(DEFAULT_USER)
            .build();
    private static final Argument<String> passwordArg = stringArgument("-p", "--password")
            .description("Set the password for connecting to the database.")
            .build();
    private static final Argument<String> dbaPasswordArg = stringArgument("-P", "--dba-password")
            .description("If the backup specifies a different password for the admin user, " +
                    "use this option to specify the new password. Otherwise you will get a permission denied.")
            .build();
    private static final Argument<Boolean> useSslArg = optionArgument("-S", "--use-ssl")
            .description("Use SSL by default for remote connections.")
            .defaultValue(false)
            .build();

    /* backup arguments */
    private static final Argument<String> backupCollectionArg = stringArgument("-b", "--backup")
            .description("Backup the specified collection.")
            .build();
    private static final Argument<File> backupOutputDirArg = fileArgument("-d", "--dir")
            .description("Specify the directory to use for backups.")
            .build();
    private static final Argument<Boolean> backupDeduplicateBlobs = booleanArgument("--deduplicate-blobs")
            .description("Deduplicate BLOBS in the backup.")
            .build();


    /* restore arguments */
    private static final Argument<File> restoreArg = fileArgument("-r", "--restore")
            .description("Restore from the specified 'full' backup file in ZIP format, or " +
                    "read the specified __contents__.xml file and restore the resources described in there.")
            .build();
    private static final Argument<Boolean> rebuildExpathRepoArg = optionArgument("-R", "--rebuild")
            .description("Rebuild the EXpath app repository after restore.")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> overwriteAppsArg = optionArgument("-a", "--overwrite-apps")
            .description("Overwrite newer applications installed in the database.")
            .defaultValue(false)
            .build();

    private static Properties loadProperties() {
        try {
            final Properties properties = ConfigurationHelper.loadProperties("backup.properties", Main.class);
            if (properties != null) {
                return properties;
            }

            System.err.println("WARN - Unable to find backup.properties");

        } catch (final IOException e) {
            System.err.println("WARN - Unable to load backup.properties: " + e.getMessage());
        }

        // return new empty properties
        return new Properties();
    }

    /**
     * Constructor for Main.
     *
     * @param arguments parsed command line arguments
     */
    public static void process(final ParsedArguments arguments) {
        final Properties properties = loadProperties();
        final Preferences preferences = Preferences.userNodeForPackage(Main.class);

        final boolean guiMode = getBool(arguments, guiArg);
        final boolean quiet = getBool(arguments, quietArg);
        Optional.ofNullable(arguments.get(optionArg)).ifPresent(options -> options.forEach(properties::setProperty));

        properties.setProperty(USER_PROP, arguments.get(userArg));
        final String optionPass = arguments.get(passwordArg);
        properties.setProperty(PASSWORD_PROP, optionPass);
        final Optional<String> optionDbaPass = getOpt(arguments, dbaPasswordArg);
        final boolean useSsl = getBool(arguments, useSslArg);
        if (useSsl) {
            properties.setProperty(SSL_ENABLE, "TRUE");
        }

        final Optional<String> backupCollection = getOpt(arguments, backupCollectionArg);
        getOpt(arguments, backupOutputDirArg).ifPresent(backupOutputDir -> properties.setProperty(BACKUP_DIR_PROP, backupOutputDir.getAbsolutePath()));

        final Optional<Path> restorePath = getOpt(arguments, restoreArg).map(File::toPath);
        final boolean rebuildRepo = getBool(arguments, rebuildExpathRepoArg);
        final boolean overwriteApps = getBool(arguments, overwriteAppsArg);

        boolean deduplicateBlobs = getBool(arguments, backupDeduplicateBlobs);

        // initialize driver
        final Database database;

        try {
            final Class<?> cl = Class.forName(properties.getProperty(DRIVER_PROP, DEFAULT_DRIVER));
            database = (Database) cl.newInstance();
            database.setProperty(CREATE_DATABASE_PROP, "true");
            database.setProperty(SSL_ENABLE, properties.getProperty(SSL_ENABLE, "FALSE"));
            
            if (properties.containsKey(CONFIGURATION_PROP)) {
                database.setProperty(CONFIGURATION_PROP, properties.getProperty(CONFIGURATION_PROP));
            }
            DatabaseManager.registerDatabase(database);
        } catch (final ClassNotFoundException | InstantiationException | XMLDBException | IllegalAccessException e) {
            reportError(e);
            return;
        }

        // process
        if (backupCollection.isPresent()) {
            String collection = backupCollection.get();
            if (collection.isEmpty()) {
                if (guiMode) {
                    final CreateBackupDialog dialog = new CreateBackupDialog(properties.getProperty(URI_PROP, DEFAULT_URI), properties.getProperty(USER_PROP, DEFAULT_USER), properties.getProperty(PASSWORD_PROP, DEFAULT_PASSWORD), Paths.get(preferences.get("directory.backup", System.getProperty("user.dir"))));

                    if (JOptionPane.showOptionDialog(null, dialog, "Create Backup", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null) == JOptionPane.YES_OPTION) {
                        collection = dialog.getCollection();
                        deduplicateBlobs = dialog.getDeduplicateBlobs();
                        properties.setProperty(BACKUP_DIR_PROP, dialog.getBackupTarget());
                    }
                } else {
                    collection = XmldbURI.ROOT_COLLECTION;
                }
            }

            if (!collection.isEmpty()) {
                try {
                    final Backup backup = new Backup(
                            properties.getProperty(USER_PROP, DEFAULT_USER),
                            properties.getProperty(PASSWORD_PROP, DEFAULT_PASSWORD),
                            Paths.get(properties.getProperty(BACKUP_DIR_PROP, DEFAULT_BACKUP_DIR)),
                            XmldbURI.xmldbUriFor(properties.getProperty(URI_PROP, DEFAULT_URI) + collection),
                            properties,
                            deduplicateBlobs
                    );
                    backup.backup(guiMode, null);
                } catch (final Exception e) {
                    reportError(e);
                }
            }
        }

        if (restorePath.isPresent()) {
            Path path = restorePath.get();
            if (!Files.exists(path) && guiMode) {
                final JFileChooser chooser = new JFileChooser();
                chooser.setMultiSelectionEnabled(false);
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

                if (chooser.showDialog(null, "Select backup file for restore") == JFileChooser.APPROVE_OPTION) {
                    path = chooser.getSelectedFile().toPath();
                }
            }

            if (Files.exists(path)) {
                final String username = properties.getProperty(USER_PROP, DEFAULT_USER);
                final String uri = properties.getProperty(URI_PROP, DEFAULT_URI);

                try {
                    final XmldbURI dbUri;
                    if(!uri.endsWith(XmldbURI.ROOT_COLLECTION)) {
                        dbUri = XmldbURI.xmldbUriFor(uri + XmldbURI.ROOT_COLLECTION);
                    } else {
                        dbUri = XmldbURI.xmldbUriFor(uri);
                    }

                    if (guiMode) {
                        restoreWithGui(username, optionPass, optionDbaPass, path, dbUri, overwriteApps);
                    } else {
                        restoreWithoutGui(username, optionPass, optionDbaPass, path, dbUri,
                                rebuildRepo, quiet, overwriteApps);
                    }
                } catch (final Exception e) {
                    reportError(e);
                }
            }
        }

        try {
            String uri = properties.getProperty(URI_PROP, XmldbURI.EMBEDDED_SERVER_URI_PREFIX);
            if (!(uri.contains(XmldbURI.ROOT_COLLECTION) || uri.endsWith(XmldbURI.ROOT_COLLECTION))) {
                uri += XmldbURI.ROOT_COLLECTION;
            }

            final Collection root = DatabaseManager.getCollection(uri, properties.getProperty(USER_PROP, DEFAULT_USER), optionDbaPass.orElse(optionPass));
            shutdown(root);
        } catch (final Exception e) {
            reportError(e);
        }
        System.exit(SystemExitCodes.OK_EXIT_CODE);
    }

    private static void restoreWithoutGui(final String username, final String password,
            final Optional<String> dbaPassword, final Path f, final XmldbURI uri, final boolean rebuildRepo,
            final boolean quiet, final boolean overwriteApps) {
        final AggregatingConsoleRestoreServiceTaskListener listener = new AggregatingConsoleRestoreServiceTaskListener(quiet);
        try {
            final Collection collection = DatabaseManager.getCollection(uri.toString(), username, password);
            final EXistRestoreService service = collection.getService(EXistRestoreService.class);
            service.restore(f.toAbsolutePath().toString(), dbaPassword.orElse(null), listener, overwriteApps);

        } catch (final XMLDBException e) {
            listener.error(e.getMessage());
        }

        if (listener.hasProblems()) {
            System.err.println(listener.getAllProblems());
        }

        if (rebuildRepo) {
            System.out.println("Rebuilding application repository ...");
            System.out.println("URI: " + uri);
            try {
                final Collection root = DatabaseManager.getCollection(uri.toString(), username, dbaPassword.orElse(password));
                if (root != null) {
                    ClientFrame.repairRepository(root);
                    System.out.println("Application repository rebuilt successfully.");
                } else {
                    System.err.println("Failed to retrieve root collection: " + uri);
                }
            } catch (final XMLDBException e) {
                reportError(e);
                System.err.println("Rebuilding application repository failed!");
            }
        } else {
            System.out.println("\nIf you restored collections inside /db/apps, you may want\n" +
                    "to rebuild the application repository. To do so, run the following query\n" +
                    "as admin:\n\n" +
                    "import module namespace repair=\"http://exist-db.org/xquery/repo/repair\"\n" +
                    "at \"resource:org/exist/xquery/modules/expathrepo/repair.xql\";\n" +
                    "repair:clean-all(),\n" +
                    "repair:repair()\n");
        }
    }

    private static class AggregatingConsoleRestoreServiceTaskListener extends ConsoleRestoreServiceTaskListener {
        private StringBuilder allProblems = null;

        public AggregatingConsoleRestoreServiceTaskListener(final boolean quiet) {
            super(quiet);
        }

        @Override
        public void warn(final String message) {
            super.warn(message);
            addProblem(true, message);
        }

        @Override
        public void error(final String message) {
            super.error(message);
            addProblem(false, message);
        }

        public boolean hasProblems() {
            return allProblems != null && allProblems.length() > 0;
        }

        public String getAllProblems() {
            return allProblems.toString();
        }

        private void addProblem(final boolean warning, final String message) {
            final String sep = System.getProperty("line.separator");
            if (allProblems == null) {
                allProblems = new StringBuilder();
                allProblems.append("------------------------------------").append(sep);
                allProblems.append("Problems occurred found during restore:").append(sep);
            }

            if (warning) {
                allProblems.append("WARN: ");
            } else {
                allProblems.append("ERROR: ");
            }
            allProblems.append(message);
            allProblems.append(sep);
        }
    }

    private static void restoreWithGui(final String username, final String password, final Optional<String> dbaPassword,
                                       final Path f, final XmldbURI uri, boolean overwriteApps) {

        final GuiRestoreServiceTaskListener listener = new GuiRestoreServiceTaskListener();

        listener.info("Connecting ...");

        final Callable<Void> callable = () -> {

            try {
                final Collection collection = DatabaseManager.getCollection(uri.toString(), username, password);
                final EXistRestoreService service = collection.getService(EXistRestoreService.class);
                service.restore(f.toAbsolutePath().toString(), dbaPassword.orElse(null), listener, overwriteApps);

                listener.enableDismissDialogButton();

                if (JOptionPane.showConfirmDialog(null, "Would you like to rebuild the application repository?\nThis is only necessary if application packages were restored.", "Rebuild App Repository?",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    System.out.println("Rebuilding application repository ...");
                    try {
                        final Collection root = DatabaseManager.getCollection(uri.toString(), username, dbaPassword.orElse(password));
                        ClientFrame.repairRepository(root);
                        listener.info("Application repository rebuilt successfully.");
                    } catch (final XMLDBException e) {
                        reportError(e);
                        listener.info("Rebuilding application repository failed!");
                    }
                }
            } catch (final Exception e) {
                ClientFrame.showErrorMessage(e.getMessage(), null); //$NON-NLS-1$
            } finally {
                if (listener.hasProblems()) {
                    ClientFrame.showErrorMessage(listener.getAllProblems(), null);
                }
            }

            return null;
        };

        final ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory(null, null, "backup.restore-with-gui"));
        final Future<Void> future = executor.submit(callable);

        while (!future.isDone() && !future.isCancelled()) {
            try {
                future.get(100, TimeUnit.MILLISECONDS);
            } catch (final InterruptedException | TimeoutException ie) {

            } catch (final ExecutionException ee) {
                break;
            }
        }
    }


    private static void reportError(final Throwable e) {
        e.printStackTrace();

        if (e.getCause() != null) {
            System.err.println("caused by ");
            e.getCause().printStackTrace();
        }

        System.exit(SystemExitCodes.CATCH_ALL_GENERAL_ERROR_EXIT_CODE);
    }

    private static void shutdown(final Collection root) {
        try {
            final DatabaseInstanceManager mgr = root.getService(DatabaseInstanceManager.class);

            if (mgr == null) {
                System.err.println("service is not available");
            } else if (mgr.isLocalInstance()) {
                System.out.println("shutting down database...");
                mgr.shutdown();
            }
        } catch (final XMLDBException e) {
            System.err.println("database shutdown failed: ");
            e.printStackTrace();
        }
    }


    public static void main(final String[] args) {
        try {
            final ParsedArguments arguments = CommandLineParser
                    .withArguments(userArg, passwordArg, dbaPasswordArg, useSslArg)
                    .andArguments(backupCollectionArg, backupOutputDirArg, backupDeduplicateBlobs)
                    .andArguments(restoreArg, rebuildExpathRepoArg, overwriteAppsArg)
                    .andArguments(helpArg, guiArg, quietArg, optionArg)
                    .programName("backup" + (OSUtil.isWindows() ? ".bat" : ".sh"))
                    .parse(args);

            process(arguments);

        } catch(final ArgumentException e) {
            System.out.println(e.getMessageAndUsage());
            System.exit(SystemExitCodes.INVALID_ARGUMENT_EXIT_CODE);

        } catch (final Throwable e) {
            e.printStackTrace();
            System.exit(SystemExitCodes.CATCH_ALL_GENERAL_ERROR_EXIT_CODE);
        }
    }
}
