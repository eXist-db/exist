/*
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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package org.exist.xquery.modules.xmpp;


import java.util.Properties;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.proxy.ProxyInfo;
import org.jivesoftware.smack.proxy.ProxyInfo.ProxyType;
import org.jivesoftware.smack.ConnectionConfiguration;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.dom.QName;
import org.exist.util.ParametersExtractor;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class XMPPConnectionFunction extends BasicFunction
{
	protected static final Logger logger = LogManager.getLogger(XMPPConnectionFunction.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "get-xmpp-connection", XMPPModule.NAMESPACE_URI, XMPPModule.PREFIX ),
			"Create a XMPP connection.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "properties", Type.ELEMENT, Cardinality.EXACTLY_ONE, 
						"An optional connection properties in the form <properties><property name=\"\" value=\"\"/></properties>.")
			},
			new FunctionReturnSequenceType( Type.LONG, Cardinality.EXACTLY_ONE, "an xs:long representing the connection handle." )
			)
		};

	public XMPPConnectionFunction( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
    }

	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		Properties props = ParametersExtractor.parseProperties( ((NodeValue) args[0].itemAt(0)).getNode() );
		
		ProxyInfo proxy; 
		ConnectionConfiguration config;
		
		if (props.containsKey("proxy.type")){
			ProxyType type   = ProxyType.valueOf(props.getProperty("proxy.type"));
			String    host   = props.getProperty("proxy.host");
			int       port   = new Integer(props.getProperty("proxy.port")).intValue();
			String    user   = props.getProperty("proxy.user");
			String    passwd = props.getProperty("proxy.password");
			proxy  =  new ProxyInfo(type, host, port, user, passwd);
		} else proxy = null;
		
		String service  = props.getProperty("xmpp.service");
		String host     = props.getProperty("xmpp.host");
		String tmp 		= props.getProperty("xmpp.port");
		int    port		= tmp == null ? 5222 : new Integer(tmp).intValue();   
		
		if (proxy == null){
			if (host == null)
				 config = new ConnectionConfiguration(service);
			else if (service == null)
				 config = new ConnectionConfiguration(host, port);
			else config = new ConnectionConfiguration(host, port, service);
		} else {
			if (host == null)
				 config = new ConnectionConfiguration(service, proxy);
			else if (service == null)
				 config = new ConnectionConfiguration(host, port, proxy);
			else config = new ConnectionConfiguration(host, port, service, proxy);
		}
		
		XMPPConnection connection = new XMPPConnection(config);
		
		// store the connection and return the handle of the connection
			
		IntegerValue integerValue = new IntegerValue( XMPPModule.storeConnection( connection ) );
		return integerValue;
	}
}
