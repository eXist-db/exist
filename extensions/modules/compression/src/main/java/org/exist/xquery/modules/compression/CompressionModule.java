/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2007-2018 The eXist Project
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
package org.exist.xquery.modules.compression;

import java.util.List;
import java.util.Map;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;

import static org.exist.xquery.FunctionDSL.functionDefs;

/**
 * XQuery Extension module for compression and de-compression functions
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author ljo
 */
public class CompressionModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/compression";

    public final static String PREFIX = "compression";
    public final static String INCLUSION_DATE = "2007-07-10";
    public final static String RELEASED_IN_VERSION = "eXist-1.2";

    private final static FunctionDef[] functions = functionDefs(
            functionDefs(ZipFunction.class,
                    ZipFunction.signatures[0],
                    ZipFunction.signatures[1],
                    ZipFunction.signatures[2]
            ),
            functionDefs(UnZipFunction.class,
                    UnZipFunction.FS_UNZIP[0],
                    UnZipFunction.FS_UNZIP[1],
                    UnZipFunction.FS_UNZIP[2]
            ),
            functionDefs(GZipFunction.class,
                    GZipFunction.signatures[0]
            ),
            functionDefs(UnGZipFunction.class,
                    UnGZipFunction.signatures[0]
            ),
            functionDefs(DeflateFunction.class,
                    DeflateFunction.signatures[0],
                    DeflateFunction.signatures[1]
            ),
            functionDefs(InflateFunction.class,
                    InflateFunction.signatures[0],
                    InflateFunction.signatures[1]
            ),
            functionDefs(TarFunction.class,
                    TarFunction.signatures[0],
                    TarFunction.signatures[1],
                    TarFunction.signatures[2]
            ),
            functionDefs(UnTarFunction.class,
                    UnTarFunction.FS_UNTAR[0],
                    UnTarFunction.FS_UNTAR[1],
                    UnTarFunction.FS_UNTAR[2]
            ),
            functionDefs(EntryFunctions.class,
                    EntryFunctions.FS_NO_FILTER[0],
                    EntryFunctions.FS_NO_FILTER[1],
                    EntryFunctions.FS_FS_STORE_ENTRY3,
                    EntryFunctions.FS_FS_STORE_ENTRY4,
                    EntryFunctions.FS_DB_STORE_ENTRY3,
                    EntryFunctions.FS_DB_STORE_ENTRY4
            )
    );

    public CompressionModule(final Map<String, List<?>> parameters) {
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

    static FunctionSignature functionSignature(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType... paramTypes) {
        return FunctionDSL.functionSignature(new QName(name, NAMESPACE_URI), description, returnType, paramTypes);
    }

    static FunctionSignature[] functionSignatures(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType[][] variableParamTypes) {
        return FunctionDSL.functionSignatures(new QName(name, NAMESPACE_URI), description, returnType, variableParamTypes);
    }

    static class CompressionModuleErrorCode extends ErrorCodes.ErrorCode {
        private CompressionModuleErrorCode(final String code, final String description) {
            super(new QName(code, NAMESPACE_URI, PREFIX), description);
        }
    }

    static final ErrorCodes.ErrorCode ARCHIVE_EXIT_ATTACK = new CompressionModuleErrorCode("archive-exit-attack", "The archive likely contains an exit attack, whereby a file extraction tries to escape the destination path.");
}
