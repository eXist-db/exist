/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
 *  http://exist.sourceforge.net
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id:
 */
package org.exist.client;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.io.PushbackInputStream;
import java.io.StringReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Observer;
import java.util.Observable;
import org.apache.oro.io.GlobFilenameFilter;
import org.exist.security.Permission;
import org.exist.security.User;
import org.exist.util.Occurrences;
import org.exist.util.XMLFilenameFilter;
import org.exist.util.DirectoryScanner;
import org.exist.util.CollectionScanner;
import org.exist.util.ProgressBar;
import org.exist.util.ProgressIndicator;
import org.exist.util.XMLUtil;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.IndexQueryService;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XPathQueryServiceImpl;
import org.gnu.readline.Readline;
import org.gnu.readline.ReadlineCompleter;
import org.gnu.readline.ReadlineLibrary;
import org.xmldb.api.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;
import org.apache.avalon.excalibur.cli.*;

/**
 *  Command-line client based on the XML:DB API.
 *
 *@author     wolf
 *@created    April 2, 2002
 */
public class InteractiveClient {

	private final static int HELP_OPT = 'h';
	private final static int QUIET_OPT = 'q';
	private final static int USER_OPT = 'u';
	private final static int PASS_OPT = 'P';
	private final static int LOCAL_OPT = 'l';
	private final static int CONFIG_OPT = 'C';
	private final static int PARSE_OPT = 'p';
	private final static int COLLECTION_OPT = 'c';
	private final static int RESOURCE_OPT = 'f';
	private final static int REMOVE_OPT = 'r';
	private final static int GET_OPT = 'g';
	private final static int MKCOL_OPT = 'm';
	private final static int RMCOL_OPT = 'R';
	private final static int OPTION_OPT = 'o';
	private final static int FIND_OPT = 'x';
	private final static int RESULTS_OPT = 'n';
	private final static int VERBOSE_OPT = 'v';
	private final static int QUERY_FILE_OPT = 'F';
	private final static int XUPDATE_OPT = 'X';
	private final static int THREADS_OPT = 't';
	private final static int RECURSE_DIRS_OPT = 'd';
	private final static int GUI_OPT = 'U';

	private final static CLOptionDescriptor OPTIONS[] =
		new CLOptionDescriptor[] {
			new CLOptionDescriptor(
				"help",
				CLOptionDescriptor.ARGUMENT_DISALLOWED,
				HELP_OPT,
				"print help on command line options and exit."),
			new CLOptionDescriptor(
				"quiet",
				CLOptionDescriptor.ARGUMENT_DISALLOWED,
				QUIET_OPT,
				"be quiet. Just print errors."),
			new CLOptionDescriptor(
				"verbose",
				CLOptionDescriptor.ARGUMENT_DISALLOWED,
				VERBOSE_OPT,
				"be verbose. Display progress information on put."),
			new CLOptionDescriptor(
				"user",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				USER_OPT,
				"set username."),
			new CLOptionDescriptor(
				"password",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				PASS_OPT,
				"specify password."),
			new CLOptionDescriptor(
				"local",
				CLOptionDescriptor.ARGUMENT_DISALLOWED,
				LOCAL_OPT,
				"launch a local database instance. Otherwise client will connect to "
					+ "URI specified in client.properties."),
			new CLOptionDescriptor(
				"config",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				CONFIG_OPT,
				"specify alternate configuration file. Implies -l."),
			new CLOptionDescriptor(
				"parse",
				CLOptionDescriptor.ARGUMENT_OPTIONAL,
				PARSE_OPT,
				"store files or directories given as extra args on command line."),
			new CLOptionDescriptor(
				"remove",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				REMOVE_OPT,
				"remove a document."),
			new CLOptionDescriptor(
				"collection",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				COLLECTION_OPT,
				"set target collection."),
			new CLOptionDescriptor(
				"resource",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				RESOURCE_OPT,
				"specify a resource contained in the current collection. "
					+ "Use in conjunction with -u to specify the resource to "
					+ "update."),
			new CLOptionDescriptor(
				"get",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				GET_OPT,
				"retrieve a document."),
			new CLOptionDescriptor(
				"mkcol",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				MKCOL_OPT,
				"create a collection (and any missing parent collection). Implies -c."),
			new CLOptionDescriptor(
				"rmcol",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				RMCOL_OPT,
				"remove entire collection"),
			new CLOptionDescriptor(
				"xpath",
				CLOptionDescriptor.ARGUMENT_OPTIONAL,
				FIND_OPT,
				"execute XPath query given as argument. Without argument reads query from stdin."),
			new CLOptionDescriptor(
				"howmany",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				RESULTS_OPT,
				"max. number of query results to be displayed."),
			new CLOptionDescriptor(
				"option",
				CLOptionDescriptor.ARGUMENTS_REQUIRED_2 | CLOptionDescriptor.DUPLICATES_ALLOWED,
				OPTION_OPT,
				"specify extra options: property=value. For available properties see "
					+ "client.properties."),
			new CLOptionDescriptor(
				"file",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				QUERY_FILE_OPT,
				"load queries from file and execute in random order."),
			new CLOptionDescriptor(
				"threads",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				THREADS_OPT,
				"number of parallel threads to test with (use with -f)."),
			new CLOptionDescriptor(
				"recurse-dirs",
				CLOptionDescriptor.ARGUMENT_DISALLOWED,
				RECURSE_DIRS_OPT,
				"recurse into subdirectories during index?"),
			new CLOptionDescriptor(
				"xupdate",
				CLOptionDescriptor.ARGUMENT_REQUIRED,
				XUPDATE_OPT,
				"process xupdate commands. Commands are read from the "
					+ "file specified in the argument."),
			new CLOptionDescriptor(
				"gui",
				CLOptionDescriptor.ARGUMENT_DISALLOWED,
				GUI_OPT,
				"start client in gui mode")};

	// ANSI colors for ls display
	private final static String ANSI_BLUE = "\033[0;34m";
	private final static String ANSI_CYAN = "\033[0;36m";
	private final static String ANSI_WHITE = "\033[0;37m";

