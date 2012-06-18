/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2011 The eXist-db Project
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
package org.exist.xquery.modules.cssparser;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;


/**
 * Module function definition for CSSParser module.
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class CSSParserModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/cssparser";

    public final static String PREFIX = "cssparser";
    public final static String INCLUSION_DATE = "2011-10-20";
    public final static String RELEASED_IN_VERSION = "eXist-2.0";

    public final static FunctionDef[] functions = {
        new FunctionDef(CSSParserFunctions.signatures[0], CSSParserFunctions.class)
    };

   
    public CSSParserModule(Map<String, List<? extends Object>> parameters) throws XPathException {
        super(functions, parameters, true);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getDescription()
     */
    @Override
    public String getDescription() {
        return "A module for parsing CSS.";
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getNamespaceURI()
     */
    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getDefaultPrefix()
     */
    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}