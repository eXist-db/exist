/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2017 The eXist Project
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
 */
package org.exist.security.realm.iprange;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.security.AbstractRealm;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.xquery.XQueryContext;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * IPRange authenticator servlet.
 *
 * @author <a href="mailto:wshager@gmail.com">Wouter Hager</a>
 */
public class IPRangeServlet extends HttpServlet {

    protected final static Logger LOG = LogManager.getLogger(IPRangeServlet.class);
    private static final long serialVersionUID = -568037449837549034L;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {


        // Get reverse proxy header when available, otherwise use regular IP address
        String ipAddress = request.getHeader("X-Forwarded-For");
        // there may be a comma-separated chain of proxies
        if(ipAddress != null && !ipAddress.isEmpty()) {
            ipAddress = ipAddress.replaceAll("\\s","");
            String[] xFFs = ipAddress.split(",");
            if(xFFs.length > 1) ipAddress = xFFs[xFFs.length - 1];
        } else {
            ipAddress = request.getRemoteAddr();
        }

        LOG.info("Detected IPaddress " + ipAddress);

        String jsonResponse = "{\"fail\":\"IP range not authenticated\"}";

        try {
            final SecurityManager securityManager = IPRangeRealm.getInstance().getSecurityManager();
            final Subject user = securityManager.authenticate(ipAddress, ipAddress);

            if (user != null) {
                LOG.info("IPRangeServlet user " + user.getUsername() + " found");

                // Security check
                if (user.hasDbaRole()) {
                    LOG.error("User " + user.getUsername() + " has DBA rights, will not be authorized");
                    return;
                }


                final HttpSession session = request.getSession();
                // store the user in the session
                if (session != null) {
                    jsonResponse = "{\"user\":\"" + user.getUsername() + "\",\"isAdmin\":\"" + user.hasDbaRole() + "\"}";
                    LOG.info("IPRangeServlet setting session attr " + XQueryContext.HTTP_SESSIONVAR_XMLDB_USER);
                    session.setAttribute(XQueryContext.HTTP_SESSIONVAR_XMLDB_USER, user);
                } else {
                    LOG.info("IPRangeServlet session is null");
                }

            } else {
                LOG.error("IPRangeServlet user not found");
            }

        } catch (final AuthenticationException e) {
            throw new IOException(e.getMessage());

        } finally {
            response.setContentType("application/json");
            final PrintWriter out = response.getWriter();
            out.print(jsonResponse);
            out.flush();
        }
    }

}
