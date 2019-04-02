/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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

package org.exist.xquery.functions.validation;

import java.util.List;
import java.util.Map;
import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

/**
 * Module function definitions for validation module.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 * @author ljo
 */
public class ValidationModule extends AbstractInternalModule {
    
    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/validation";
    
    public final static String PREFIX = "validation";
    public final static String INCLUSION_DATE = "2005-11-17";
    public final static String RELEASED_IN_VERSION = "eXist-1.0";
    
    public final static FunctionDef[] functions = {     
       new FunctionDef(GrammarTooling.signatures[0], GrammarTooling.class),
       new FunctionDef(GrammarTooling.signatures[1], GrammarTooling.class),
       new FunctionDef(GrammarTooling.signatures[2], GrammarTooling.class),

       new FunctionDef(Jaxv.signatures[0], Jaxv.class),
       new FunctionDef(Jaxv.signatures[1], Jaxv.class),
       new FunctionDef(Jaxv.signatures[2], Jaxv.class),
       new FunctionDef(Jaxv.signatures[3], Jaxv.class),

       new FunctionDef(Jing.signatures[0], Jing.class),
       new FunctionDef(Jing.signatures[1], Jing.class),
       
       new FunctionDef(Jaxp.signatures[0], Jaxp.class),
       new FunctionDef(Jaxp.signatures[1], Jaxp.class),
       new FunctionDef(Jaxp.signatures[2], Jaxp.class),
       new FunctionDef(Jaxp.signatures[3], Jaxp.class),

       new FunctionDef(Jaxp.signatures[4], Jaxp.class)
    };

//    static {
//        Arrays.sort(functions, new FunctionComparator());
//    }
    
    public final static QName EXCEPTION_QNAME =
            new QName("exception", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX);
    
    public final static QName EXCEPTION_MESSAGE_QNAME =
            new QName("exception-message", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX);
    
    public ValidationModule(final Map<String, List<?>> parameters) throws XPathException {
        super(functions, parameters);
    }

    @Override
    public void prepare(final XQueryContext context) throws XPathException {
        declareVariable(EXCEPTION_QNAME, null);
        declareVariable(EXCEPTION_MESSAGE_QNAME, null);
    }

    /* (non-Javadoc)
         * @see org.exist.xquery.ValidationModule#getDescription()
         */
    public String getDescription() {
        return "A module for XML validation and grammars functions.";
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
