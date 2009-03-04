package org.exist.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;
import java.util.prefs.Preferences;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.avalon.excalibur.cli.CLArgsParser;
import org.apache.avalon.excalibur.cli.CLOption;
import org.apache.avalon.excalibur.cli.CLOptionDescriptor;
import org.apache.avalon.excalibur.cli.CLUtil;
import org.exist.storage.DBBroker;
import org.exist.util.ConfigurationHelper;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;

/**
 * Main.java
 * 
 * @author Wolfgang Meier
 */
public class Main {

	private final static int HELP_OPT = 'h';
	private final static int USER_OPT = 'u';
	private final static int PASS_OPT = 'p';
	private final static int DBA_PASS_OPT = 'P';
	private final static int BACKUP_OPT = 'b';
	private final static int BACKUP_DIR_OPT = 'd';
	private final static int RESTORE_OPT = 'r';
	private final static int OPTION_OPT = 'o';
	private final static int GUI_OPT = 'U';

	private final static CLOptionDescriptor OPTIONS[] =
		new CLOptionDescriptor[] {
			new CLOptionDescriptor(
				"help",
				CLOptionDescriptor.ARGUMENT_DISALLOWED,
				HELP_OPT,
				"print help on command line options and exit."),
			new CLOptionDescriptor(
				"gui",
				CLOptionDescriptor.ARGUMENT_DISALLOWED,
				GUI_OPT,
				"start in GUI mode"),
			new CLOptionDescriptor(
				"user",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				USER_OPT,
				"set user."),
			new CLOptionDescriptor(
				"password",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				PASS_OPT,
				"set the password for connecting to the database."),
			new CLOptionDescriptor(
				"dba-password",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				DBA_PASS_OPT,
				"if the backup specifies a different password for the admin/dba user, use this option " +
				"to specify the new password. Otherwise you will get a permission denied"),
			new CLOptionDescriptor(
				"backup",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				BACKUP_OPT,
				"backup the specified collection."),
			new CLOptionDescriptor(
				"dir",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				BACKUP_DIR_OPT,
				"specify the directory to use for backups."),
			new CLOptionDescriptor(
				"restore",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				RESTORE_OPT,
				"read the specified restore file and restore the " + "resources described there."),
			new CLOptionDescriptor(
				"option",
				CLOptionDescriptor.ARGUMENTS_REQUIRED_2 | CLOptionDescriptor.DUPLICATES_ALLOWED,
				OPTION_OPT,
				"specify extra options: property=value. For available properties see "
					+ "client.properties.")};