	// properties
	protected static String EDIT_CMD = "xemacs $file";
	protected static String ENCODING = "ISO-8859-1";
	protected static String PASS = null;
	protected static String URI = "xmldb:exist://localhost:8080/exist/xmlrpc";
	protected static String USER = org.exist.security.SecurityManager.DBA_USER;
	protected static int PARALLEL_THREADS = 5;
	protected static Properties defaultProps = new Properties();
	{
		defaultProps.setProperty("driver", driver);
		defaultProps.setProperty("uri", URI);
		defaultProps.setProperty("editor", EDIT_CMD);
		defaultProps.setProperty("indent", "true");
		defaultProps.setProperty("encoding", ENCODING);
		defaultProps.setProperty("user", USER);
		defaultProps.setProperty("colors", "false");
		defaultProps.setProperty("permissions", "false");
		defaultProps.setProperty("expand-xincludes", "true");
	}

	protected static String driver = "org.exist.xmldb.DatabaseImpl";
	protected static String configuration = null;

	protected TreeSet completitions = new TreeSet();
	protected Collection current = null;
	protected int nextInSet = 1;
	protected int maxResults = 10;
	protected String path = "/db";
	protected Properties properties;
	protected String[] resources = null;
	protected ResourceSet result = null;
	protected int filesCount = 0;
	protected boolean quiet = false;
	protected boolean verbose = false;
	protected boolean recurseDirs = false;
	protected boolean startGUI = false;
	protected ClientFrame frame;

	public InteractiveClient() {
	}

	/**  Display help on commands */
	protected void displayHelp() {
		messageln("--- general commands ---");
		messageln("ls                   list collection contents");
		messageln("cd [collection|..]   change current collection");
		messageln("put [file pattern] upload file or directory" + " to the database");
		messageln("mkcol collection     create new sub-collection in " + "current collection");
		messageln("rm document          remove document from current " + "collection");
		messageln("rmcol collection     remove collection");
		messageln("set [key=value]      set property. Calling set without ");
		messageln("                     argument shows current settings.");
		messageln("\n--- search commands ---");
		messageln("find xpath-expr      execute the given XPath expression.");
		messageln("show [position]      display query result value at position.");
		messageln("\n--- user management (may require dba rights) ---");
		messageln("users                list existing users.");
		messageln("adduser username     create a new user.");
		messageln("passwd username      change password for user. ");
		messageln("chown user group [resource]");
		messageln("                     change resource ownership. chown without");
		messageln("                     resource changes ownership of the current");
		messageln("                     collection.");
		messageln("chmod [resource] permissions");
		messageln("                     change resource permissions. Format:");
		messageln("                     [user|group|other]=[+|-][read|write|update].");
		messageln("                     chmod without resource changes permissions for");
		messageln("                     the current collection.");
		messageln("quit                 quit the program");
	}

