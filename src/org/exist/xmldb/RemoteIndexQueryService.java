/*
 * RemoteIndexQueryService.java - Mar 28, 2003
 * 
 * @author wolf
 */
package org.exist.xmldb;

import java.io.IOException;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.exist.util.Occurrences;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 *
 * To change this generated comment go to 
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class RemoteIndexQueryService implements IndexQueryService {

	protected XmlRpcClient rpcClient = null;
	protected CollectionImpl parent;

	public RemoteIndexQueryService(XmlRpcClient client, CollectionImpl parent) {
		this.rpcClient = client;
		this.parent = parent;
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.IndexQueryService#getIndexedElements(boolean)
	 */
	public Occurrences[] getIndexedElements(boolean inclusive) throws XMLDBException {
		try {
			Vector params = new Vector();
			params.addElement(parent.getPath());
			params.addElement(new Boolean(inclusive));
			Vector result = (Vector) rpcClient.execute("getIndexedElements", params);
			Occurrences occurrences[] = new Occurrences[result.size()];
			Vector row;
			for (int i = 0; i < occurrences.length; i++) {
				row = (Vector) result.elementAt(i);
				occurrences[i] = new Occurrences((String) row.elementAt(0));
				occurrences[i].addOccurrences(((Integer) row.elementAt(1)).intValue());
			}
			return occurrences;
		} catch (XmlRpcException e) {
			throw new XMLDBException(
				ErrorCodes.UNKNOWN_ERROR,
				"xmlrpc error while retrieving indexed elements",
				e);
		} catch (IOException e) {
			throw new XMLDBException(
				ErrorCodes.UNKNOWN_ERROR,
				"io error while retrieving indexed elements",
				e);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.IndexQueryService#scanIndexTerms(java.lang.String, java.lang.String, boolean)
	 */
	public Occurrences[] scanIndexTerms(String start, String end, boolean inclusive)
		throws XMLDBException {
		try {
			Vector params = new Vector();
			params.addElement(parent.getPath());
			params.addElement(start);
			params.addElement(end);
			params.addElement(new Boolean(inclusive));
			Vector result = (Vector) rpcClient.execute("scanIndexTerms", params);
			Occurrences occurrences[] = new Occurrences[result.size()];
			Vector row;
			for (int i = 0; i < occurrences.length; i++) {
				row = (Vector) result.elementAt(i);
				occurrences[i] = new Occurrences((String) row.elementAt(0));
				occurrences[i].addOccurrences(((Integer) row.elementAt(1)).intValue());
			}
			return occurrences;
		} catch (XmlRpcException e) {
			throw new XMLDBException(
				ErrorCodes.UNKNOWN_ERROR,
				"xmlrpc error while retrieving indexed elements",
				e);
		} catch (IOException e) {
			throw new XMLDBException(
				ErrorCodes.UNKNOWN_ERROR,
				"io error while retrieving indexed elements",
				e);
		}
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Service#getName()
	 */
	public String getName() throws XMLDBException {
		return "IndexQueryService";
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Service#getVersion()
	 */
	public String getVersion() throws XMLDBException {
		return "1.0";
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Service#setCollection(org.xmldb.api.base.Collection)
	 */
	public void setCollection(Collection col) throws XMLDBException {
		this.parent = (CollectionImpl) col;
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Configurable#getProperty(java.lang.String)
	 */
	public String getProperty(String name) throws XMLDBException {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Configurable#setProperty(java.lang.String, java.lang.String)
	 */
	public void setProperty(String name, String value) throws XMLDBException {
	}

}
