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
package org.exist.xquery.modules.binary;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

import static org.exist.xquery.FunctionDSL.functionDefs;

/**
 * EXPath Binary Module 4.0.
 *
 * @see <a href="https://qt4cg.org/specifications/expath-binary-40/Overview.html">EXPath Binary Module 4.0</a>
 */
public class BinaryModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://expath.org/ns/binary";
    public static final String PREFIX = "bin";
    public static final String INCLUSION_DATE = "2026-03-04";
    public static final String RELEASED_IN_VERSION = "eXist-7.0.0";

    private static final FunctionDef[] functions = functionDefs(
            functionDefs(BinaryConversionFunctions.class,
                    BinaryConversionFunctions.FS_HEX,
                    BinaryConversionFunctions.FS_BIN,
                    BinaryConversionFunctions.FS_OCTAL,
                    BinaryConversionFunctions.FS_TO_OCTETS,
                    BinaryConversionFunctions.FS_FROM_OCTETS),

            functionDefs(BinaryBasicFunctions.class,
                    BinaryBasicFunctions.FS_LENGTH,
                    BinaryBasicFunctions.FS_PART[0],
                    BinaryBasicFunctions.FS_PART[1],
                    BinaryBasicFunctions.FS_JOIN,
                    BinaryBasicFunctions.FS_INSERT_BEFORE,
                    BinaryBasicFunctions.FS_PAD_LEFT[0],
                    BinaryBasicFunctions.FS_PAD_LEFT[1],
                    BinaryBasicFunctions.FS_PAD_RIGHT[0],
                    BinaryBasicFunctions.FS_PAD_RIGHT[1],
                    BinaryBasicFunctions.FS_FIND),

            functionDefs(BinaryTextFunctions.class,
                    BinaryTextFunctions.FS_DECODE_STRING[0],
                    BinaryTextFunctions.FS_DECODE_STRING[1],
                    BinaryTextFunctions.FS_DECODE_STRING[2],
                    BinaryTextFunctions.FS_DECODE_STRING[3],
                    BinaryTextFunctions.FS_ENCODE_STRING[0],
                    BinaryTextFunctions.FS_ENCODE_STRING[1]),

            functionDefs(BinaryPackingFunctions.class,
                    BinaryPackingFunctions.FS_PACK_DOUBLE[0],
                    BinaryPackingFunctions.FS_PACK_DOUBLE[1],
                    BinaryPackingFunctions.FS_PACK_FLOAT[0],
                    BinaryPackingFunctions.FS_PACK_FLOAT[1],
                    BinaryPackingFunctions.FS_PACK_INTEGER[0],
                    BinaryPackingFunctions.FS_PACK_INTEGER[1],
                    BinaryPackingFunctions.FS_UNPACK_DOUBLE[0],
                    BinaryPackingFunctions.FS_UNPACK_DOUBLE[1],
                    BinaryPackingFunctions.FS_UNPACK_FLOAT[0],
                    BinaryPackingFunctions.FS_UNPACK_FLOAT[1],
                    BinaryPackingFunctions.FS_UNPACK_INTEGER[0],
                    BinaryPackingFunctions.FS_UNPACK_INTEGER[1],
                    BinaryPackingFunctions.FS_UNPACK_UNSIGNED_INTEGER[0],
                    BinaryPackingFunctions.FS_UNPACK_UNSIGNED_INTEGER[1]),

            functionDefs(BinaryBitwiseFunctions.class,
                    BinaryBitwiseFunctions.FS_OR,
                    BinaryBitwiseFunctions.FS_XOR,
                    BinaryBitwiseFunctions.FS_AND,
                    BinaryBitwiseFunctions.FS_NOT,
                    BinaryBitwiseFunctions.FS_SHIFT),

            functionDefs(BinaryInferEncodingFunction.class,
                    BinaryInferEncodingFunction.FS_INFER_ENCODING[0],
                    BinaryInferEncodingFunction.FS_INFER_ENCODING[1])
    );

    public BinaryModule(final Map<String, List<? extends Object>> parameters) {
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
        return "EXPath Binary Module 4.0 https://qt4cg.org/specifications/expath-binary-40/Overview.html";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
