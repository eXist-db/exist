/**
 *  AdminSoapBindingImpl.java This file was auto-generated from WSDL by the
 *  Apache Axis Wsdl2java emitter.
 */

package org.exist.soap;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import java.rmi.RemoteException;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Category;
import org.exist.EXistException;
import org.exist.Parser;
import org.exist.collections.Collection;
import org.exist.dom.DocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xpath.XPathException;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 *@created    August 2, 2002
 */
public class AdminSoapBindingImpl implements org.exist.soap.Admin {

	private static Category LOG = Category.getInstance(Admin.class.getName());

	private BrokerPool pool;

	/**  Constructor for the AdminSoapBindingImpl object */
	public AdminSoapBindingImpl() {
		try {
			pool = BrokerPool.getInstance();
		} catch (Exception e) {
			throw new RuntimeException("failed to initialize broker pool");
		}
	}

	public String connect(String user, String password)
		throws java.rmi.RemoteException {
		User u = pool.getSecurityManager().getUser(user);
		if (u == null)
			throw new RemoteException("user " + user + " does not exist");
		if (!u.validate(password))
			throw new RemoteException("the supplied password is invalid");
		LOG.debug("user " + user + " connected");
		return SessionManager.getInstance().createSession(u);
	}

	public void disconnect(String id) throws RemoteException {
		SessionManager manager = SessionManager.getInstance();
		Session session = manager.getSession(id);
		if (session != null) {
			LOG.debug("disconnecting session " + id);
			manager.disconnect(id);
		}
	}

	public boolean createCollection(String sessionId, String collection)
		throws RemoteException {
		Session session = SessionManager.getInstance().getSession(sessionId);
		DBBroker broker = null;
		try {
			broker = pool.get(session.getUser());
			LOG.debug("creating collection " + collection);
			org.exist.collections.Collection coll =
				broker.getOrCreateCollection(collection);
			if (coll == null) {
				LOG.debug("failed to create collection");
				return false;
			}
			broker.saveCollection(coll);
			broker.flush();
			broker.sync();
			return true;
		} catch (Exception e) {
			LOG.debug(e);
			throw new RemoteException(e.getMessage());
		} finally {
			pool.release(broker);
		}
	}

