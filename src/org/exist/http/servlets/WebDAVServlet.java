/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.http.servlets;

import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exist.http.webdav.WebDAV;
import org.exist.storage.BrokerPool;

/**
 * Provides a WebDAV interface to the database. All WebDAV requests
 * are delegated to the {@link org.exist.http.webdav.WebDAV} class.
 *
 * @author wolf
 */
public class WebDAVServlet extends HttpServlet {
    
    private WebDAV webdav;
    /** id of the database registred against the BrokerPool */
    protected String databaseid = BrokerPool.DEFAULT_INSTANCE_NAME;
    
    
        /* (non-Javadoc)
         * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
         */
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        // <frederic.glorieux@ajlsm.com> to allow multi-instance webdav server,
        // use a databaseid everywhere
        String id = config.getInitParameter("database-id");
        if (id != null && !"".equals(id)) this.databaseid=id;
        
        int authMethod = WebDAV.DIGEST_AUTH;
        String param = config.getInitParameter("authentication");
        
        if(param != null && "basic".equalsIgnoreCase(param))
            authMethod = WebDAV.BASIC_AUTH;
        
        webdav = new WebDAV(authMethod, this.databaseid);
    }
    
        /* (non-Javadoc)
         * @see javax.servlet.http.HttpServlet#service(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
         */
    protected void service(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
//		dumpHeaders(request);
        webdav.process(request, response);
    }
    
    private void dumpHeaders(HttpServletRequest request) {
        System.out.println("-------------------------------------------------------");
        for(Enumeration e = request.getHeaderNames(); e.hasMoreElements(); ) {
            String header = (String)e.nextElement();
            System.out.println(header + " = " + request.getHeader(header));
        }
    }
}
