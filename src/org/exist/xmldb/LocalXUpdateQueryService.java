package org.exist.xmldb;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.ParserConfigurationException;

import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * LocalXUpdateQueryService.java
 * 
 * @author Wolfgang Meier
 */
public class LocalXUpdateQueryService implements XUpdateQueryService {

	private BrokerPool pool;
	private User user;
	private LocalCollection parent;

	/**
	 * Constructor for LocalXUpdateQueryService.
	 */
	public LocalXUpdateQueryService(
		User user,
		BrokerPool pool,
		LocalCollection parent) {
		this.pool = pool;
		this.user = user;
		this.parent = parent;
	}

	/**
	 * @see org.xmldb.api.modules.XUpdateQueryService#updateResource(java.lang.String, java.lang.String)
	 */
	public long updateResource(String resource, String xupdate)
		throws XMLDBException {
		DocumentSet docs = null;
		DBBroker broker = null;
		try {
			broker = pool.get();
			if (resource == null) {
				docs = parent.collection.allDocs(user);
				System.out.println("searching " + docs.getLength() + " docs.");
			} else {
				docs = new DocumentSet();
				String id = parent.getName() + '/' + resource;
				DocumentImpl doc = parent.collection.getDocument(id);
				docs.add(doc);
			}
			XUpdateProcessor processor = new XUpdateProcessor(pool, user);
			Modification modifications[] =
				processor.parse(new InputSource(new StringReader(xupdate)));
			long mods = 0;
			for (int i = 0; i < modifications.length; i++) {
				mods += modifications[i].process(docs);
			}
			broker.flush();
            broker.sync();
			return mods;
		} catch (ParserConfigurationException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage());
		} catch (IOException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage());
		} catch (SAXException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage());
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				e.getMessage());
		} catch (EXistException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage());
		} finally {
			pool.release(broker);
		}
	}

	/**
	 * * @see org.xmldb.api.modules.XUpdateQueryService#update(java.lang.String)
	 */
	public long update(String arg1) throws XMLDBException {
		return updateResource(null, arg1);
	}

	/**
	 * @see org.xmldb.api.base.Service#getName()
	 */
	public String getName() throws XMLDBException {
		return "XUpdateQueryService";
	}

	/**
	 * @see org.xmldb.api.base.Service#getVersion()
	 */
	public String getVersion() throws XMLDBException {
		return "1.0";
	}

	/**
	 * @see org.xmldb.api.base.Service#setCollection(org.xmldb.api.base.Collection)
	 */
	public void setCollection(Collection arg0) throws XMLDBException {
	}

	/**
	 * @see org.xmldb.api.base.Configurable#getProperty(java.lang.String)
	 */
	public String getProperty(String arg0) throws XMLDBException {
		return null;
	}

	/**
	 * @see org.xmldb.api.base.Configurable#setProperty(java.lang.String, java.lang.String)
	 */
	public void setProperty(String arg0, String arg1) throws XMLDBException {
	}

}