	/**
	 *  The main program for the InteractiveClient class.
	 *
	 *@param  args  The command line arguments
	 */
	public static void main(String[] args) {
		try {
			InteractiveClient client = new InteractiveClient();
			client.run(args);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 *  Register XML:DB driver and retrieve root collection.
	 *
	 *@exception  Exception  Description of the Exception
	 */
	private void connect() throws Exception {
		Class cl = Class.forName(properties.getProperty("driver"));
		Database database = (Database) cl.newInstance();
		database.setProperty("create-database", "true");
		if (properties.containsKey("configuration"))
			database.setProperty("configuration", properties.getProperty("configuration"));
		DatabaseManager.registerDatabase(database);
		current =
			DatabaseManager.getCollection(
				properties.getProperty("uri") + path,
				properties.getProperty("user"),
				properties.getProperty("password"));
	}

	/**
	 *  Get list of resources contained in collection.
	 *
	 *@exception  XMLDBException  Description of the Exception
	 */
	private void getResources() throws XMLDBException {
		if (current == null)
			return;
		UserManagementService mgtService =
			(UserManagementService) current.getService("UserManagementService", "1.0");
		String childCollections[] = current.listChildCollections();
		String childResources[] = current.listResources();
		Permission perms[] = null;
		if (properties.getProperty("permissions").equals("true"))
			perms = mgtService.listCollectionPermissions();
		resources = new String[childCollections.length + childResources.length];
		int i = 0;
		Collection child;
		for (; i < childCollections.length; i++) {
			child = current.getChildCollection(childCollections[i]);
			if (properties.getProperty("permissions").equals("true")) {
				if (properties.getProperty("colors").equals("true"))
					resources[i] =
						'd'
							+ perms[i].toString()
							+ '\t'
							+ ANSI_BLUE
							+ childCollections[i]
							+ ANSI_WHITE;
				else
					resources[i] = 'd' + perms[i].toString() + '\t' + childCollections[i];
			} else
				resources[i] = childCollections[i];
			completitions.add(childCollections[i]);
		}
		if (properties.getProperty("permissions").equals("true"))
			perms = mgtService.listResourcePermissions();
		Resource res;
		for (int j = 0; j < childResources.length; i++, j++) {
			res = current.getResource(childResources[j]);
			if (properties.getProperty("permissions").equals("true") && j < perms.length) {
				resources[i] = '-' + perms[j].toString() + '\t' + childResources[j];
			} else
				resources[i] = childResources[j];
			completitions.add(childResources[j]);
		}
	}

	/**
	 *  Display document on screen.
	 *
	 *@param  str  Description of the Parameter
	 */
	protected void more(String str) {
		LineNumberReader reader = new LineNumberReader(new StringReader(str));
		String line;
		int count = 0;
		int ch;
		try {
			while (System.in.available() > 0)
				System.in.read();

			while ((line = reader.readLine()) != null) {
				if (reader.getLineNumber() % 24 == 0) {
					System.out.print(
						"line: "
							+ reader.getLineNumber()
							+ "; press [return] for more or [q] for quit.");
					ch = System.in.read();
					if (ch == 'q' || ch == 'Q')
						return;
				}
				System.out.println(line);
			}
		} catch (IOException ioe) {
			System.err.println("IOException: " + ioe);
		}
	}

	/**
	 *  In interactive mode, process a line entered by the user.
	 *
	 *@param  line  the line entered
	 *@return       true if command != quit
	 */
	protected boolean process(String line) {
		if (startGUI)
			frame.setPath(path);
		String args[];
		if (line.startsWith("find")) {
			args = new String[2];
			args[0] = "find";
			args[1] = line.substring(5);
		} else {
			StringTokenizer tok = new StringTokenizer(line, " ");
			args = new String[tok.countTokens()];
			int i = 0;
			while (tok.hasMoreTokens())
				args[i++] = tok.nextToken();
		}
		String newPath = path;
		try {
			if (args[0].equalsIgnoreCase("ls")) {
				// list collection contents
				if (properties.getProperty("permissions").equals("true")) {
					for (int i = 0; i < resources.length; i++)
						messageln(resources[i]);
				} else {
					StringBuffer buf;
					for (int i = 0; i < resources.length; i++) {
						buf = new StringBuffer();
						int k = 0;
						for (int j = 0; i < resources.length && j < 5; i++, j++) {
							buf.append(resources[i] + '\t');
							k = j;
						}
						if (k == 4 && i < resources.length)
							i--;
						messageln(buf.toString());
					}
				}
			} else if (args[0].equalsIgnoreCase("cd")) {
				// change current collection
				completitions.clear();
				String tempPath = newPath;
				Collection temp;
				if (args.length < 2 || args[1] == null) {
					tempPath = "/db";
					temp =
						DatabaseManager.getCollection(
							properties.getProperty("uri") + "/db",
							properties.getProperty("user"),
							properties.getProperty("password"));
				} else {
					if (args[1].equals("..")) {
						tempPath =
							newPath.equals("/db")
								? "/db"
								: tempPath.substring(0, newPath.lastIndexOf("/"));
						if (tempPath.length() == 0)
							tempPath = "/db";
					} else if (args[1].startsWith("/"))
						tempPath = args[1];
					else
						tempPath = tempPath + '/' + args[1];

					temp =
						DatabaseManager.getCollection(
							properties.getProperty("uri") + tempPath,
							properties.getProperty("user"),
							properties.getProperty("password"));
				}
				if (temp != null) {
					current = temp;
					newPath = tempPath;
					if(startGUI)
						frame.setPath(newPath);
				} else {
					messageln("no such collection.");
				}
				getResources();
			} else if (args[0].equalsIgnoreCase("get")) {
				if (args.length < 2) {
					System.err.println("wrong number of arguments.");
					return true;
				}
				XMLResource res = retrieve(args[1]);
				// display document
				if(res != null) {
					if (startGUI) {
						frame.setEditable(false);
						//res.getContentAsSAX(frame.getContentHandler());
						frame.display((String)res.getContent());
						frame.setEditable(true);
					} else {
						String content = (String)res.getContent();
						more(content);
					}
				}
				return true;
			} else if (args[0].equalsIgnoreCase("find")) {
				// search
				if (args.length < 2) {
					messageln("no query argument found.");
					return true;
				}
				messageln(args[1]);
				long start = System.currentTimeMillis();
				result = find(args[1]);
				if (result == null)
					messageln("nothing found");
				else
					messageln(
						"found "
							+ result.getSize()
							+ " hits in "
							+ (System.currentTimeMillis() - start)
							+ "ms.");

				nextInSet = 1;

			} else if (args[0].equalsIgnoreCase("show")) {
				// show search results
				if (result == null) {
					messageln("no result set.");
					return true;
				}
				try {
					int start = nextInSet;
					int count = 1;
					if (args.length > 1)
						start = Integer.parseInt(args[1]);

					if (args.length > 2)
						count = Integer.parseInt(args[2]);

					final int s = (int) result.getSize();
					if (start < 1 || start > s) {
						messageln("start offset out of range");
						return true;
					}
					--start;
					if (start + count > s)
						count = s - start;

					nextInSet = start + count + 1;
					for (int i = start; i < start + count; i++) {
						Resource r = result.getResource((long) i);
						if (startGUI)
							frame.display((String) r.getContent());
						else
							more((String) r.getContent());
					}
					messageln(
						"displayed items "
							+ (start + 1)
							+ " to "
							+ (start + count)
							+ " of "
							+ result.getSize());
				} catch (NumberFormatException nfe) {
					messageln("wrong argument");
					return true;
				}

			} else if (args[0].equalsIgnoreCase("mkcol")) {
				// create collection
				if (args.length < 2) {
					messageln("missing argument.");
					return true;
				}
				CollectionManagementService mgtService =
					(CollectionManagementService) current.getService(
						"CollectionManagementService",
						"1.0");
				Collection newCollection = mgtService.createCollection(args[1]);
				if (newCollection == null)
					messageln("could not create collection.");
				else
					messageln("created collection.");

				// re-read current collection
				current =
					DatabaseManager.getCollection(
						properties.getProperty("uri") + path,
						properties.getProperty("user"),
						properties.getProperty("password"));
				getResources();

			} else if (args[0].equalsIgnoreCase("put")) {
				// put a document or directory into the database
				if (args.length < 2) {
					messageln("missing argument.");
					return true;
				}
				boolean r = parse(args[1]);
				getResources();
				return r;
			} else if (args[0].equalsIgnoreCase("rm")) {
				// remove document
				if (args.length < 2) {
					messageln("missing argument.");
					return true;
				}

				remove(args[1]);

				// re-read current collection
				current =
					DatabaseManager.getCollection(
						properties.getProperty("uri") + path,
						properties.getProperty("user"),
						properties.getProperty("password"));
				getResources();

			} else if (args[0].equalsIgnoreCase("rmcol")) {
				// remove collection
				if (args.length < 2) {
					messageln("wrong argument count.");
					return true;
				}
				rmcol(args[1]);
				// re-read current collection
				current =
					DatabaseManager.getCollection(
						properties.getProperty("uri") + path,
						properties.getProperty("user"),
						properties.getProperty("password"));
				getResources();
			} else if (args[0].equalsIgnoreCase("adduser")) {
				if (args.length < 2) {
					System.err.println("Usage: adduser name");
					return true;
				}
				try {
					UserManagementService mgtService =
						(UserManagementService) current.getService("UserManagementService", "1.0");

					String p1;
					String p2;
					while (true) {
						p1 = Readline.readline("password: ");
						p2 = Readline.readline("re-enter password: ");
						if (p1.equals(p2))
							break;
						else
							System.out.println("\nentered passwords differ. Try again...");

					}
					String home = Readline.readline("home collection [none]: ");
					User user = new User(args[1], p1);
					if (home.length() > 0)
						user.setHome(home);
					String groups = Readline.readline("enter groups: ");
					StringTokenizer tok = new StringTokenizer(groups, " ,");
					String group;
					while (tok.hasMoreTokens()) {
						group = tok.nextToken();
						if (group.length() > 0)
							user.addGroup(group);
					}

					mgtService.addUser(user);
					System.out.println("user " + user + " created.");
				} catch (Exception e) {
					System.out.println("ERROR: " + e.getMessage());
					e.printStackTrace();
				}
			} else if (args[0].equalsIgnoreCase("users")) {
				UserManagementService mgtService =
					(UserManagementService) current.getService("UserManagementService", "1.0");
				User users[] = mgtService.getUsers();
				System.out.println("User\t\tGroups");
				System.out.println("-----------------------------------------");
				for (int i = 0; i < users.length; i++) {
					System.out.print(users[i].getName() + "\t\t");
					for (Iterator j = users[i].getGroups(); j.hasNext();) {
						System.out.print(j.next());
						if (j.hasNext())
							System.out.print(", ");

					}
					System.out.println();
				}
			} else if (args[0].equalsIgnoreCase("passwd")) {
				if (args.length < 2) {
					System.out.println("Usage: passwd username");
					return true;
				}
				try {
					UserManagementService mgtService =
						(UserManagementService) current.getService("UserManagementService", "1.0");
					User user = mgtService.getUser(args[1]);
					if (user == null) {
						System.out.println("no such user.");
						return true;
					}
					String p1;
					String p2;
					while (true) {
						p1 = Readline.readline("password: ");
						p2 = Readline.readline("re-enter password: ");
						if (p1.equals(p2))
							break;
						else
							System.out.println("\nentered passwords differ. Try again...");

					}
					user.setPassword(p1);
					mgtService.updateUser(user);
					properties.setProperty("password", p1);
				} catch (Exception e) {
					System.err.println("ERROR: " + e.getMessage());
				}
			} else if (args[0].equalsIgnoreCase("chmod")) {
				if (args.length < 2) {
					System.out.println("Usage: chmod [resource] mode");
					return true;
				}

				Collection temp = null;
				if (args.length == 3) {
					System.out.println("trying collection: " + args[1]);
					temp = current.getChildCollection(args[1]);
					if (temp == null) {
						System.out.println("\ntrying resource: " + args[1]);
						Resource r = current.getResource(args[1]);
						if (r != null) {
							UserManagementService mgtService =
								(UserManagementService) current.getService(
									"UserManagementService",
									"1.0");
							mgtService.chmod(r, args[2]);
						} else
							System.err.println("Resource " + args[1] + " not found.");
					} else {
						UserManagementService mgtService =
							(UserManagementService) temp.getService("UserManagementService", "1.0");
						mgtService.chmod(args[2]);
					}
				} else {
					UserManagementService mgtService =
						(UserManagementService) current.getService("UserManagementService", "1.0");
					mgtService.chmod(args[1]);
				}
				// re-read current collection
				current =
					DatabaseManager.getCollection(
						properties.getProperty("uri") + path,
						properties.getProperty("user"),
						properties.getProperty("password"));
				getResources();
			} else if (args[0].equalsIgnoreCase("chown")) {
				if (args.length < 3) {
					System.out.println("Usage: chown username group [resource]");
					return true;
				}

				Collection temp;
				if (args.length == 4)
					temp = current.getChildCollection(args[3]);
				else
					temp = current;
				if (temp != null) {
					UserManagementService mgtService =
						(UserManagementService) temp.getService("UserManagementService", "1.0");
					User u = mgtService.getUser(args[1]);
					if (u == null) {
						System.out.println("unknown user");
						return true;
					}
					mgtService.chown(u, args[2]);
					System.out.println("owner changed.");
					getResources();
					return true;
				}
				Resource res = current.getResource(args[3]);
				if (res != null) {
					UserManagementService mgtService =
						(UserManagementService) current.getService("UserManagementService", "1.0");
					User u = mgtService.getUser(args[1]);
					if (u == null) {
						System.out.println("unknown user");
						return true;
					}
					mgtService.chown(res, u, args[2]);
					getResources();
					return true;
				}
				System.err.println("Resource " + args[3] + " not found.");

			} else if (args[0].equalsIgnoreCase("elements")) {
				System.out.println("Element occurrences in collection " + current.getName());
				System.out.println("--------------------------------------------" + "-----------");
				IndexQueryService service =
					(IndexQueryService) current.getService("IndexQueryService", "1.0");
				Occurrences[] elements = service.getIndexedElements(true);
				for (int i = 0; i < elements.length; i++) {
					System.out.println(
						formatString(
							elements[i].getTerm(),
							Integer.toString(elements[i].getOccurrences()),
							50));
				}
				return true;

			} else if (args[0].equalsIgnoreCase("terms")) {
				if (args.length < 3) {
					System.out.println("Usage: terms sequence-start sequence-end");
					return true;
				}
				IndexQueryService service =
					(IndexQueryService) current.getService("IndexQueryService", "1.0");
				Occurrences[] terms = service.scanIndexTerms(args[1], args[2], true);
				System.out.println("Element occurrences in collection " + current.getName());
				System.out.println("--------------------------------------------" + "-----------");
				for (int i = 0; i < terms.length; i++) {
					System.out.println(
						formatString(
							terms[i].getTerm(),
							Integer.toString(terms[i].getOccurrences()),
							50));
				}
			} else if (args[0].equalsIgnoreCase("set")) {
				if (args.length == 1)
					properties.list(System.out);
				else
					try {
						StringTokenizer tok = new StringTokenizer(args[1], "= ");
						if (tok.countTokens() < 2) {
							System.err.println("please specify a key=value pair");
							return true;
						}
						String key = tok.nextToken();
						String val = tok.nextToken();
						System.out.println(key + " = " + val);
						properties.setProperty(key, val);
						getResources();
					} catch (Exception e) {
						System.err.println("Exception: " + e.getMessage());
					}
			} else if (args[0].equalsIgnoreCase("shutdown")) {
				DatabaseInstanceManager mgr =
					(DatabaseInstanceManager) current.getService("DatabaseInstanceManager", "1.0");
				if (mgr == null) {
					System.err.println("service is not available");
					return true;
				}
				mgr.shutdown();
				return true;
			} else if (args[0].equalsIgnoreCase("help") || args[0].equals("?"))
				displayHelp();
			else if (args[0].equalsIgnoreCase("quit")) {
				return false;
			} else {
				System.err.println("unknown command");
				return true;
			}
			path = newPath;
			return true;
		} catch (XMLDBException xde) {
			xde.printStackTrace();
			System.err.println("XMLDBException: " + xde.getMessage() + " [" + xde.errorCode + "]");
			return true;
		}
	}

	private final ResourceSet find(String xpath) throws XMLDBException {
		String sortBy = null;
		int p = xpath.indexOf(" sort by ");
		if (p > -1) {
			String xp = xpath.substring(0, p);
			sortBy = xpath.substring(p + " sort by ".length());
			xpath = xp;
			System.out.println("XPath =   " + xpath);
			System.out.println("Sort-by = " + sortBy);
		}
		XPathQueryServiceImpl service =
			(XPathQueryServiceImpl) current.getService("XPathQueryService", "1.0");
		service.setProperty("pretty", properties.getProperty("indent"));
		service.setProperty("encoding", properties.getProperty("encoding"));
		return (sortBy == null) ? service.query(xpath) : service.query(xpath, sortBy);
	}

	private final void testQuery(String queryFile) {
		try {
			File f = new File(queryFile);
			if (!f.canRead()) {
				System.err.println("can't read query file: " + queryFile);
				return;
			}
			BufferedReader reader = new BufferedReader(new FileReader(f));
			String line;
			ArrayList queries = new ArrayList(10);
			QueryThread thread = null;
			while ((line = reader.readLine()) != null)
				queries.add(line);
			for (int i = 0; i < PARALLEL_THREADS; i++) {
				thread = new QueryThread(queries);
				thread.setName("QueryThread" + i);
				thread.start();
			}
			try {
				thread.join();
			} catch (InterruptedException e) {
			}
		} catch (FileNotFoundException e) {
			System.err.println("ERROR: " + e);
		} catch (IOException e) {
			System.err.println("ERROR: " + e);
		}
	}

	private class QueryThread extends Thread {

		ArrayList queries;

		public QueryThread(ArrayList queries) {
			this.queries = queries;
		}

		public void run() {
			try {
				Collection collection =
					DatabaseManager.getCollection(
						properties.getProperty("uri") + path,
						properties.getProperty("user"),
						properties.getProperty("password"));
				XPathQueryService service =
					(XPathQueryService) current.getService("XPathQueryService", "1.0");
				service.setProperty("pretty", "true");
				service.setProperty("encoding", properties.getProperty("encoding"));
				Random r = new Random(System.currentTimeMillis());
				String query;
				for (int i = 0; i < 10; i++) {
					query = (String) queries.get(r.nextInt(queries.size()));
					System.out.println(getName() + " query: " + query);
					ResourceSet result = service.query(query);
					System.out.println(getName() + " found: " + result.getSize());
				}
			} catch (XMLDBException e) {
				System.err.println("ERROR: " + e.getMessage());
			}
			System.out.println(getName() + " finished.");
		}
	}

	private final XMLResource retrieve(String resource) throws XMLDBException {
		current.setProperty("pretty", properties.getProperty("indent"));
		current.setProperty("encoding", properties.getProperty("encoding"));
		current.setProperty("expand-xincludes", properties.getProperty("expand-xincludes"));
		XMLResource res = (XMLResource) current.getResource(resource);
		if (res == null) {
			messageln("document not found.");
			return null;
		} else
			return res;
	}

	private final void remove(String pattern) throws XMLDBException {
		Collection collection = current;
		if (pattern.startsWith("/")) {
			System.err.println("path pattern should be relative to current collection");
			return;
		}
		Resource resources[];
		XMLResource res = (XMLResource) collection.getResource(pattern);
		if (res == null)
			resources = CollectionScanner.scan(collection, "", pattern);
		else {
			resources = new Resource[1];
			resources[0] = res;
		}
		Collection parent;
		for (int i = 0; i < resources.length; i++) {
			message("removing document " + ((XMLResource) resources[i]).getDocumentId() + " ...");
			parent = resources[i].getParentCollection();
			parent.removeResource(resources[i]);
			messageln("done.");
		}
	}

	private final void xupdate(String resource, String filename)
		throws XMLDBException, IOException {
		File file = new File(filename);
		if (!(file.exists() && file.canRead())) {
			System.out.println("cannot read file " + filename);
			return;
		}
		String commands = XMLUtil.readFile(file, "UTF-8");
		XUpdateQueryService service =
			(XUpdateQueryService) current.getService("XUpdateQueryService", "1.0");
		long modifications = 0;
		if (resource != null)
			modifications = service.update(commands);
		else
			modifications = service.updateResource(resource, commands);
		System.out.println(modifications + " modifications processed " + "successfully.");
	}

	private final void rmcol(String collection) throws XMLDBException {
		CollectionManagementService mgtService =
			(CollectionManagementService) current.getService("CollectionManagementService", "1.0");
		message("removing collection " + collection + " ...");
		mgtService.removeCollection(collection);
		messageln("done.");
	}

	private final File[] findFiles(String pattern) {
		String pathSep = System.getProperty("file.separator", "/");
		String baseDir = ".", globExpr = pattern;
		int p = pattern.lastIndexOf(pathSep);
		if (-1 < p) {
			baseDir = pattern.substring(0, p);
			globExpr = pattern.substring(p + 1);
		}
		messageln("base = " + baseDir + "; glob = " + globExpr);
		File dir = new File(baseDir);
		if (!(dir.isDirectory() && dir.canRead()))
			return null;
		return dir.listFiles((FileFilter) new GlobFilenameFilter(globExpr));
	}

	private final boolean findRecursive(Collection collection, File dir, String base) {
		File temp[] = dir.listFiles();
		if (collection instanceof Observable && verbose) {
			ProgressObserver observer = new ProgressObserver();
			((Observable) collection).addObserver(observer);
		}
		Collection c;
		XMLResource document;
		CollectionManagementService mgtService;
		String next;
		for (int i = 0; i < temp.length; i++) {
			next = base + '/' + temp[i].getName();
			try {
				if (temp[i].isDirectory()) {
					messageln("entering directory " + temp[i].getAbsolutePath());
					c = collection.getChildCollection(temp[i].getName());
					if (c == null) {
						mgtService =
							(CollectionManagementService) collection.getService(
								"CollectionManagementService",
								"1.0");
						c = mgtService.createCollection(temp[i].getName());

					}
					findRecursive(c, temp[i], next);
				} else {
					long start1 = System.currentTimeMillis();
					message(
						"storing document "
							+ temp[i].getName()
							+ " ("
							+ i
							+ " of "
							+ temp.length
							+ ") "
							+ "...");
					document =
						(XMLResource) collection.createResource(temp[i].getName(), "XMLResource");
					document.setContent(temp[i]);
					collection.storeResource(document);
					++filesCount;
					messageln(
						" "
							+ temp[i].length()
							+ " bytes in "
							+ (System.currentTimeMillis() - start1)
							+ "ms.");

				}
			} catch (XMLDBException e) {
				messageln("could not parse file " + temp[i].getAbsolutePath());
			}
		}
		return true;
	}

	private final boolean parse(String fileName) throws XMLDBException {
		fileName = fileName.replace('/', File.separatorChar).replace('\\', File.separatorChar);
		File file = new File(fileName);
		XMLResource document;
		String xml;
		File files[];
		if (file.canRead()) {
			if (file.isDirectory()) {
				if (recurseDirs) {
					filesCount = 0;
					long start = System.currentTimeMillis();
					boolean result = findRecursive(current, file, path);
					messageln(
						"storing "
							+ filesCount
							+ " files took "
							+ ((System.currentTimeMillis() - start) / 1000)
							+ "sec.");
					return result;
				} else
					files = file.listFiles(new XMLFilenameFilter());
			} else {
				files = new File[1];
				files[0] = file;
			}
		} else
			files = DirectoryScanner.scanDir(fileName);
		if (current instanceof Observable && verbose) {
			ProgressObserver observer = new ProgressObserver();
			((Observable) current).addObserver(observer);
		}

		long start;
		long start0 = System.currentTimeMillis();
		long bytes = 0;
		for (int i = 0; i < files.length; i++) {
			start = System.currentTimeMillis();
			document = (XMLResource) current.createResource(files[i].getName(), "XMLResource");
			message(
				"storing document "
					+ files[i].getName()
					+ " ("
					+ (i + 1)
					+ " of "
					+ files.length
					+ ") ...");
			document.setContent(files[i]);
			current.storeResource(document);
			messageln("done.");
			messageln(
				"parsing "
					+ files[i].length()
					+ " bytes took "
					+ (System.currentTimeMillis() - start)
					+ "ms.\n");
			bytes += files[i].length();
		}
		messageln("parsed " + bytes + " bytes in " + (System.currentTimeMillis() - start0) + "ms.");
		return true;
	}

	private final void mkcol(String collPath) throws XMLDBException {
		if (collPath.startsWith("/db"))
			collPath = collPath.substring("/db".length());
		CollectionManagementService mgtService;
		Collection c;
		String p = "/db", token;
		StringTokenizer tok = new StringTokenizer(collPath, "/");
		while (tok.hasMoreTokens()) {
			token = tok.nextToken();
			p = p + '/' + token;
			c =
				DatabaseManager.getCollection(
					properties.getProperty("uri") + p,
					properties.getProperty("user"),
					properties.getProperty("password"));
			if (c == null) {
				mgtService =
					(CollectionManagementService) current.getService(
						"CollectionManagementService",
						"1.0");
				current = mgtService.createCollection(token);
			} else
				current = c;
		}
		path = p;
	}

	// Reads user password from given input stream.
	private char[] readPassword(InputStream in) throws IOException {

		char[] lineBuffer;
		char[] buf;
		int i;

		buf = lineBuffer = new char[128];

		int room = buf.length;
		int offset = 0;
		int c;

		loop : while (true)
			switch (c = in.read()) {
				case -1 :
				case '\n' :
					break loop;
				case '\r' :
					int c2 = in.read();
					if ((c2 != '\n') && (c2 != -1)) {
						if (!(in instanceof PushbackInputStream))
							in = new PushbackInputStream(in);

						((PushbackInputStream) in).unread(c2);
					} else
						break loop;
				default :
					if (--room < 0) {
						buf = new char[offset + 128];
						room = buf.length - offset - 1;
						System.arraycopy(lineBuffer, 0, buf, 0, offset);
						Arrays.fill(lineBuffer, ' ');
						lineBuffer = buf;
					}
					buf[offset++] = (char) c;
					break;
			}

		if (offset == 0)
			return null;

		char[] ret = new char[offset];
		System.arraycopy(buf, 0, ret, 0, offset);
		Arrays.fill(buf, ' ');

		return ret;
	}

	/**
	 *  Main processing method for the InteractiveClient object
	 *
	 *@param  args  Description of the Parameter
	 */
	public void run(String args[]) {
		// read properties
		properties = new Properties(defaultProps);
		try {
			String home = System.getProperty("exist.home");
			File propFile;
			if (home == null)
				propFile = new File("client.properties");
			else
				propFile =
					new File(
						home + System.getProperty("file.separator", "/") + "client.properties");

			InputStream pin;
			if (propFile.canRead())
				pin = new FileInputStream(propFile);
			else
				pin = InteractiveClient.class.getResourceAsStream("client.properties");

			if (pin != null)
				properties.load(pin);

		} catch (IOException ioe) {
		}

		// parse command-line options
		CLArgsParser optParser = new CLArgsParser(args, OPTIONS);

		if (optParser.getErrorString() != null) {
			System.err.println("ERROR: " + optParser.getErrorString());
			return;
		}
		List opt = optParser.getArguments();
		int size = opt.size();
		CLOption option;
		boolean needPasswd = false;
		boolean interactive = true;
		boolean foundCollection = false;
		boolean doParse = false;
		String optionRemove = null;
		String optionGet = null;
		String optionMkcol = null;
		String optionRmcol = null;
		String optionXpath = null;
		String optionQueryFile = null;
		String optionXUpdate = null;
		String optionResource = null;
		List optionalArgs = new ArrayList();
		for (int i = 0; i < size; i++) {
			option = (CLOption) opt.get(i);
			switch (option.getId()) {
				case HELP_OPT :
					printUsage();
					return;
				case GUI_OPT :
					startGUI = true;
					break;
				case QUIET_OPT :
					quiet = true;
					break;
				case VERBOSE_OPT :
					verbose = true;
					break;
				case LOCAL_OPT :
					properties.setProperty("uri", "xmldb:exist://");
					break;
				case USER_OPT :
					properties.setProperty("user", option.getArgument());
					needPasswd = true;
					break;
				case PASS_OPT :
					properties.setProperty("password", option.getArgument());
					needPasswd = false;
					break;
				case CONFIG_OPT :
					properties.setProperty("configuration", option.getArgument());
					break;
				case COLLECTION_OPT :
					path = option.getArgument();
					foundCollection = true;
					break;
				case RESOURCE_OPT :
					optionResource = option.getArgument();
					break;
				case PARSE_OPT :
					doParse = true;
					if (option.getArgumentCount() == 1)
						optionalArgs.add(option.getArgument());
					break;
				case RECURSE_DIRS_OPT :
					recurseDirs = true;
					break;
				case REMOVE_OPT :
					optionRemove = option.getArgument();
					break;
				case GET_OPT :
					optionGet = option.getArgument();
					break;
				case MKCOL_OPT :
					optionMkcol = option.getArgument();
					foundCollection = true;
					break;
				case RMCOL_OPT :
					optionRmcol = option.getArgument();
					foundCollection = true;
					break;
				case FIND_OPT :
					optionXpath = (option.getArgumentCount() == 1 ? option.getArgument() : "stdin");
					break;
				case RESULTS_OPT :
					try {
						maxResults = Integer.parseInt(option.getArgument());
					} catch (NumberFormatException e) {
						System.err.println("parameter -n needs a valid number");
						return;
					}
					break;
				case OPTION_OPT :
					properties.setProperty(option.getArgument(0), option.getArgument(1));
					break;
				case QUERY_FILE_OPT :
					optionQueryFile = option.getArgument();
					break;
				case THREADS_OPT :
					try {
						PARALLEL_THREADS = Integer.parseInt(option.getArgument());
					} catch (NumberFormatException e) {
						System.err.println("parameter -t needs a valid number");
					}
					break;
				case XUPDATE_OPT :
					optionXUpdate = option.getArgument();
					break;
				case CLOption.TEXT_ARGUMENT :
					optionalArgs.add(option.getArgument());
					break;
			}
		}
		String pathSep = System.getProperty("file.separator", "/");
		String home = System.getProperty("exist.home");
		if (home == null)
			home = System.getProperty("user.dir");
		File history = new File(home + File.separatorChar + ".exist_history");
		if (startGUI) {
			frame = new ClientFrame(this, path);
			frame.setLocation(100, 100);
			frame.setSize(500, 450);
			frame.show();
		} else {
			//			initialize Readline library
			try {
				Readline.load(ReadlineLibrary.GnuReadline);
			} catch (UnsatisfiedLinkError ule) {
				if (!quiet) {
					System.out.println("GNU Readline not found. Using System.in.");
					System.out.println("If GNU Readline is available on your system,");
					System.out.println("add directory ./lib to your LD_LIBRARY_PATH");
				}
			}
			Readline.initReadline("exist");
			Readline.setCompleter(new CollectionCompleter()); 
			if (history.canRead())
				try {
					Readline.readHistoryFile(history.getAbsolutePath());
				} catch (Exception e) {
				}
		}
		
		// prompt for password if needed
		if (needPasswd)
			try {
				properties.setProperty("password", Readline.readline("password: "));
			} catch (Exception e) {
			}

		if (!quiet)
			printNotice();
			
		// connect to the db
		try {
			connect();
		} catch (XMLDBException xde) {
			System.err.println("XMLDBException: " + xde.getMessage());
			xde.printStackTrace();
			return;
		} catch (Exception cnf) {
			System.err.println("Cannot connect to database");
			return;
		}
		if (current == null) {
			System.err.println("Could not retrieve collection " + path);
			return;
		}

		// process command-line actions
		if (optionRmcol != null) {
			if (!foundCollection) {
				System.err.println("Please specify target collection with --collection");
				return;
			}
			try {
				rmcol(optionRmcol);
			} catch (XMLDBException e) {
				System.err.println("XMLDBException while removing collection: " + e.getMessage());
				e.printStackTrace();
			}
			interactive = false;
		}
		if (optionMkcol != null) {
			try {
				mkcol(optionMkcol);
			} catch (XMLDBException e) {
				System.err.println("XMLDBException during mkcol: " + e.getMessage());
				e.printStackTrace();
			}
			interactive = false;
		}
		if (optionGet != null) {
			try {
				System.out.println(retrieve(optionGet));
			} catch (XMLDBException e) {
				System.err.println(
					"XMLDBException while trying to retrieve document: " + e.getMessage());
				e.printStackTrace();
			}
			return;
		} else if (optionRemove != null) {
			if (!foundCollection) {
				System.err.println("Please specify target collection with --collection");
				return;
			}
			try {
				remove(optionRemove);
			} catch (XMLDBException e) {
				System.out.println("XMLDBException during parse: " + e.getMessage());
				e.printStackTrace();
			}
			return;
		} else if (doParse) {
			if (!foundCollection) {
				System.err.println("Please specify target collection with --collection");
				return;
			}
			for (Iterator i = optionalArgs.iterator(); i.hasNext();)
				try {
					parse((String) i.next());
				} catch (XMLDBException e) {
					System.out.println("XMLDBException during parse: " + e.getMessage());
					e.printStackTrace();
				}
			return;
		} else if (optionXpath != null) {
			// if no argument has been found, read query from stdin
			if (optionXpath.equals("stdin")) {
				try {
					BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
					optionXpath = stdin.readLine();
				} catch (IOException e) {
					System.err.println("failed to read query from stdin");
					return;
				}
			}
			try {
				ResourceSet result = find(optionXpath);
				for (int i = 0; i < maxResults && i < result.getSize(); i++)
					System.out.println(((Resource) result.getResource((long) i)).getContent());
			} catch (XMLDBException e) {
				System.err.println("XMLDBException during query: " + e.getMessage());
				e.printStackTrace();
			}
			return;
		} else if (optionQueryFile != null) {
			testQuery(optionQueryFile);
		} else if (optionXUpdate != null) {
			try {
				xupdate(optionResource, optionXUpdate);
			} catch (XMLDBException e) {
				System.err.println("XMLDBException during xupdate: " + e.getMessage());
			} catch (IOException e) {
				System.err.println("IOException during xupdate: " + e.getMessage());
			}
		}

		// enter interactive mode
		try {
			getResources();
		} catch (XMLDBException e1) {
			System.out.println(
				"XMLDBException while retrieving collection " + "contents: " + e1.getMessage());
			e1.getCause().printStackTrace();
		}
		messageln("\ntype help or ? for help.");
		if(!startGUI)
			readlineInputLoop(home, history);
		else
			frame.displayPrompt();
	}

	public void readlineInputLoop(String home, File history) {
		String line;
		boolean cont = true;
		while (cont)
			try {
				if (properties.getProperty("colors").equals("true"))
					line = Readline.readline(ANSI_CYAN + "exist:" + path + ">" + ANSI_WHITE);
				else
					line = Readline.readline("exist:" + path + ">");
				if (line != null)
					cont = process(line);

			} catch (EOFException e) {
				break;
			} catch (IOException ioe) {
				System.err.println(ioe);
			}
		try {
			Readline.writeHistoryFile(history.getAbsolutePath());
		} catch (Exception e) {
		}
		Readline.cleanup();
		messageln("quit.");
	}

	private final void printUsage() {
		System.out.println("Usage: java " + InteractiveClient.class.getName() + " [options]");
		System.out.println(CLUtil.describeOptions(OPTIONS).toString());
	}

	public void printNotice() {
		messageln("eXist version 0.9, Copyright (C) 2003 Wolfgang Meier");
		messageln("eXist comes with ABSOLUTELY NO WARRANTY.");
		messageln(
			"This is free software, and you are welcome to "
				+ "redistribute it\nunder certain conditions; "
				+ "for details read the license file.\n");
	}

	private final void message(String msg) {
		if (!quiet) {
			if (startGUI)
				frame.display(msg);
			else
				System.out.print(msg);
		}
	}

	private final void messageln(String msg) {
		if (!quiet) {
			if (startGUI)
				frame.display(msg + '\n');
			else
				System.out.println(msg);
		}
	}

	private class CollectionCompleter implements ReadlineCompleter {

		Iterator possibleValues;

		public String completer(String text, int state) {
			if (state == 0)
				possibleValues = completitions.tailSet(text).iterator();

			if (possibleValues.hasNext()) {
				String nextKey = (String) possibleValues.next();
				if (nextKey.startsWith(text))
					return nextKey;
			}
			return null;
			// we reached the last choice.
		}
	}

	public static class ProgressObserver implements Observer {

		ProgressBar elementsProgress = new ProgressBar("storing elements");
		Observable lastObservable = null;
		ProgressBar parseProgress = new ProgressBar("storing nodes   ");
		ProgressBar wordsProgress = new ProgressBar("storing words   ");

		public void update(Observable o, Object obj) {
			ProgressIndicator ind = (ProgressIndicator) obj;
			if (lastObservable == null || o != lastObservable)
				System.out.println();

			if (o instanceof org.exist.storage.ElementIndex)
				elementsProgress.set(ind.getValue(), ind.getMax());
			else if (o instanceof org.exist.storage.TextSearchEngine)
				wordsProgress.set(ind.getValue(), ind.getMax());
			else
				parseProgress.set(ind.getValue(), ind.getMax());

			lastObservable = o;
		}
	}

	private static String formatString(String s1, String s2, int width) {
		StringBuffer buf = new StringBuffer(width);
		if (s1.length() > width)
			s1 = s1.substring(0, width - 1);
		buf.append(s1);
		int fill = width - (s1.length() + s2.length());
		for (int i = 0; i < fill; i++)
			buf.append(' ');
		buf.append(s2);
		return buf.toString();
	}
}
