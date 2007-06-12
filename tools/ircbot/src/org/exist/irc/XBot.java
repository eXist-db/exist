package org.exist.irc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.exist.security.Permission;
import org.exist.security.PermissionFactory;
import org.exist.xmldb.UserManagementService;
import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;
import org.jibble.pircbot.PircBot;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * Implements a simple IRC drone, which logs IRC events to a collection in an eXist database.
 * One log file is created every day. Messages are appended using XUpdate.
 * 
 * @author wolf
 *
 */
public class XBot extends PircBot {

	private final static String VERSION = "0.2";
	
	private final static String URI = "xmldb:exist://localhost:8080/xmlrpc/db";
	
	private final static String COLLECTION = "ircbot";
    
    private final static Properties DEFAULTS = new Properties();
    static {
        DEFAULTS.setProperty("xmldb.user", "guest");
        DEFAULTS.setProperty("xmldb.password", "guest");
        DEFAULTS.setProperty("xmldb.uri", URI);
        DEFAULTS.setProperty("xmldb.collection", COLLECTION);
        DEFAULTS.setProperty("irc.server", "irc.freenode.net");
        DEFAULTS.setProperty("irc.channel", "#existdb");
        DEFAULTS.setProperty("irc.nickname", "XDrone");
        DEFAULTS.setProperty("irc.password", "");
    }
	
	private final static String XUPDATE_START =
		"<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">\n" +
		"	<xu:variable name=\"now\" select=\"current-time()\"/>\n";
	
	private final static String URL_REGEX =
		"((http|ftp)s{0,1}://[\\-\\.\\,/\\%\\~\\=\\@\\_\\&\\:\\?\\#a-zA-Z0-9]*[/\\=\\#a-zA-Z0-9])";
	
	// these commands may be passed in a private message to the bot 
	private final Command[] commands = {
		new HelpCommand(),
		new QuitCommand(),
		new FunctionLookup()
	};

    private Properties properties = new Properties(DEFAULTS);
    
	// the base collection
	private Collection collection;

	private DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	
	private Pattern urlPattern;
	private Matcher matcher = null;
	
	public XBot() throws IrcException {
		super();

        File f = new File("xbot.properties");
        if (f.canRead()) {
            System.out.println("Reading properties file: " + f.getAbsolutePath());
            try {
                properties.load(new FileInputStream(f));
            } catch (IOException e) {
                System.err.println("Failed to load properties: " + e.getMessage());
            }
        }
		this.setName(properties.getProperty("irc.nickname"));
		this.setVerbose(true);
		setupDb();
		
		urlPattern = Pattern.compile(URL_REGEX);
	}

	/**
	 * Connect to the server.
	 * 
	 * @throws IrcException
	 * @throws IOException
	 */
	public void connect() throws IrcException, IOException {
		log("Connecting to " + properties.getProperty("irc.server"));
		boolean connected = false;
		while (!connected) {
			try {
				connect(properties.getProperty("irc.server"));
				connected = true;
			} catch (NickAlreadyInUseException e) {
				this.setName(this.getName() + '_');
			}
		}
		log("Join channel: " + properties.getProperty("irc.channel"));
		joinChannel(properties.getProperty("irc.channel"));
		sendMessage("NickServ", "IDENTIFY " + properties.getProperty("irc.password"));
	}
	
	/**
	 * Callback method called after a user has joined the channel.
	 */
	protected void onJoin(String channel, String sender, String login, String hostname) {
		try {
			String xupdate =
				"<join nick=\"" + sender + "\" login=\"" + login + "\" host=\"" + hostname + "\">" +
				"<xu:attribute name=\"time\"><xu:value-of select=\"$now\"/></xu:attribute>" +
				"</join>";
			doUpdate(xupdate);
		} catch (XMLDBException e) {
			log("An error occurred: " + e.getMessage());
		}
	}
	
	/**
	 * Callback method: a user has parted.
	 */
	protected void onPart(String channel, String sender, String login, String hostname) {
		try {
			String xupdate =
				"<part nick=\"" + sender + "\" login=\"" + login + "\" host=\"" + hostname + "\">" +
				"<xu:attribute name=\"time\"><xu:value-of select=\"$now\"/></xu:attribute>" +
				"</part>";
			doUpdate(xupdate);
		} catch (XMLDBException e) {
			log("An error occurred: " + e.getMessage());
		}
	}
	
