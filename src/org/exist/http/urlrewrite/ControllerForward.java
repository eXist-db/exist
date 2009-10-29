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

    public ControllerForward(Element config, String uri) {
        super(config, uri);
        this.target = config.getAttribute("path");
    }

    public void doRewrite(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    }

    public boolean isControllerForward() {
        return true;
    }

    protected void updateRequest(XQueryURLRewrite.RequestWrapper request) {
        super.updateRequest(request);
        if (!(target.length() == 0 || target.equals("/") ||target.startsWith(XmldbURI.XMLDB_URI_PREFIX))) {
            String oldURI = request.getInContextPath();
            String uri = target + oldURI;
            request.setInContextPath(uri);
        }
    }

    protected void rewriteRequest(XQueryURLRewrite.RequestWrapper request) {
        if (target != null && target.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
            XmldbURI dbURI = XmldbURI.create(target);
            this.uri = "/rest";
            request.setPaths("/rest" + dbURI.getCollectionPath() + request.getInContextPath(), "/rest");
            request.setBasePath("/rest" + dbURI.getCollectionPath());
        }
    }
}
