package org.exist.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Observable;
import java.util.Stack;
import java.util.StringTokenizer;

import javax.swing.JFrame;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.security.User;
import org.exist.xmldb.UserManagementService;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

/**
 * Restore.java
 * 
 * @author Wolfgang Meier
 */
public class Restore extends DefaultHandler {

	private File contents;
	private String uri;
	private String username;
	private String pass;
	private XMLReader reader;
	private Collection current;
	private Stack stack = new Stack();
	private RestoreDialog dialog = null;

	public final static String NS = "http://exist.sourceforge.net/NS/exist";

	/**
	 * Constructor for Restore.
	 */
	public Restore(String user, String pass, File contents, String uri)
		throws ParserConfigurationException, SAXException {
		this.username = user;
		this.pass = pass;
		this.uri = uri;
		SAXParserFactory saxFactory = SAXParserFactory.newInstance();
		saxFactory.setNamespaceAware(true);
		saxFactory.setValidating(false);
		SAXParser sax = saxFactory.newSAXParser();
		reader = sax.getXMLReader();
		reader.setContentHandler(this);

		stack.push(contents);

		// check if /db/system is in the backup. We have to process
		// this first to create users.
		File dir = contents.getParentFile();
		if (dir.isDirectory() && dir.getName().equals("db")) {
			File sys =
				new File(
					dir.getAbsolutePath()
						+ File.separatorChar
						+ "system"
						+ File.separatorChar
						+ "__contents__.xml");
			// put /db/system on top of the stack
			if (sys.canRead()) {
				System.out.println("found /db/system. It will be processed first.");
				stack.push(sys);
			}
		}
	}

	public Restore(String user, String pass, File contents)
		throws ParserConfigurationException, SAXException {
		this(user, pass, contents, "xmldb:exist://");
	}

	public void restore(boolean showGUI, JFrame parent)
		throws XMLDBException, FileNotFoundException, IOException, SAXException {
		if (showGUI) {
			dialog = new RestoreDialog(parent, "Restoring data ...", false);
			dialog.setVisible(true);
			Thread restoreThread = new Thread() {
				public void run() {
					while (!stack.isEmpty()) {
						try {
							contents = (File) stack.pop();
							reader.parse(new InputSource(new FileInputStream(contents)));
						} catch (FileNotFoundException e) {
							dialog.displayMessage(e.getMessage());
						} catch (IOException e) {
							dialog.displayMessage(e.getMessage());
						} catch (SAXException e) {
							dialog.displayMessage(e.getMessage());
						}
					}
					dialog.setVisible(false);
				}
			};
			restoreThread.start();
			if(parent == null) {
				while (restoreThread.isAlive()) {
					synchronized (this) {
						try {
							wait(20);
						} catch (InterruptedException e) {
						}
					}
				}
			}
		} else {
			while(!stack.isEmpty()) {
				contents = (File) stack.pop();
				String sysId = contents.toURL().toString();
				InputSource is = new InputSource(new FileInputStream(contents));
				is.setSystemId(sysId);
				is.setEncoding("UTF-8");
				System.out.println("restoring " + sysId);
				reader.parse(is);
			}
		}
	}

	/**
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
		throws SAXException {
		if (namespaceURI.equals(NS)) {
			if (localName.equals("collection")) {
				final String name = atts.getValue("name");
				final String owner = atts.getValue("owner");
				final String group = atts.getValue("group");
				final String mode = atts.getValue("mode");
				if (name == null)
					throw new SAXException("collection requires a name " + "attribute");
				try {
					if(dialog != null)
						dialog.displayMessage("creating collection " + name);
					current = mkcol(name);
					UserManagementService service =
						(UserManagementService) current.getService("UserManagementService", "1.0");
					User u = new User(owner, null, group);
					service.chown(u, group);
					service.chmod(Integer.parseInt(mode, 8));
				} catch (XMLDBException e) {
					throw new SAXException(e.getMessage(), e);
				}
				if(dialog != null)
					dialog.setCollection(name);
			} else if (localName.equals("subcollection")) {
				final String name = atts.getValue("name");
				final String fname =
					contents.getParentFile().getAbsolutePath()
						+ File.separatorChar
						+ name
						+ File.separatorChar
						+ "__contents__.xml";
				final File f = new File(fname);
				if (f.exists() && f.canRead())
					stack.push(f);
				else
					System.err.println(f.getAbsolutePath() + " does not exist or is not readable.");
			} else if (localName.equals("resource")) {
				String type = atts.getValue("type");
				if(type == null)
					type ="XMLResource";
				final String name = atts.getValue("name");
				final String owner = atts.getValue("owner");
				final String group = atts.getValue("group");
				final String perms = atts.getValue("mode");
				if (name == null)
					throw new SAXException("collection requires a name attribute");
				final File f =
					new File(
						contents.getParentFile().getAbsolutePath() + File.separatorChar + name);
				try {
					if (dialog != null && current instanceof Observable) {
						((Observable) current).addObserver(dialog.getObserver());
					}
					if(dialog != null)
						dialog.setResource(name);
					final Resource res =
						current.createResource(name, type);
					res.setContent(f);
					if(dialog == null)
						System.out.println("restoring " + name);
					current.storeResource(res);
					UserManagementService service =
						(UserManagementService) current.getService("UserManagementService", "1.0");
					User u = new User(owner, null, group);
					service.chown(res, u, group);
					service.chmod(res, Integer.parseInt(perms, 8));
					if(dialog != null)
						dialog.displayMessage("restored " + name);
				} catch (XMLDBException e) {
					throw new SAXException(e);
				}
			}
		}
	}

	private final Collection mkcol(String collPath) throws XMLDBException {
		if (collPath.startsWith("/db"))
			collPath = collPath.substring("/db".length());
		CollectionManagementService mgtService;
		Collection c;
		Collection current = DatabaseManager.getCollection(uri + "/db", username, pass);
		String p = "/db", token;
		StringTokenizer tok = new StringTokenizer(collPath, "/");
		while (tok.hasMoreTokens()) {
			token = tok.nextToken();
			p = p + '/' + token;
			c = DatabaseManager.getCollection(uri + p, username, pass);
			if (c == null) {
				mgtService =
					(CollectionManagementService) current.getService(
						"CollectionManagementService",
						"1.0");
				current = mgtService.createCollection(token);
			} else
				current = c;
		}
		return current;
	}
}