package org.exist.soap;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.rmi.RemoteException;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.sync.Sync;
import org.exist.util.LockException;
import org.exist.xquery.XPathException;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *  Provides the actual implementations for the methods defined in
 * {@link org.exist.soap.Admin}.
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 */
public class AdminSoapBindingImpl implements org.exist.soap.Admin {

	private static Logger LOG = Logger.getLogger(Admin.class.getName());

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
		Session session = getSession(sessionId);
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
			broker.sync(Sync.MINOR_SYNC);
			return true;
		} catch (Exception e) {
			LOG.debug(e.getMessage(), e);
			throw new RemoteException(e.getMessage());
		} finally {
			pool.release(broker);
		}
	}

	public boolean removeCollection(String sessionId, String name)
		throws RemoteException {
		Session session = getSession(sessionId);
		DBBroker broker = null;
		try {
			broker = pool.get(session.getUser());
			Collection collection = broker.getCollection(name);
			if(collection == null)
				return false;
			return broker.removeCollection(collection);
		} catch (Exception e) {
			LOG.debug(e.getMessage(), e);
			throw new RemoteException(e.getMessage(), e);
		} finally {
			pool.release(broker);
		}
	}

	public boolean removeDocument(String sessionId, String path)
		throws RemoteException {
		Session session = getSession(sessionId);
		DBBroker broker = null;
		try {
			broker = pool.get(session.getUser());
			int p = path.lastIndexOf('/');
			if (p < 0 || p == path.length() - 1)
				throw new EXistException("Illegal document path");
			String collectionName = path.substring(0, p);
			String docName = path.substring(p + 1);
			Collection collection = broker.getCollection(collectionName);
			if (collection == null)
				throw new EXistException(
					"Collection " + collectionName + " not found");
			DocumentImpl doc = collection.getDocument(broker, docName);
			if(doc == null)
				throw new EXistException("Document " + docName + " not found");
			if(doc.getResourceType() == DocumentImpl.BINARY_FILE)
				collection.removeBinaryResource(broker, doc);
			else
				collection.removeDocument(broker, docName);
			return true;
		} catch (Exception e) {
			LOG.debug(e.getMessage(), e);
			throw new RemoteException(e.getMessage(), e);
		} finally {
			pool.release(broker);
		}
	}

	public void store(
		String sessionId,
		byte[] data,
		java.lang.String encoding,
		java.lang.String path,
		boolean replace)
		throws RemoteException {
		Session session = getSession(sessionId);
		DBBroker broker = null;
		try {
			broker = pool.get(session.getUser());
			int p = path.lastIndexOf('/');
			if (p < 0 || p == path.length() - 1)
				throw new RemoteException("Illegal document path");
			String collectionName = path.substring(0, p);
			path = path.substring(p + 1);
			Collection collection = broker.getCollection(collectionName);
			if (collection == null)
				throw new EXistException("Collection " + collectionName + " not found");
			if(!replace) {
				DocumentImpl old = collection.getDocument(broker, path);
				if(old != null)
					throw new RemoteException("Document exists and overwrite is not allowed");
			}
			long startTime = System.currentTimeMillis();
			DocumentImpl doc =
				collection.addDocument(
						broker,
						path,
						new InputSource(new ByteArrayInputStream(data)),
                        "text/xml");
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
				collection.allDocs(broker, new DocumentSet(), true, true);
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
		} catch (LockException e) {
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
			DocumentImpl doc = (DocumentImpl)broker.getDocument(documentName);
			if (doc == null)
				throw new RemoteException(
					"document " + documentName + " not found");
			if(!doc.getPermissions().validate(broker.getUser(), Permission.READ))
				throw new RemoteException("Not allowed to read resource");
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
		} catch (LockException e) {
		    throw new RemoteException(e.getMessage(), e);
        } finally {
			pool.release(broker);
		}
	}
}
