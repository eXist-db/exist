/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.debuggee;

import org.exist.dom.QName;
import org.exist.xquery.CompiledXQuery;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface Debuggee {

	public static final String NAMESPACE_URI = "http://www.xdebug.org/";
	public static final String PREFIX = "DBGp";

	public static final QName SESSION = new QName("session", NAMESPACE_URI, PREFIX);
	public static final QName IDEKEY = new QName("idekey", NAMESPACE_URI, PREFIX);
	
	public boolean joint(CompiledXQuery compiledXQuery);

	public String start(String uri) throws Exception;

	public Session getSession(String id);
}
