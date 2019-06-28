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
 */
package org.exist.xquery.modules.cache;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import static org.exist.xquery.FunctionDSL.functionDefs;

/**
 * XQuery Extension module for store data in global cache
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="mailto:gazdovsky@gmail.com">Evgeny Gazdovsky</a>
 * @author ljo
 *
 * @version 2.0
 */
public class CacheModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/cache";

    public final static String PREFIX = "cache";
    public final static String INCLUSION_DATE = "2009-03-04";
    public final static String RELEASED_IN_VERSION = "eXist-1.4";

    public static final FunctionDef[] functions = functionDefs(
            CacheFunctions.class,
            CacheFunctions.FS_CREATE_CACHE,
            CacheFunctions.FS_NAMES,
            CacheFunctions.FS_PUT,
            CacheFunctions.FS_LIST,
            CacheFunctions.FS_KEYS,
            CacheFunctions.FS_GET,
            CacheFunctions.FS_REMOVE,
            CacheFunctions.FS_CLEAR[0],
            CacheFunctions.FS_CLEAR[1],
            CacheFunctions.FS_CLEANUP,
            CacheFunctions.FS_DESTROY);


    static final Map<String, Cache> caches = new ConcurrentHashMap<>();

    public CacheModule(final Map<String, List<?>> parameters) {
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
        return "A module for accessing global caches for sharing data between concurrent sessions";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

    static FunctionSignature functionSignature(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType... paramTypes) {
        return FunctionDSL.functionSignature(new QName(name, NAMESPACE_URI), description, returnType, paramTypes);
    }

    static FunctionSignature[] functionSignatures(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType[][] variableParamTypes) {
        return FunctionDSL.functionSignatures(new QName(name, NAMESPACE_URI), description, returnType, variableParamTypes);
    }

    static class CacheModuleErrorCode extends ErrorCodes.ErrorCode {
        private CacheModuleErrorCode(final String code, final String description) {
            super(new QName(code, NAMESPACE_URI, PREFIX), description);
        }
    }

    static final ErrorCodes.ErrorCode INSUFFICIENT_PERMISSIONS = new CacheModuleErrorCode("insufficient-permissions", "The calling user does not have sufficient permissions to operate on the cache.");
    static final ErrorCodes.ErrorCode KEY_SERIALIZATION = new CacheModuleErrorCode("key-serialization", "Unable to serialize the provided key.");
}
