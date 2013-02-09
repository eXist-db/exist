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

import org.exist.http.urlrewrite.XQueryURLRewrite.RequestWrapper;
import org.w3c.dom.Element;

import javax.servlet.RequestDispatcher;
import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

public class PathForward extends Forward {

    private ServletConfig filterConfig;
    private String servletName = null;

    public PathForward(ServletConfig filterConfig, Element config, String uri) throws ServletException {
        super(config, uri);
        this.filterConfig = filterConfig;
        servletName = config.getAttribute("servlet");
        final String url = config.getAttribute("url");
        if (servletName != null && servletName.length() == 0)
            {servletName = null;}
        if (servletName == null) {
            if (url == null || url.length() == 0)
                {throw new ServletException("<exist:forward> needs either an attribute 'url' or 'servlet'.");}
            setTarget(URLRewrite.normalizePath(url));
        }
    }

    
    @Override
	protected void setAbsolutePath(RequestWrapper request) {
		request.setPaths(target, servletName);
	}


	@Override
    protected RequestDispatcher getRequestDispatcher(HttpServletRequest request) {
        if (servletName != null)
            {return filterConfig.getServletContext().getNamedDispatcher(servletName);}
        else if (request != null)
            {return request.getRequestDispatcher(target);}
        else
            {return filterConfig.getServletContext().getRequestDispatcher(target);}
    }
}
