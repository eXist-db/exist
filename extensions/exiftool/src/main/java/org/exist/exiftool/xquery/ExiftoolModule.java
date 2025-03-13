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
package org.exist.exiftool.xquery;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;

/**
 * @author <a href="mailto:dulip.withanage@gmail.com">Dulip Withanage</a>
 * @version 1.0
 */
public class ExiftoolModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/exiftool";
    public final static String PREFIX = "exiftool";
    public final static String INCLUSION_DATE = "2011-04-11";
    public final static String RELEASED_IN_VERSION = "eXist-1.5";

    public final static FunctionDef[] functions = {
        new FunctionDef(MetadataFunctions.getMetadata, MetadataFunctions.class)
    };

    public ExiftoolModule(Map<String, List<? extends Object>> parameters) throws XPathException {
        super(functions, parameters, true);
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
        return ("Module for reading and writing embedded metadata for binary files");
    }

    @Override
    public String getReleaseVersion() {
        return (RELEASED_IN_VERSION);
    }

    protected String getExiftoolPath() {
        return (String)getParameter("exiftool-path").getFirst();
    }

    protected String getPerlPath() {
        return (String)getParameter("perl-path").getFirst();
    }

  
}