/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2011 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.debuggee.dbgp.packets;

import org.apache.mina.core.session.IoSession;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.xquery.Variable;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class PropertyGet extends Command {

	/**
	 * -d stack depth (optional, debugger engine should assume zero if not provided)
	 */
	private int stackDepth = 0;
			   
	/**
	 * -c context id (optional, retrieved by context-names, debugger engine should assume zero if not provided)
	 */
	private int contextID = 0;
			   
	/**
	 * -n property long name (required)
	 */
	private String nameLong;
			
	/**
	 * -m max data size to retrieve (optional)
	 */
	private Integer maxDataSize = null;
			
	/**
	 * -p data page (property_get, property_value: optional for arrays, hashes, objects, etc.; property_set: not required; debugger engine should assume zero if not provided)
	 */
	private String dataPage = null;
			   
	/**
	 * -k property key as retrieved in a property element, optional, used for property_get of children and property_value, required if it was provided by the debugger engine.
	 */
	private String propertyKey = null;
	
	/**
	 * -a property address as retrieved in a property element, optional, used for property_set/value
	 */
	private String propertyAddress = null;
	
	private Variable variable;
	
	public PropertyGet(IoSession session, String args) {
		super(session, args);
	}

	protected void setArgument(String arg, String val) {
		if (arg.equals("d"))
			stackDepth = Integer.parseInt(val);
		
		else if (arg.equals("c"))
			contextID = Integer.parseInt(val);
		
		else if (arg.equals("n"))
			nameLong = val;
		
		else if (arg.equals("m"))
			maxDataSize = Integer.parseInt(val);
		
		else if (arg.equals("p"))
			dataPage = val;
		
		else if (arg.equals("k"))
			propertyKey = val;
		
		else if (arg.equals("a"))
			propertyAddress = val;
		
		else
			super.setArgument(arg, val);
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.dgbp.packets.Command#exec()
	 */
	@Override
	public void exec() {
		variable = getJoint().getVariable(nameLong);
	}

	public byte[] responseBytes() {
		if (variable == null)
			return errorBytes("property_get");
		
		StringBuilder responce = new StringBuilder();
		responce.append(xml_declaration);
		responce.append("<response " +
				namespaces +
				"command=\"property_get\" transaction_id=\"");
		responce.append(transactionID);
		responce.append("\">");
		responce.append(getPropertyString(variable, getJoint().getContext()));
		responce.append("</response>");

		return responce.toString().getBytes();
	}

	protected static StringBuilder getPropertyString(Variable variable, XQueryContext context) {
        Sequence value = variable.getValue();

        Database db = context.getDatabase();
        try(final DBBroker broker = db.getBroker()) {

	        Serializer serializer = broker.getSerializer();
	        serializer.reset();
	
	        try {
	        	StringBuilder property = new StringBuilder();
	            if (value.hasOne()) {
	                String strVal = getPropertyValue(value.itemAt(0), serializer);
	                String type = Type.subTypeOf(value.getItemType(), Type.NODE) ? "node" :
	                    Type.getTypeName(value.getItemType());
	                property.append("<property name=\"");
	                property.append(variable.getQName().toString());
	                property.append("\" fullname=\"");
	                property.append(variable.getQName().toString());
	                property.append("\" type=\"");
	                property.append(type);
	                property.append("\" size=\"");
	                property.append(strVal.length());
	                property.append("\" encoding=\"none\">");
	                property.append(strVal);
	                property.append("</property>");
	            } else {
	            	property.append("<property name=\"");
	            	property.append(variable.getQName().toString());
	            	property.append("\" fullname=\"");
	            	property.append(variable.getQName().toString());
	            	property.append("\" type=\"array\" children=\"true\" numchildren=\"");
	            	property.append(value.getItemCount());
	            	property.append("\">");
	                int idx = 0;
	                for (SequenceIterator si = value.iterate(); si.hasNext(); idx++) {
	                    Item item = si.nextItem();
	                    String strVal = getPropertyValue(item, serializer);
	                    String type = Type.subTypeOf(value.getItemType(), Type.NODE) ? "xs:string" :
	                        Type.getTypeName(value.getItemType());
	                    property.append("<property name=\"");
	                    property.append(idx);
	                    property.append("\" type=\"");
	                    property.append(type);
	                    property.append("\" size=\"");
	                    property.append(strVal.length());
	                    property.append("\" encoding=\"none\">");
	                    property.append(strVal);
	                    property.append("</property>");
	                }
	                property.append("</property>");
	            }
	            return property;
	        } catch (SAXException e) {
	            e.printStackTrace();
	        } catch (XPathException e) {
	            e.printStackTrace();
	        }
        } catch (EXistException e) {
			e.printStackTrace();
		}
        return null;
    }
	
	protected static String getTypeString(Variable variable) {
		if (!variable.isInitialized())
			return "uninitialized";
		
		return Type.getTypeName(variable.getType());
	}

    protected static String getPropertyValue(Item item, Serializer serializer) throws SAXException, XPathException {
        if (Type.subTypeOf(item.getType(), Type.NODE)) {
            return "<![CDATA[" + serializer.serialize((NodeValue) item) + "]]>";
        } else {
            return item.getStringValue();
        }
    }

	public byte[] commandBytes() {
		String command = "property_get" +
				" -i "+transactionID+
				" -n "+nameLong+
				" -d "+String.valueOf(stackDepth)+
				" -c "+String.valueOf(contextID);

		if (maxDataSize != null)
			command += " -m "+String.valueOf(maxDataSize);

		if (dataPage != null)
			command += " -p "+String.valueOf(dataPage);

		if (propertyKey != null)
			command += " -k "+propertyKey;

		if (propertyAddress != null)
			command += " -a "+propertyAddress;

		return command.getBytes();
	}
	
	public String toString() {
		
		StringBuilder response = new StringBuilder();
		response.append("property_get ");

		addAsString(response, "stackDepth", stackDepth);
		addAsString(response, "contextID", contextID);
		addAsString(response, "nameLong", nameLong);
		addAsString(response, "maxDataSize", maxDataSize);
		addAsString(response, "dataPage", dataPage);
		addAsString(response, "propertyKey", propertyKey);
		addAsString(response, "propertyAddress", propertyAddress);
		
		response.append("["+transactionID+"]");

		return response.toString();
	}
	
	private void addAsString(StringBuilder response, String name, Object value) {
		if (value != null) {
			response.append(name);
			response.append(" = '");
			response.append(value);
			response.append("' ");
		}
	}
}
