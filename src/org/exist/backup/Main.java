package org.exist.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.avalon.excalibur.cli.CLArgsParser;
import org.apache.avalon.excalibur.cli.CLOption;
import org.apache.avalon.excalibur.cli.CLOptionDescriptor;
import org.apache.avalon.excalibur.cli.CLUtil;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
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
	private final static int BACKUP_OPT = 'b';
	private final static int BACKUP_DIR_OPT = 'd';
    private final static int RESTORE_OPT = 'r';
	private final static int OPTION_OPT = 'o';

	private final static CLOptionDescriptor OPTIONS[] =
		new CLOptionDescriptor[] {
			new CLOptionDescriptor(
				"help",
				CLOptionDescriptor.ARGUMENT_DISALLOWED,
				HELP_OPT,
				"print help on command line options and exit."),
			new CLOptionDescriptor(
				"user",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				USER_OPT,
				"set user."),
			new CLOptionDescriptor(
				"password",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				PASS_OPT,
				"set password."),
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
                "read the specified restore file and restore the " +
                "resources described there."),
			new CLOptionDescriptor(
				"option",
				CLOptionDescriptor.ARGUMENTS_REQUIRED_2
					| CLOptionDescriptor.DUPLICATES_ALLOWED,
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
			String home = System.getProperty("exist.home");
			File propFile;
			if (home == null)
				propFile = new File("backup.properties");
			else
				propFile =
					new File(
						home
							+ System.getProperty("file.separator", "/")
							+ "backup.properties");

			InputStream pin;
			if (propFile.canRead())
				pin = new FileInputStream(propFile);
			else
				pin = Main.class.getResourceAsStream("backup.properties");

			if (pin != null)
				properties.load(pin);

		} catch (IOException ioe) {
		}

		//      parse command-line options
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
		for (int i = 0; i < size; i++) {
			option = (CLOption) opt.get(i);
			switch (option.getId()) {
				case HELP_OPT :
					printUsage();
					return;
				case OPTION_OPT :
					properties.setProperty(
						option.getArgument(0),
						option.getArgument(1));
					break;
				case USER_OPT :
					properties.setProperty("user", option.getArgument());
					break;
				case PASS_OPT :
					optionPass = option.getArgument();
					break;
				case BACKUP_OPT :
					if (option.getArgumentCount() == 1)
						optionBackup = option.getArgument();
					else
						optionBackup = "/db";
					break;
                case RESTORE_OPT :
                    optionRestore = option.getArgument();
                    break;
				case BACKUP_DIR_OPT :
					properties.setProperty("backup-dir", option.getArgument());
					break;
			}
		}

		// initialize driver
		try {
			Class cl =
				Class.forName(
					properties.getProperty(
						"driver",
						"org.exist.xmldb.DatabaseImpl"));
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			if (properties.containsKey("configuration"))
				database.setProperty(
					"configuration",
					properties.getProperty("configuration"));
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
		if (optionBackup != null) {
			Backup backup =
				new Backup(
					properties.getProperty("user", "admin"),
					optionPass,
					properties.getProperty("backup-dir", "backup"),
					properties.getProperty("uri", "xmldb:exist://")
						+ optionBackup);
			try {
				backup.backup();
			} catch (XMLDBException e) {
				reportError(e);
			} catch (IOException e) {
				reportError(e);
			} catch (SAXException e) {
				System.err.println("ERROR: " + e.getMessage());
				System.err.println("caused by ");
				e.getException().printStackTrace();
			}
		}
        if (optionRestore != null) {
            try {
				Restore restore = new Restore(
				    properties.getProperty("user", "admin"),
				    optionPass,
				    new File(optionRestore),
                    properties.getProperty("uri", "xmldb:exist://")
				);
				restore.restore();
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
			}
        }
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
		System.out.println(
			"Usage: java " + Main.class.getName() + " [options]");
		System.out.println(CLUtil.describeOptions(OPTIONS).toString());
	}

	public static void main(String[] args) {
		process(args);
	}
}
