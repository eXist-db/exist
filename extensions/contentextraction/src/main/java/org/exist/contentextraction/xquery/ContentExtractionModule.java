/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
package org.exist.contentextraction.xquery;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;

/**
 * @author <a href="mailto:dulip.withanage@gmail.com">Dulip Withanage</a>
 * @version 1.0
 */
public class ContentExtractionModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/contentextraction";
    public final static String PREFIX = "contentextraction";
    public final static String INCLUSION_DATE = "2011-01-20";
    public final static String RELEASED_IN_VERSION = "eXist-1.5";

    public final static FunctionDef[] functions = {
        new FunctionDef(ContentFunctions.getMeatadata, ContentFunctions.class),
        new FunctionDef(ContentFunctions.getMetadataAndContent, ContentFunctions.class),
        new FunctionDef(ContentFunctions.streamContent, ContentFunctions.class)
    };

//    public final static QName EXCEPTION_QNAME =
//            new QName("exception", ContentExtractionModule.NAMESPACE_URI, ContentExtractionModule.PREFIX);
//    public final static QName EXCEPTION_MESSAGE_QNAME =
//            new QName("exception-message", ContentExtractionModule.NAMESPACE_URI, ContentExtractionModule.PREFIX);

    public ContentExtractionModule(Map<String, List<? extends Object>> parameters) throws XPathException {
        super(functions, parameters, true);
//        declareVariable(EXCEPTION_QNAME, null);
//        declareVariable(EXCEPTION_MESSAGE_QNAME, null);
    }

    @Override
    public String getNamespaceURI() {
        return (NAMESPACE_URI);
    }

    @Override
    public String getDefaultPrefix() {
        return (PREFIX);
    }

    @Override
    public String getDescription() {
        return ("Module for processing content and returning metadata and content");
    }

    @Override
    public String getReleaseVersion() {
        return (RELEASED_IN_VERSION);
    }
}