	/**
	 * Callback method: a user disconnected from the server.
	 */
	protected void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason) {
		try {
			String xupdate =
				"<part nick=\"" + sourceNick + "\" login=\"" + sourceLogin + "\" host=\"" + sourceHostname + 
				"\" reason=\"" + reason + "\">" +
				"<xu:attribute name=\"time\"><xu:value-of select=\"$now\"/></xu:attribute>" +
				"</part>";
			doUpdate(xupdate);
		} catch (XMLDBException e) {
			log("An error occurred: " + e.getMessage());
		}
	}
	
	/**
	 * Callback method: a message was sent.
	 */
	protected void onMessage(String channel, String sender, String login, String hostname, String message) {
		try {
			String xupdate =
				"<message nick=\"" + sender + "\">" +
				"<xu:attribute name=\"time\"><xu:value-of select=\"$now\"/></xu:attribute>" +
				"<![CDATA[" + preprocessMessage(message) + "]]>" +
				"</message>\n";
			doUpdate(xupdate);
		} catch (XMLDBException e) {
			log("An error occurred: " + e.getMessage());
		}
	}
	
	/**
	 * Callback method: a private message has been sent to the bot. Check if it contains a known
	 * command and execute it.
	 */
	protected void onPrivateMessage(String sender, String login, String hostname, String message) {
		String args[] = message.split("\\s+");
		log("Arguments: " + args.length + "; command: " + args[0]);
		boolean recognized = false;
		for (int i = 0; i < commands.length; i++) {
			if (args[0].equalsIgnoreCase(commands[i].name)) {
				// executing command
				try {
					commands[i].execute(sender, args);
				} catch (IrcException e) {
					log("An exception occurred while executing command '" + message + "': " + e.getMessage());
				}
				recognized = true;
			}
		}
		if (!recognized) {
			sendMessage(sender, "Don't know what to respond. Send me a message 'HELP' to see a list of " +
					"commands I understand.");
		}
	}
	
	/**
	 * Helper method to xupdate the log file.
	 * 
	 * @param content
	 * @throws XMLDBException
	 */
	private void doUpdate(String content) throws XMLDBException {
		String xupdate =
			XUPDATE_START +
			"	<xu:append select=\"doc('" + getLogPath() + "')/xlog\">\n" +
			content +
			"	</xu:append>\n" +
			"</xu:modifications>";
        log("XUpdate:\n" + xupdate);
		XUpdateQueryService service = (XUpdateQueryService)
		collection.getService("XUpdateQueryService", "1.0");
		service.update(xupdate);
	}
	
	/**
	 * Parse a message. Tries to detect URLs in the message and transforms them
	 * into an HTML link.
	 * 
	 * @param message
	 * @return
	 */
	private String preprocessMessage(String message) {
		if (matcher == null)
			matcher = urlPattern.matcher(message);
		else
			matcher.reset(message);
		return matcher.replaceAll("]]><a href=\"$1\">$1</a><![CDATA[");
	}
	
	/**
	 * Initialize the database.
	 * 
	 * @throws IrcException
	 */
	private void setupDb() throws IrcException {
		try {
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
            
			collection = DatabaseManager.getCollection(properties.getProperty("xmldb.uri") + '/' + properties.getProperty("xmldb.collection"), 
                    properties.getProperty("xmldb.user"), properties.getProperty("xmldb.password"));
            
			if (collection == null) {
				Collection root = DatabaseManager.getCollection(properties.getProperty("xmldb.uri"), 
                        properties.getProperty("xmldb.user"), properties.getProperty("xmldb.password"));
				CollectionManagementService mgr = (CollectionManagementService)
					root.getService("CollectionManagementService", "1.0");
                UserManagementService umgr = (UserManagementService)
                    root.getService("UserManagementService", "1.0");
				collection = mgr.createCollection(COLLECTION);
                Permission perms = umgr.getPermissions(collection);
                perms.setPermissions(0744);
                umgr.setPermissions(collection, perms);
			}
		} catch (Exception e) {
			throw new IrcException("Failed to initialize the database: " + e.getMessage());
		}
	}
	
	/**
	 * Returns the full database path to the current log document.
	 * 
	 * @return
	 * @throws XMLDBException
	 */
	private String getLogPath() throws XMLDBException {
		return "/db/" + COLLECTION + '/' + getCurrentLog();
	}
	
	/**
	 * Returns the name of the current log document. If no document
	 * has been created for today yet, create a new, empty one.
	 * 
	 * @return
	 * @throws XMLDBException
	 */
	private String getCurrentLog() throws XMLDBException {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		String date = dateFormat.format(cal.getTime());
		String resourceName = date + ".xlog";
		XMLResource res = (XMLResource) collection.getResource(resourceName);
		if (res == null) {
			// create a new log for today's date
			String xml = 
				"<xlog server=\"" + properties.getProperty("irc.server") + "\" channel=\"" + properties.getProperty("irc.channel") + "\" date=\"" + date + "\"/>";
			res = (XMLResource) collection.createResource(resourceName, "XMLResource");
			res.setContent(xml);
			collection.storeResource(res);
            UserManagementService umgr = (UserManagementService)
                collection.getService("UserManagementService", "1.0");
            umgr.setPermissions(res, PermissionFactory.getPermission(properties.getProperty("xmldb.user"), 
                    properties.getProperty("xmldb.password"), 0744));
		}
		return resourceName;
	}
	
	/**
	 * Base class for all commands that can be send in a private message.
	 * 
	 * @author wolf
	 *
	 */
	private abstract class Command {
		String name;
		String description;
		
		public Command(String name, String description) {
			this.name = name;
			this.description = description;
		}
		
		public abstract void execute(String target, String[] args) throws IrcException;
	}
	
	private class HelpCommand extends Command {
		
		public HelpCommand() {
			super("help", "List available commands.");
		}
		
		public void execute(String target, String args[]) throws IrcException {
			sendMessage(target, "XBot " + VERSION + " - Available commands:");
			for (int i = 0; i < commands.length; i++) {
				sendMessage(target, commands[i].name + "\t\t" + commands[i].description);
			}
		}
	}
	
	private class QuitCommand extends Command {
		
		public QuitCommand() {
			super("quit", "Disconnect from the server.");
		}
		
		public void execute(String target, String[] args) throws IrcException {
			if (args.length < 2) {
				sendMessage(target, "Usage: QUIT password");
				return;
			}
			if (!args[1].equals(properties.getProperty("irc.password"))) {
				sendMessage(target, "Wrong password specified!");
				return;
			}
			quitServer("Even a bot needs to rest sometimes...");
			System.exit(0);
		}
	}
	
	private class FunctionLookup extends Command {
		
		public FunctionLookup() {
			super("lookup", "Lookup a function definition");
		}
		
		public void execute(String target, String[] args) throws IrcException {
			try {
				XPathQueryService service = (XPathQueryService)
					collection.getService("XPathQueryService", "1.0");
				ResourceSet result = service.query("util:describe-function('" + args[1] + "')");
				if (result.getSize() == 0) {
					sendMessage(target, "Function " + args[1] + " is unknown!");
					return;
				}
				Node node = ((XMLResource) result.getResource(0)).getContentAsDOM();
				if (node.getNodeType() == Node.DOCUMENT_NODE)
					node = ((Document)node).getDocumentElement();
				NodeList children = ((Element)node).getElementsByTagName("prototype");
				for (int i = 0; i < children.getLength(); i++) {
					Element elem = (Element) children.item(i);
					NodeList nl = elem.getChildNodes();
					for (int j = 0; j < nl.getLength(); j++) {
						node = nl.item(j);
						if (node.getNodeType() == Node.ELEMENT_NODE) {
							if ("signature".equals(node.getLocalName())) {
								sendMessage(target, "[signature] " + getNodeValue(node));
							} else if ("description".equals(node.getLocalName())) {
								sendMessage(target, "[description] " + getNodeValue(node));
							}
						}
					}
				}
			} catch (XMLDBException e) {
				sendMessage(target, "An exception occurred: " + e.getMessage());
			}
		}
		
		private String getNodeValue(Node node) {
			StringBuffer buf = new StringBuffer();
			node = node.getFirstChild();
			while (node != null) {
				buf.append(node.getNodeValue());
				node = node.getNextSibling();
			}
			return buf.toString();
		}
	}
	
	/**
	 * @param args
	 * @throws IrcException 
	 */
	public static void main(String[] args) throws Exception {
		XBot bot = new XBot();
		bot.connect();
	}
}
