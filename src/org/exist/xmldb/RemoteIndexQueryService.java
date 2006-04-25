/*
 * eXist Open Source Native XML Database
 *   
 * Copyright (C) 2001-04 Wolfgang M. Meier wolfgang@exist-db.org
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id$
 */
package org.exist.xmldb;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.exist.dom.QName;
import org.exist.util.Occurrences;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

public class RemoteIndexQueryService implements IndexQueryService {

	protected XmlRpcClient rpcClient = null;
	protected RemoteCollection parent;

	public RemoteIndexQueryService(XmlRpcClient client, RemoteCollection parent) {
		this.rpcClient = client;
		this.parent = parent;
	}

	
    /** @see org.exist.xmldb.IndexQueryService#reindexCollection() */
    public void reindexCollection() throws XMLDBException {
    	reindexCollection( parent.getPath() );
    }
    
    
    /* (non-Javadoc)
     * @see org.exist.xmldb.IndexQueryService#reindexCollection(java.lang.String)
     */
    public void reindexCollection(String collectionPath) throws XMLDBException {
    	try{
    		reindexCollection(XmldbURI.xmldbUriFor(collectionPath));
    	} catch(URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }
        /* (non-Javadoc)
         * @see org.exist.xmldb.IndexQueryService#reindexCollection(java.lang.String)
         */
   public void reindexCollection(XmldbURI collectionPath) throws XMLDBException {
       if (parent != null)
    	   collectionPath = parent.getPathURI().resolveCollectionPath(collectionPath);        
		Vector params = new Vector();
		params.addElement(collectionPath.toString());
		try {
			rpcClient.execute("reindexCollection", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(
					ErrorCodes.UNKNOWN_ERROR,
					"xmlrpc error while doing reindexCollection: ", e);
		} catch (IOException e) {
			throw new XMLDBException(
					ErrorCodes.UNKNOWN_ERROR,
					"xmlrpc error while doing reindexCollection: ", e);
		}
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xmldb.IndexQueryService#getIndexedElements(boolean)
	 */
	public Occurrences[] getIndexedElements(boolean inclusive) throws XMLDBException {
		try {
			Vector params = new Vector();
			params.addElement(parent.getPath());
			params.addElement(Boolean.valueOf(inclusive));
			Vector result = (Vector) rpcClient.execute("getIndexedElements", params);
			Occurrences occurrences[] = new Occurrences[result.size()];
			Vector row;
			for (int i = 0; i < occurrences.length; i++) {
				row = (Vector) result.elementAt(i);
				QName qname = new QName((String)row.elementAt(0), (String)row.elementAt(1),
						(String)row.elementAt(2));
				occurrences[i] = new Occurrences(qname);
				occurrences[i].addOccurrences(((Integer) row.elementAt(3)).intValue());
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
			params.addElement(Boolean.valueOf(inclusive));
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
	 * @see org.exist.xmldb.IndexQueryService#scanIndexTerms(java.lang.String, java.lang.String, java.lang.String)
	 */
	public Occurrences[] scanIndexTerms(String xpath, String start, String end) throws XMLDBException {
		try {
			Vector params = new Vector();
			params.addElement(xpath);
			params.addElement(start);
			params.addElement(end);
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
		this.parent = (RemoteCollection) col;
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


	/* (non-Javadoc)
	 * @see org.exist.xmldb.IndexQueryService#configureCollection(java.lang.String)
	 */
	public void configureCollection(String configData) throws XMLDBException {
	    String path = parent.getPath();
		Vector params = new Vector();
		params.addElement(path);
		params.addElement(configData);
		try {
			rpcClient.execute("configureCollection", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(
					ErrorCodes.UNKNOWN_ERROR,
					"xmlrpc error while doing reindexCollection: ", e);
		} catch (IOException e) {
			throw new XMLDBException(
					ErrorCodes.UNKNOWN_ERROR,
					"xmlrpc error while doing reindexCollection: ", e);
		}		
	}

}
