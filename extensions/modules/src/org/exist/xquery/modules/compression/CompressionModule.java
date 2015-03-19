/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2007-2009 The eXist Project
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
 * $Id$
 */
package org.exist.xquery.modules.compression;

import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * XQuery Extension module for compression and de-compression functions
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @author ljo
 * @version 1.0
 */
public class CompressionModule extends AbstractInternalModule {

    private final static Logger logger = LogManager.getLogger(CompressionModule.class);

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/compression";

    public final static String PREFIX = "compression";
    public final static String INCLUSION_DATE = "2007-07-10";
    public final static String RELEASED_IN_VERSION = "eXist-1.2";

    private final static FunctionDef[] functions = {
        new FunctionDef(ZipFunction.signatures[0], ZipFunction.class),
        new FunctionDef(ZipFunction.signatures[1], ZipFunction.class),
        new FunctionDef(UnZipFunction.signatures[0], UnZipFunction.class),

        new FunctionDef(GZipFunction.signatures[0], GZipFunction.class),
        new FunctionDef(UnGZipFunction.signatures[0], UnGZipFunction.class),

        new FunctionDef(TarFunction.signatures[0], TarFunction.class),
        new FunctionDef(TarFunction.signatures[1], TarFunction.class),
        new FunctionDef(UnTarFunction.signatures[0], UnTarFunction.class)
    };

    public CompressionModule(Map<String, List<? extends Object>> parameters) {
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

    public String getDescription() {
        return "A module for compression and decompression functions";
    }

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

}
