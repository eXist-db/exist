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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Base class for all rewritten URLs.
 */
public abstract class URLRewrite {

    protected String uri;
    protected String target;

    protected URLRewrite(Element config, String uri) {
        this.uri = uri;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public abstract void doRewrite(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException;

    protected static String normalizePath(String path) {
        StringBuffer sb = new StringBuffer(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '/') {
                if (i == 0 || path.charAt(i - 1) != '/')
                    sb.append(c);
            } else
                sb.append(c);
        }
        return sb.toString();
    }
}
