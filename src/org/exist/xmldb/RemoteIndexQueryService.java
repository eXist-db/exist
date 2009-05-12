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

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
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
    
    
    /** (non-Javadoc)
     * @deprecated Use XmldbURI version instead
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
            List params = new ArrayList(1);
		params.add(collectionPath.toString());
		try {
			rpcClient.execute("reindexCollection", params);
		} catch (XmlRpcException e) {
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
            List params = new ArrayList(1);
			params.add(parent.getPath());
			params.add(Boolean.valueOf(inclusive));
			Object[] result = (Object[]) rpcClient.execute("getIndexedElements", params);
			Occurrences occurrences[] = new Occurrences[result.length];
			Object[] row;
			for (int i = 0; i < occurrences.length; i++) {
				row = (Object[]) result[i];
				QName qname = new QName((String)row[0], (String)row[1],
						(String)row[2]);
				occurrences[i] = new Occurrences(qname);
				occurrences[i].addOccurrences(((Integer) row[3]).intValue());
			}
			return occurrences;
		} catch (XmlRpcException e) {
			throw new XMLDBException(
				ErrorCodes.UNKNOWN_ERROR,
				"xmlrpc error while retrieving indexed elements",
				e);
		}
    }

	/* (non-Javadoc)
	 * @see org.exist.xmldb.IndexQueryService#scanIndexTerms(java.lang.String, java.lang.String, boolean)
	 */
	public Occurrences[] scanIndexTerms(String start, String end, boolean inclusive)
		throws XMLDBException {
		try {
            List params = new ArrayList(1);
			params.add(parent.getPath());
			params.add(start);
			params.add(end);
			params.add(Boolean.valueOf(inclusive));
			Object[] result = (Object[]) rpcClient.execute("scanIndexTerms", params);
			Occurrences occurrences[] = new Occurrences[result.length];
			Object[] row;
			for (int i = 0; i < occurrences.length; i++) {
				row = (Object[]) result[i];
				occurrences[i] = new Occurrences((String) row[0]);
				occurrences[i].addOccurrences(((Integer) row[1]).intValue());
			}
			return occurrences;
		} catch (XmlRpcException e) {
			throw new XMLDBException(
				ErrorCodes.UNKNOWN_ERROR,
				"xmlrpc error while retrieving indexed elements",
				e);
		}
    }

	/* (non-Javadoc)
	 * @see org.exist.xmldb.IndexQueryService#scanIndexTerms(java.lang.String, java.lang.String, java.lang.String)
	 */
	public Occurrences[] scanIndexTerms(String xpath, String start, String end) throws XMLDBException {
		try {
            List params = new ArrayList(1);
			params.add(xpath);
			params.add(start);
			params.add(end);
			Object[] result = (Object[]) rpcClient.execute("scanIndexTerms", params);
			Occurrences occurrences[] = new Occurrences[result.length];
			Object[] row;
			for (int i = 0; i < occurrences.length; i++) {
				row = (Object[]) result[i];
				occurrences[i] = new Occurrences((String) row[0]);
				occurrences[i].addOccurrences(((Integer) row[1]).intValue());
			}
			return occurrences;
		} catch (XmlRpcException e) {
			throw new XMLDBException(
				ErrorCodes.UNKNOWN_ERROR,
				"xmlrpc error while retrieving indexed elements",
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
        List params = new ArrayList(1);
		params.add(path);
		params.add(configData);
		try {
			rpcClient.execute("configureCollection", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(
					ErrorCodes.UNKNOWN_ERROR,
					"xmlrpc error while doing reindexCollection: ", e);
		}
    }

}
