/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist team
 * http://exist-db.org
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
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xmlrpc;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.xmlrpc.Base64;
import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcServer;
import org.exist.EXistException;
import org.exist.http.Descriptor;
import org.exist.http.servlets.HttpServletRequestWrapper;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.MimeType;

public class RpcServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	protected XmlRpcServer xmlrpc;
    /** id of the database registred against the BrokerPool */
    protected String databaseid = BrokerPool.DEFAULT_INSTANCE_NAME;


    /**
     *  Handle XML-RPC requests
     *
     *@param  request               Description of the Parameter
     *@param  response              Description of the Parameter
     *@exception  ServletException  Description of the Exception
     *@exception  IOException       Description of the Exception
     */
    public void doPost( HttpServletRequest request,
                        HttpServletResponse response )
         throws ServletException, IOException {
    	
    	String user = "admin";
        String password = null;
        String auth = request.getHeader( "Authorization" );
        
        
        if ( auth != null ) {
            byte[] c = Base64.decode( auth.substring( 6 ).getBytes() );
            String s = new String( c );
            int p = s.indexOf( ':' );
            user = s.substring( 0, p );
            password = s.substring( p + 1 );
        }
    	
    	// Request logger

        InputStream inputStream;		
		Descriptor descriptor = Descriptor.getDescriptorSingleton();
    	if( descriptor.allowRequestLogging() ) {
        	// Wrap HttpServletRequest, because both request Logger and xmlrpc 
    		// need the request InputStream, which is consumed when read.
    		HttpServletRequestWrapper requestWrapper = 
    			new HttpServletRequestWrapper(request, /*formEncoding*/ "utf-8" );
    		descriptor.doLogRequestInReplayLog(requestWrapper);
    		inputStream = requestWrapper.getContentBodyInputStream();
    	} else {
            //- Caution : this must be called AFTER HttpServletRequestWrapper, 
    		// otherwise Web server throws IllegalStateException
    		inputStream = request.getInputStream();
    	}
    	
		byte[] result =
            xmlrpc.execute( inputStream, user, password );

		response.setContentType( "text/xml" );
        response.setContentLength( result.length );
        OutputStream output = response.getOutputStream();
        output.write( result );
        output.flush();
    }


    /**
     *  Create XML-RPC handler
     *
     *@param  config                Description of the Parameter
     *@exception  ServletException  Description of the Exception
     */
    public void init( ServletConfig config ) throws ServletException {
        super.init( config );
        // <frederic.glorieux@ajlsm.com> to allow multi-instance xmlrpc server, 
        // use a databaseid everywhere
        String id = config.getInitParameter("database-id");
        if (id != null && !"".equals(id))
        	this.databaseid=id;
        if ( !BrokerPool.isConfigured(databaseid) )
            throw new ServletException( "database is not running" );
        boolean enableDebug = false;
        String param = config.getInitParameter("debug");
        if(param != null)
        	enableDebug = param.equalsIgnoreCase("true");
        try {
        	BrokerPool pool = BrokerPool.getInstance(databaseid);
            Configuration conf = pool.getConfiguration();
            xmlrpc = new XmlRpcServer();
            AuthenticatedHandler rpcserv = new AuthenticatedHandler( conf, databaseid );
            //RpcServer rpcserv = new RpcServer( conf );
            xmlrpc.addHandler( "$default", rpcserv );
            XmlRpc.setDebug( enableDebug );
            XmlRpc.setEncoding( "UTF-8" );
        } catch (EXistException e) {
        	throw new ServletException( e );
        } catch ( XmlRpcException e ) {
            throw new ServletException( e.getMessage() );
        }
    }
}