	/**
	 * Constructor for Main.
	 */
	public static void process(String args[]) {
		// read properties
		Properties properties = new Properties();
		try {
			File propFile = ConfigurationHelper.lookup("backup.properties");
			InputStream pin;
			if (propFile.canRead())
				pin = new FileInputStream(propFile);
			else
				pin = Main.class.getResourceAsStream("backup.properties");

			if (pin != null)
				properties.load(pin);

		} catch (IOException ioe) {
		}

        Preferences preferences = Preferences.userNodeForPackage(Main.class);
        
		// parse command-line options
		final CLArgsParser optParser = new CLArgsParser(args, OPTIONS);

		if (optParser.getErrorString() != null) {
			System.err.println("ERROR: " + optParser.getErrorString());
			return;
		}
		final List opt = optParser.getArguments();
		final int size = opt.size();
		CLOption option;
		String optionBackup = null;
		String optionRestore = null;
		String optionPass = null;
		String optionDbaPass = null;
		boolean doBackup = false;
		boolean doRestore = false;
		boolean guiMode = false;
		for (int i = 0; i < size; i++) {
			option = (CLOption) opt.get(i);
			switch (option.getId()) {
				case HELP_OPT :
					printUsage();
					return;
				case GUI_OPT :
					guiMode = true;
					break;
				case OPTION_OPT :
					properties.setProperty(option.getArgument(0), option.getArgument(1));
					break;
				case USER_OPT :
					properties.setProperty("user", option.getArgument());
					break;
				case PASS_OPT :
					properties.setProperty("password", option.getArgument());
					optionPass = option.getArgument();  //remove after change inside restore
					break;
				case DBA_PASS_OPT :
					optionDbaPass = option.getArgument();
					break;
				case BACKUP_OPT :
					if (option.getArgumentCount() == 1)
						optionBackup = option.getArgument();
					else
						optionBackup = null;
					doBackup = true;
					break;
				case RESTORE_OPT :
					if(option.getArgumentCount() == 1)
						optionRestore = option.getArgument();
					doRestore = true;
					break;
				case BACKUP_DIR_OPT :
					properties.setProperty("backup-dir", option.getArgument());
					break;
			}
		}

		// initialize driver
		Database database;
		try {
			Class cl =
				Class.forName(properties.getProperty("driver", "org.exist.xmldb.DatabaseImpl"));
			database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			if (properties.containsKey("configuration"))
				database.setProperty("configuration", properties.getProperty("configuration"));
			DatabaseManager.registerDatabase(database);
		} catch (ClassNotFoundException e) {
			reportError(e);
			return;
		} catch (InstantiationException e) {
			reportError(e);
			return;
		} catch (IllegalAccessException e) {
			reportError(e);
			return;
		} catch (XMLDBException e) {
			reportError(e);
			return;
		}

		// process
		if (doBackup) {
			if (optionBackup == null) {
				if (guiMode) {
					CreateBackupDialog dialog =
						new CreateBackupDialog(
							properties.getProperty("uri", "xmldb:exist://"),
							properties.getProperty("user", "admin"),
							properties.getProperty("password",""),
                            new File(preferences.get("directory.backup", System.getProperty("user.dir"))));
					if (JOptionPane
						.showOptionDialog(
							null,
							dialog,
							"Create Backup",
							JOptionPane.OK_CANCEL_OPTION,
							JOptionPane.QUESTION_MESSAGE,
							null,
							null,
							null)
						== JOptionPane.YES_OPTION) {
						optionBackup = dialog.getCollection();
						properties.setProperty("backup-dir", dialog.getBackupTarget());
					}
				} else
					optionBackup = DBBroker.ROOT_COLLECTION;
			}
			if (optionBackup != null) {
				try {
					Backup backup =
					new Backup(
						properties.getProperty("user", "admin"),
						properties.getProperty("password",""),
						properties.getProperty("backup-dir", "backup"),
						XmldbURI.xmldbUriFor(properties.getProperty("uri", "xmldb:exist://") + optionBackup),
						properties);
					backup.backup(guiMode, null);
				} catch (XMLDBException e) {
					reportError(e);
				} catch (IOException e) {
					reportError(e);
				} catch (SAXException e) {
					System.err.println("ERROR: " + e.getMessage());
					System.err.println("caused by ");
					e.getException().printStackTrace();
				} catch (URISyntaxException e) {
					reportError(e);
				}
			}
		}
		if (doRestore) {
			if (optionRestore == null && guiMode) {
				JFileChooser chooser = new JFileChooser();
				chooser.setMultiSelectionEnabled(false);
				chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
				if (chooser.showDialog(null, "Select backup file for restore")
					== JFileChooser.APPROVE_OPTION) {
					File f = chooser.getSelectedFile();
					optionRestore = f.getAbsolutePath();
				}
			}
			if (optionRestore != null) {
				try {
					Restore restore =
						new Restore(
							properties.getProperty("user", "admin"),
							optionPass,
							optionDbaPass,
							new File(optionRestore),
							properties.getProperty("uri", "xmldb:exist://"));
					restore.restore(guiMode, null);
				} catch (FileNotFoundException e) {
					reportError(e);
				} catch (ParserConfigurationException e) {
					reportError(e);
				} catch (SAXException e) {
					reportError(e);
				} catch (XMLDBException e) {
					reportError(e);
				} catch (IOException e) {
					reportError(e);
				} catch (URISyntaxException e) {
					reportError(e);
				}
			}
		}
		try {
			Collection root =
				DatabaseManager.getCollection(
					properties.getProperty("uri", "xmldb:exist://") + DBBroker.ROOT_COLLECTION,
					properties.getProperty("user", "admin"),
					optionDbaPass == null ? optionPass : optionDbaPass);
			shutdown(root);
		} catch (XMLDBException e1) {
			e1.printStackTrace();
		}
		System.exit(0);
	}

	private final static void reportError(Throwable e) {
		System.err.println("ERROR: " + e.getMessage());
		e.printStackTrace();
		if (e.getCause() != null) {
			System.err.println("caused by ");
			e.getCause().printStackTrace();
		}
	}

	private final static void printUsage() {
		System.out.println("Usage: java " + Main.class.getName() + " [options]");
		System.out.println(CLUtil.describeOptions(OPTIONS).toString());
	}

	private final static void shutdown(Collection root) {
		try {
			DatabaseInstanceManager mgr =
				(DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
			if (mgr == null) {
				System.err.println("service is not available");
			} else if (mgr.isLocalInstance()) {
				System.out.println("shutting down database...");
				mgr.shutdown();
			}
		} catch (XMLDBException e) {
			System.err.println("database shutdown failed: ");
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		process(args);
	}
}
