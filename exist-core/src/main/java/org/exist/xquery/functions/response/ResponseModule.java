/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2018 The eXist Project
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
 */
package org.exist.xquery.functions.response;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.*;

/**
 * Module function definitions for xmldb module.
 *
 * @author Adam Retter (adam.retter@devon.gov.uk)
 * @author ljo
 * @author José María Fernández (jmfg@users.sourceforge.net)
 */
public class ResponseModule extends AbstractInternalModule {

    private final static Logger LOG = LogManager.getLogger(ResponseModule.class);

    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/response";
    public static final String PREFIX = "response";
    public final static String INCLUSION_DATE = "2006-04-09";
    public final static String RELEASED_IN_VERSION = "eXist-1.0";

    public static final FunctionDef[] functions = {
            new FunctionDef(RedirectTo.signature, RedirectTo.class),
            new FunctionDef(SetCookie.signatures[0], SetCookie.class),
            new FunctionDef(SetCookie.signatures[1], SetCookie.class),
            new FunctionDef(SetCookie.signatures[2], SetCookie.class),
            new FunctionDef(SetDateHeader.signature, SetDateHeader.class),
            new FunctionDef(SetHeader.signature, SetHeader.class),
            new FunctionDef(SetStatusCode.signature, SetStatusCode.class),
            new FunctionDef(StreamBinary.signature, StreamBinary.class),
            new FunctionDef(Stream.signature, Stream.class),
            new FunctionDef(GetExists.signature, GetExists.class)
    };

    public ResponseModule(final Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }

    @Override
    public String getDescription() {
        return ("A module for dealing with HTTP responses.");
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
    public String getReleaseVersion() {
        return (RELEASED_IN_VERSION);
    }
}
