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
package org.exist.xquery.modules.xmldiff;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;

/**
 * @author dizzzz
 * @author ljo
 */
public class XmlDiffModule extends AbstractInternalModule {
    
    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/xmldiff";
    
    public final static String PREFIX = "xmldiff";
    public final static String INCLUSION_DATE = "2006-02-19";
    public final static String RELEASED_IN_VERSION = "eXist-1.2";

    public final static FunctionDef[] functions = {
       new FunctionDef(Compare.signature, Compare.class),       
    };

    public XmlDiffModule(Map<String, List<?>> parameters) throws XPathException {
        super(functions, parameters);
    }
    
        /* (non-Javadoc)
         * @see org.exist.xquery.ValidationModule#getDescription()
         */
    public String getDescription() {
        return "A module for determining differences in XML documents.";
    }
    
        /* (non-Javadoc)
         * @see org.exist.xquery.ValidationModule#getNamespaceURI()
         */
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }
    
        /* (non-Javadoc)
         * @see org.exist.xquery.ValidationModule#getDefaultPrefix()
         */
    public String getDefaultPrefix() {
        return PREFIX;
    }

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

}
