/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * $Id$
 */

package org.exist.http.urlrewrite;

import org.w3c.dom.Element;
import org.exist.xmldb.XmldbURI;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import java.io.IOException;

public class ControllerForward extends URLRewrite {
	
    /**
     * Adding server-name="www.example.com" to a root tag in the controller-config.xml file.<br/>
     * <br/>
     *  i.e.<br/> 
     *  <br/>
     *  &lt;root server-name="example1.com" pattern="/*" path="xmldb:exist:///db/org/example1/"/&gt;<br/>
     *  &lt;root server-name="example2.com" pattern="/*" path="xmldb:exist:///db/org/example2/"/&gt;<br/>
     *  <br/>
     *  Will redirect http://example1.com to /db/org/example1/<br/>
     *  and http://example2.com to /db/org/example2/<br/>
     *  <br/>
     *  If there is no server-name attribute on the root tag, then the server name is ignored while performing the URL rewriting.
     *  
     */
	private String serverName = null;

    public ControllerForward(Element config, String uri) {
        super(config, uri);
        this.target = config.getAttribute("path");
    }

    @Override
    public void doRewrite(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    }

    @Override
    public boolean isControllerForward() {
        return true;
    }

    @Override
    protected void updateRequest(XQueryURLRewrite.RequestWrapper request) {
        super.updateRequest(request);
        if (!(target.length() == 0 || "/".equals(target) ||target.startsWith(XmldbURI.XMLDB_URI_PREFIX))) {
            final String oldURI = request.getInContextPath();
            final String uri = target + oldURI;
            request.setInContextPath(uri);
        }
    }

    @Override
    protected void rewriteRequest(XQueryURLRewrite.RequestWrapper request) {
        if (target != null && target.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
            final XmldbURI dbURI = XmldbURI.create(target);
            this.uri = "/rest";
            String colPath = dbURI.getCollectionPath();
            final String contextPath = request.getInContextPath();
            if (contextPath.startsWith(colPath))
                {colPath = "";}
            request.setPaths("/rest" + colPath + contextPath, "/rest");
            request.setBasePath("/rest" + colPath);
            request.setAttribute(XQueryURLRewrite.RQ_ATTR, "true");
        }
    }

	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	public String getServerName() {
		return serverName;
	}
}
