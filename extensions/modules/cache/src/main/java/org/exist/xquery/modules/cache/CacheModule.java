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
package org.exist.xquery.modules.cache;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private static final Logger LOG = LogManager.getLogger(CacheModule.class);

    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/cache";

    public static final String PREFIX = "cache";
    public static final String INCLUSION_DATE = "2009-03-04";
    public static final String RELEASED_IN_VERSION = "eXist-1.4";

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

    private static final String PARAM_NAME_ENABLE_LAZY_CREATION = "enableLazyCreation";
    private static final String PARAM_NAME_LAZY_MAXIMUM_SIZE = "lazy.maximumSize";
    private static final String PARAM_NAME_LAZY_EXPIRE_AFTER_ACCESS = "lazy.expireAfterAccess";
    private static final String PARAM_NAME_LAZY_EXPIRE_AFTER_WRITE = "lazy.expireAfterWrite";
    private static final String PARAM_NAME_LAZY_PUT_GROUP = "lazy.putGroup";
    private static final String PARAM_NAME_LAZY_GET_GROUP = "lazy.getGroup";
    private static final String PARAM_NAME_LAZY_REMOVE_GROUP = "lazy.removeGroup";
    private static final String PARAM_NAME_LAZY_CLEAR_GROUP = "lazy.clearGroup";

    private static final long DEFAULT_LAZY_MAXIMUM_SIZE = 128;  // 128 items
    private static final long DEFAULT_LAZY_EXPIRE_AFTER_ACCESS = 1000 * 60 * 5;  // 5 minutes
    private static final long DEFAULT_LAZY_EXPIRE_AFTER_WRITE = 1000 * 60 * 5;  // 5 minutes

    static final Map<String, Cache> caches = new ConcurrentHashMap<>();

    private final Optional<CacheConfig> lazyCacheConfig;

    public CacheModule(final Map<String, List<?>> parameters) {
        super(functions, parameters);

        // read parameters
        this.lazyCacheConfig = parseParameters(parameters);
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
        return FunctionDSL.functionSignature(new QName(name, NAMESPACE_URI, PREFIX), description, returnType, paramTypes);
    }

    static FunctionSignature[] functionSignatures(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType[][] variableParamTypes) {
        return FunctionDSL.functionSignatures(new QName(name, NAMESPACE_URI, PREFIX), description, returnType, variableParamTypes);
    }

    static class CacheModuleErrorCode extends ErrorCodes.ErrorCode {
        private CacheModuleErrorCode(final String code, final String description) {
            super(new QName(code, NAMESPACE_URI, PREFIX), description);
        }
    }

    static final ErrorCodes.ErrorCode INSUFFICIENT_PERMISSIONS = new CacheModuleErrorCode("insufficient-permissions", "The calling user does not have sufficient permissions to operate on the cache.");
    static final ErrorCodes.ErrorCode KEY_SERIALIZATION = new CacheModuleErrorCode("key-serialization", "Unable to serialize the provided key.");
    static final ErrorCodes.ErrorCode LAZY_CREATION_DISABLED = new CacheModuleErrorCode("lazy-creation-disabled", "There is no such named cache, and lazy creation of the cache has been disabled.");

    private static Optional<CacheConfig> parseParameters(final Map<String, List<?>> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Optional.empty();
        }

        final boolean enableLazyCreation = getFirstString(parameters, PARAM_NAME_ENABLE_LAZY_CREATION)
                .map(Boolean::parseBoolean)
                .orElse(false);

        if (!enableLazyCreation) {
            return Optional.empty();
        }

        final Optional<String> putGroup = getFirstString(parameters, PARAM_NAME_LAZY_PUT_GROUP);
        final Optional<String> getGroup = getFirstString(parameters, PARAM_NAME_LAZY_GET_GROUP);
        final Optional<String> removeGroup = getFirstString(parameters, PARAM_NAME_LAZY_REMOVE_GROUP);
        final Optional<String> clearGroup = getFirstString(parameters, PARAM_NAME_LAZY_CLEAR_GROUP);
        final Optional<CacheConfig.Permissions> permissions = Optional.of(new CacheConfig.Permissions(putGroup, getGroup, removeGroup, clearGroup));

        final Optional<Long> maximumSize = getFirstString(parameters, PARAM_NAME_LAZY_MAXIMUM_SIZE)
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (final NumberFormatException e) {
                        LOG.warn("Unable to set {} to: {}. Using default: {}", PARAM_NAME_LAZY_MAXIMUM_SIZE, s, DEFAULT_LAZY_MAXIMUM_SIZE);
                        return DEFAULT_LAZY_MAXIMUM_SIZE;
                    }
                });

        final Optional<Long> expireAfterAccess = getFirstString(parameters, PARAM_NAME_LAZY_EXPIRE_AFTER_ACCESS)
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (final NumberFormatException e) {
                        LOG.warn("Unable to set {} to: {}. Using default: {}", PARAM_NAME_LAZY_EXPIRE_AFTER_ACCESS, s, DEFAULT_LAZY_EXPIRE_AFTER_ACCESS);
                        return DEFAULT_LAZY_EXPIRE_AFTER_ACCESS;
                    }
                });

        final Optional<Long> expireAfterWrite = getFirstString(parameters, PARAM_NAME_LAZY_EXPIRE_AFTER_WRITE)
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (final NumberFormatException e) {
                        LOG.warn("Unable to set {} to: {}. Using default: {}", PARAM_NAME_LAZY_EXPIRE_AFTER_WRITE, s, DEFAULT_LAZY_EXPIRE_AFTER_WRITE);
                        return DEFAULT_LAZY_EXPIRE_AFTER_ACCESS;
                    }
                });


        return Optional.of(new CacheConfig(permissions, maximumSize, expireAfterAccess, expireAfterWrite));
    }

    private static Optional<String> getFirstString(final Map<String, List<?>> parameters, final String paramName) {
        return Optional.ofNullable(parameters.get(paramName))
                .filter(l -> l.size() == 1)
                .map(l -> l.getFirst())
                .filter(o -> o instanceof String)
                .map(o -> (String)o);
    }

    Optional<CacheConfig> getLazyCacheConfig() {
        return lazyCacheConfig;
    }
}
