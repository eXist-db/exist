/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
 *  
 *  $Id$
 */
package org.exist.xslt.functions;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XSLModule extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://www.w3.org/1999/XSL/Transform";
	public final static String PREFIX = "xsl";

    public final static String RELEASED_IN_VERSION = "eXist-1.5";

    private final static FunctionDef[] functions = {
		new FunctionDef(Current.signatures[0], Current.class),
		new FunctionDef(Document.signatures[0], Document.class),
		new FunctionDef(Document.signatures[1], Document.class),
		new FunctionDef(Format_date.signatures[0], Format_date.class),
		new FunctionDef(Format_date.signatures[1], Format_date.class),
		new FunctionDef(Format_dateTime.signatures[0], Format_dateTime.class),
		new FunctionDef(Format_dateTime.signatures[1], Format_dateTime.class),
		new FunctionDef(Format_number.signatures[0], Format_number.class),
		new FunctionDef(Format_number.signatures[1], Format_number.class),
		new FunctionDef(Format_time.signatures[0], Format_time.class),
		new FunctionDef(Format_time.signatures[1], Format_time.class),
		new FunctionDef(Generate_id.signatures[0], Generate_id.class),
		new FunctionDef(Key.signatures[0], Key.class),
		new FunctionDef(Key.signatures[1], Key.class),
		new FunctionDef(System_property.signatures[0], System_property.class),
		new FunctionDef(Unparsed_entity_public_id.signatures[0], Unparsed_entity_public_id.class),
		new FunctionDef(Unparsed_entity_uri.signatures[0], Unparsed_entity_uri.class),
		new FunctionDef(Unparsed_text_available.signatures[0], Unparsed_text_available.class),
		new FunctionDef(Unparsed_text_available.signatures[1], Unparsed_text_available.class),
		new FunctionDef(Unparsed_text.signatures[0], Unparsed_text.class),
		new FunctionDef(Unparsed_text.signatures[1], Unparsed_text.class),
		new FunctionDef(Generate_id.signatures[0], Generate_id.class),
		new FunctionDef(Generate_id.signatures[1], Generate_id.class),
		};

	/**
	 * @param parameters
	 */
	public XSLModule(Map<String, List<? extends Object>> parameters) {
		super(functions, parameters);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractInternalModule#getDefaultPrefix()
	 */
	@Override
	public String getDefaultPrefix() {
		return PREFIX;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractInternalModule#getNamespaceURI()
	 */
	@Override
	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() {
		return "XSLT Module";
	}

	@Override
	public String getReleaseVersion() {
		return RELEASED_IN_VERSION;
	}

}
