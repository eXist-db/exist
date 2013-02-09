 /*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001 Wolfgang M. Meier
 * meier@ifs.tu-darmstadt.de
 * http://exist.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.exist.xmldb;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

public class RemoteResourceIterator implements ResourceIterator {

	protected XmlRpcClient rpcClient;
	protected RemoteCollection collection;
	protected Vector<Object> resources;
	protected int pos = 0 ;
	protected int indentXML;
	protected String encoding = "UTF-8";

	public RemoteResourceIterator(RemoteCollection col, Vector<Object> resources, 
								int indentXML, String encoding) {
		this.resources = resources;
		this.collection = col;
		this.indentXML = indentXML;
		this.encoding = encoding;
	}

	public int getLength() {
		return resources.size();
	}

	public boolean hasMoreResources() throws XMLDBException {
		return pos < resources.size();
	}

    public void setNext(int next) {
        pos = next;
    }
    
	@SuppressWarnings("unchecked")
	public Resource nextResource() throws XMLDBException {
        if(pos >= resources.size())
            {return null;}
        // node or value?
        if(resources.elementAt(pos) instanceof Vector<?>) {
            // node
            final Vector<String> v = (Vector<String>)resources.elementAt(pos++);
            final String doc = v.elementAt(0);
            final String s_id = v.elementAt(1);
            
            final Vector<Object> params = new Vector<Object>();
            params.addElement(doc);
            params.addElement(s_id);
            params.addElement(Integer.valueOf(indentXML));
            params.addElement(encoding);
            try {
                final byte[] data = (byte[])collection.getClient().execute("retrieve", params);
                final XMLResource res = new RemoteXMLResource(collection, XmldbURI.xmldbUriFor(doc), doc + "_" + s_id);
                res.setContent(new String(data, encoding));
                return res;
            } catch(final XmlRpcException xre) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
            } catch(final IOException ioe) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
			} catch (final URISyntaxException e) {
                throw new XMLDBException(ErrorCodes.INVALID_URI, e.getMessage(), e);
			}
        } else {
            // value
            final XMLResource res = new RemoteXMLResource(collection, null, Integer.toString(pos));
            res.setContent(resources.elementAt(pos++));
            return res;
        }
    }
}
	
