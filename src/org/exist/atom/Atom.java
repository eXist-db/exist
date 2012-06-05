/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2006-2012 The eXist Project
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
package org.exist.atom;

import java.net.URI;

import org.exist.dom.QName;

/**
 *
 * @author R. Alexander Milowski
 */
public interface Atom {
   
	String MIME_TYPE = "application/atom+xml";
	
	URI NAMESPACE = URI.create("http://www.w3.org/2005/Atom");

	String NAMESPACE_STRING = NAMESPACE.toString();

	QName FEED = new QName("feed",NAMESPACE_STRING,"atom");
	QName ENTRY = new QName("entry",NAMESPACE_STRING,"atom");
	QName TITLE = new QName("title",NAMESPACE_STRING,"atom");
	QName UPDATED = new QName("updated",NAMESPACE_STRING,"atom");
	QName PUBLISHED = new QName("published",NAMESPACE_STRING,"atom");
	QName SUMMARY = new QName("summary",NAMESPACE_STRING,"atom");
}