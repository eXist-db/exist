/*
 *  eXist EXPath Zip Client Module Extension
 *  Copyright (C) 2011 Adam Retter <adam@existsolutions.com>
 *  www.existsolutions.com
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
package org.expath.exist;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 * @version EXPath Zip Client Module Candidate 12 October 2010 http://expath.org/spec/zip/20101012
 */
public class ZipModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://expath.org/ns/zip";

    public final static String PREFIX = "zip";
    public final static String INCLUSION_DATE = "2011-03-26";
    public final static String RELEASED_IN_VERSION = "1.5";

    private final static FunctionDef[] functions = {
        new FunctionDef(ZipEntryFunctions.signatures[0], ZipEntryFunctions.class),
        new FunctionDef(ZipEntryFunctions.signatures[1], ZipEntryFunctions.class),
        new FunctionDef(ZipEntryFunctions.signatures[2], ZipEntryFunctions.class),
        new FunctionDef(ZipEntryFunctions.signatures[3], ZipEntryFunctions.class),
        new FunctionDef(ZipFileFunctions.signatures[0], ZipFileFunctions.class),
        new FunctionDef(ZipFileFunctions.signatures[1], ZipFileFunctions.class)


    };

    public ZipModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "EXPath ZIP Module http://expath.org/spec/zip";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
