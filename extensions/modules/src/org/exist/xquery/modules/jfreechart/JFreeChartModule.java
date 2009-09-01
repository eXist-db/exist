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
package org.exist.xquery.modules.jfreechart;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;

/**
 * JFreeChart module for eXist.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 * @author ljo
 */
public class JFreeChartModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/jfreechart";
    public final static String PREFIX = "jfreechart";
    public final static String INCLUSION_DATE = "2009-04-13";
    public final static String RELEASED_IN_VERSION = "eXist-1.4";

    public final static FunctionDef[] functions = {
        new FunctionDef(JFreeCharting.signatures[0], JFreeCharting.class),
        new FunctionDef(JFreeCharting.signatures[1], JFreeCharting.class),
    };

    public final static QName EXCEPTION_QNAME =
            new QName("exception", JFreeChartModule.NAMESPACE_URI, JFreeChartModule.PREFIX);
    
    public final static QName EXCEPTION_MESSAGE_QNAME =
            new QName("exception-message", JFreeChartModule.NAMESPACE_URI, JFreeChartModule.PREFIX);

    public JFreeChartModule() throws XPathException {
        super(functions);
        declareVariable(EXCEPTION_QNAME, null);
        declareVariable(EXCEPTION_MESSAGE_QNAME, null);
    }

    public String getDescription() {
        return "A module for generating charts using the JFreeChart library.";
    }

    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    public String getDefaultPrefix() {
        return PREFIX;
    }

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

}
