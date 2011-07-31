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
 * $Id: PassThrough.java 12936 2010-10-14 14:05:15Z gev $
 */
package org.exist.http.urlrewrite;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exist.http.servlets.HttpResponseWrapper;
import org.w3c.dom.Element;

public class PassThrough extends URLRewrite {

    public PassThrough(HttpServletRequest request) {
        super(null, request.getRequestURI());
        this.target = request.getRequestURI().substring(request.getContextPath().length());
    }
    
    public PassThrough(Element config, HttpServletRequest request) {
        super(config, request.getRequestURI());
        this.target = request.getRequestURI().substring(request.getContextPath().length());
    }

    @Override
    public void doRewrite(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        setHeaders(new HttpResponseWrapper(response));
        chain.doFilter(request, response);
    }
}
