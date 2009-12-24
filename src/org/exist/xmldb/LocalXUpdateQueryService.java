package org.exist.xmldb;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.cluster.ClusterComunication;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.MutableDocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;

/**
 * LocalXUpdateQueryService.java
 * 
 * @author Wolfgang Meier
 */
public class LocalXUpdateQueryService implements XUpdateQueryService {

	private final static Logger LOG = 
		Logger.getLogger(LocalXUpdateQueryService.class);

	private BrokerPool pool;
	private User user;
	private LocalCollection parent;
	private XUpdateProcessor processor = null;
	
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
		long start = System.currentTimeMillis();
		MutableDocumentSet docs = new DefaultDocumentSet();
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
		DBBroker broker = null;
		org.exist.collections.Collection c = parent.getCollection();
		try {
			broker = pool.get(user);
			if (resource == null) {
				docs = c.allDocs(broker, docs, true, true);
			} else {
				XmldbURI resourceURI = XmldbURI.xmldbUriFor(resource);
				DocumentImpl doc = c.getDocument(broker, resourceURI);
				if(doc == null) {
                    transact.abort(transaction);
					throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Resource not found: " + resource);
                }
				docs.add(doc);
			}
			if(processor == null)
				processor = new XUpdateProcessor(broker, docs, parent.getAccessContext());
			else {
				processor.setBroker(broker);
				processor.setDocumentSet(docs);
			}
			Modification modifications[] =
				processor.parse(new InputSource(new StringReader(xupdate)));
			long mods = 0;
			for (int i = 0; i < modifications.length; i++) {
				mods += modifications[i].process(transaction);
				broker.flush();
			}
            transact.commit(transaction);

            //Cluster event send
            ClusterComunication clCommunication  = ClusterComunication.getInstance();  //retrieve the cluster communication
            
            //TODO: use xmldbURI?
            if ( clCommunication != null) //if cluster is setting use the cluster communication behaviour
                clCommunication.update( resource, c.getURI().toString(), xupdate );


            LOG.debug("xupdate took " + (System.currentTimeMillis() - start) +
            	"ms.");
			return mods;
		} catch (ParserConfigurationException e) {
            transact.abort(transaction);
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(),e);
		} catch (IOException e) {
            transact.abort(transaction);
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(),e);
		} catch (SAXException e) {
            transact.abort(transaction);
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(),e);
		} catch (URISyntaxException e) {
            transact.abort(transaction);
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(),e);
		} catch (PermissionDeniedException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				e.getMessage());
		} catch (EXistException e) {
            transact.abort(transaction);
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(),e);
		} catch(Exception e) {
            transact.abort(transaction);
			e.printStackTrace();
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(),e);
		} finally {
			if(processor != null)
				processor.reset();
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