	public boolean removeCollection(String sessionId, String collection)
		throws RemoteException {
		Session session = SessionManager.getInstance().getSession(sessionId);
		DBBroker broker = null;
		try {
			broker = pool.get(session.getUser());
			if (broker.getCollection(collection) == null)
				return false;
			return broker.removeCollection(collection);
		} catch (Exception e) {
			LOG.debug(e.getMessage(), e);
			throw new RemoteException(e.getMessage(), e);
		} finally {
			pool.release(broker);
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  path                 Description of the Parameter
	 *@return                      Description of the Return Value
	 *@exception  RemoteException  Description of the Exception
	 */
	public boolean removeDocument(String sessionId, String path)
		throws RemoteException {
		Session session = SessionManager.getInstance().getSession(sessionId);
		DBBroker broker = null;
		try {
			broker = pool.get(session.getUser());
			int p = path.lastIndexOf('/');
			if (p < 0 || p == path.length() - 1)
				throw new EXistException("Illegal document path");
			String collectionName = path.substring(0, p);
			path = path.substring(p + 1);
			Collection collection = broker.getCollection(collectionName);
			if (collection == null)
				throw new EXistException(
					"Collection " + collectionName + " not found");
            collection.removeDocument(broker, path);
			return true;
		} catch (Exception e) {
			LOG.debug(e.getMessage(), e);
			throw new RemoteException(e.getMessage(), e);
		} finally {
			pool.release(broker);
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  data                 Description of the Parameter
	 *@param  encoding             Description of the Parameter
	 *@param  path                 Description of the Parameter
	 *@param  replace              Description of the Parameter
	 *@exception  RemoteException  Description of the Exception
	 */
	public void store(
		String sessionId,
		byte[] data,
		java.lang.String encoding,
		java.lang.String path,
		boolean replace)
		throws RemoteException {
		Session session = SessionManager.getInstance().getSession(sessionId);
		DBBroker broker = null;
		try {
			broker = pool.get(session.getUser());
			if (broker.getDocument(path) != null) {
				if (!replace)
					throw new RemoteException(
						"document "
							+ path
							+ " exists and parameter replace is set to false.");
			}
			String xml;
			try {
				xml = new String(data, encoding);
			} catch (UnsupportedEncodingException e) {
				throw new RemoteException(e.getMessage());
			}
			long startTime = System.currentTimeMillis();
			Parser parser = new Parser(broker, session.getUser(), true);
			Document doc = parser.parse(xml, path);
			LOG.debug("flushing data files");
			broker.flush();
			LOG.debug(
				"parsing "
					+ path
					+ " took "
					+ (System.currentTimeMillis() - startTime)
					+ "ms.");
		} catch (Exception e) {
			LOG.debug(e);
			throw new RemoteException(e.getMessage(), e);
		} finally {
			pool.release(broker);
		}
	}

	private Session getSession(String id) throws java.rmi.RemoteException {
		Session session = SessionManager.getInstance().getSession(id);
		if (session == null)
			throw new java.rmi.RemoteException(
				"Session is invalid or timed out");
		return session;
	}

	/* (non-Javadoc)
	 * @see org.exist.soap.Admin#xupdate(java.lang.String, java.lang.String)
	 */
	public int xupdate(String sessionId, String collectionName, String xupdate)
		throws RemoteException {
		DBBroker broker = null;
		Session session = getSession(sessionId);
		try {
			broker = pool.get(session.getUser());
			Collection collection = broker.getCollection(collectionName);
			if (collection == null)
				throw new RemoteException(
					"collection " + collectionName + " not found");
			DocumentSet docs =
				collection.allDocs(broker, new DocumentSet(), true);
			XUpdateProcessor processor =
				new XUpdateProcessor(broker, docs);
			Modification modifications[] =
				processor.parse(new InputSource(new StringReader(xupdate)));
			long mods = 0;
			for (int i = 0; i < modifications.length; i++) {
				mods += modifications[i].process();
				broker.flush();
			}
			return (int) mods;
		} catch (ParserConfigurationException e) {
			throw new RemoteException(e.getMessage(), e);
		} catch (IOException e) {
			throw new RemoteException(e.getMessage(), e);
		} catch (EXistException e) {
			throw new RemoteException(e.getMessage(), e);
		} catch (SAXException e) {
			throw new RemoteException(e.getMessage(), e);
		} catch (PermissionDeniedException e) {
			throw new RemoteException(e.getMessage(), e);
		} catch (XPathException e) {
			throw new RemoteException(e.getMessage(), e);
		} finally {
			pool.release(broker);
		}
	}

	/* (non-Javadoc)
		 * @see org.exist.soap.Admin#xupdate(java.lang.String, java.lang.String)
		 */
	public int xupdateResource(
		String sessionId,
		String documentName,
		String xupdate)
		throws RemoteException {
		DBBroker broker = null;
		Session session = getSession(sessionId);
		try {
			broker = pool.get(session.getUser());
			Document doc = broker.getDocument(documentName);
			if (doc == null)
				throw new RemoteException(
					"document " + documentName + " not found");
			DocumentSet docs = new DocumentSet();
			docs.add(doc);
			XUpdateProcessor processor =
				new XUpdateProcessor(broker, docs);
			Modification modifications[] =
				processor.parse(new InputSource(new StringReader(xupdate)));
			long mods = 0;
			for (int i = 0; i < modifications.length; i++) {
				mods += modifications[i].process();
				broker.flush();
			}
			return (int) mods;
		} catch (ParserConfigurationException e) {
			throw new RemoteException(e.getMessage(), e);
		} catch (IOException e) {
			throw new RemoteException(e.getMessage(), e);
		} catch (EXistException e) {
			throw new RemoteException(e.getMessage(), e);
		} catch (SAXException e) {
			throw new RemoteException(e.getMessage(), e);
		} catch (PermissionDeniedException e) {
			throw new RemoteException(e.getMessage(), e);
		} catch (XPathException e) {
			throw new RemoteException(e.getMessage(), e);
		} finally {
			pool.release(broker);
		}
	}
}
