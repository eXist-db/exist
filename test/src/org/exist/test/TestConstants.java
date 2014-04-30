/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2006-2009 The eXist Project
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
package org.exist.test;

import org.exist.xmldb.XmldbURI;
import org.exist.xquery.util.URIUtils;

public class TestConstants {

	/**
	 * String representing the decoded path: t[e s]tá열
	 */
	public static final String DECODED_SPECIAL_NAME = "t[e s]t\u00E0\uC5F4";
	
	/**
	 * String representing the encoded path: t%5Be%20s%5Dt%C3%A0%EC%97%B4
	 */
	public static final String SPECIAL_NAME = URIUtils.urlEncodeUtf8(DECODED_SPECIAL_NAME);

	/**
	 * XmldbURI representing the decoded path: t[e s]tá열
	 */
	public static final XmldbURI SPECIAL_URI = XmldbURI.create(SPECIAL_NAME);

	/**
	 * XmldbURI representing the decoded path: /db/test
	 */
	public static final XmldbURI TEST_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("test");
	/**
	 * XmldbURI representing the decoded path: /db/test/test2
	 */
	public static final XmldbURI TEST_COLLECTION_URI2 = TEST_COLLECTION_URI.append("test2");
	/**
	 * XmldbURI representing the decoded path: /db/test/test2/test3
	 */
	public static final XmldbURI TEST_COLLECTION_URI3 = TEST_COLLECTION_URI2.append("test3");
	
	/**
	 * XmldbURI representing the decoded path: /db/t[e s]tá열
	 */
	public static final XmldbURI SPECIAL_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append(SPECIAL_NAME);

	/**
	 * XmldbURI representing the decoded path: /db/destination
	 */
	public static final XmldbURI DESTINATION_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("destination");
	/**
	 * XmldbURI representing the decoded path: /db/destination2
	 */
	public static final XmldbURI DESTINATION_COLLECTION_URI2 = XmldbURI.ROOT_COLLECTION_URI.append("destination2");
	/**
	 * XmldbURI representing the decoded path: /db/destination3
	 */
	public static final XmldbURI DESTINATION_COLLECTION_URI3 = XmldbURI.ROOT_COLLECTION_URI.append("destination3");
	
	/**
	 * XmldbURI representing the decoded path: test.xml
	 */
	public static final XmldbURI TEST_XML_URI = XmldbURI.create("test.xml");
	/**
	 * XmldbURI representing the decoded path: test2.xml
	 */
	public static final XmldbURI TEST_XML_URI2 = XmldbURI.create("test2.xml");
	/**
	 * XmldbURI representing the decoded path: test3.xml
	 */
	public static final XmldbURI TEST_XML_URI3 = XmldbURI.create("test3.xml");
	
	/**
	 * XmldbURI representing the decoded path: t[e s]tá열.xml
	 */
	public static final XmldbURI SPECIAL_XML_URI = XmldbURI.create(URIUtils.urlEncodeUtf8("t[es]t\u00E0\uC5F4.xml"));

	/**
	 * XmldbURI representing the decoded path: binary.txt
	 */
	public static final XmldbURI TEST_BINARY_URI = XmldbURI.create("binary.txt");
	
	/**
	 * XmldbURI representing the decoded path: testmodule.xqm
	 */
	public static final XmldbURI TEST_MODULE_URI = XmldbURI.create("testmodule.xqm");
	
}
