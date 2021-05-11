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

import org.exist.storage.serializers.Serializer;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;

import java.util.Collection;
import java.util.Optional;
import java.util.Properties;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.modules.cache.CacheModule.*;

/**
 * Function implementations for the Cache Module
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class CacheFunctions extends BasicFunction {

    private final static Properties OUTPUT_PROPERTIES = new Properties();
    static {
        OUTPUT_PROPERTIES.setProperty(OutputKeys.INDENT, "no");
        OUTPUT_PROPERTIES.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    }

    private static final FunctionParameterSequenceType FS_PARAM_CACHE_NAME = param("cache-name", Type.STRING, "The name of the cache");
    private static final FunctionParameterSequenceType FS_PARAM_KEY = manyParam("key", Type.ANY_TYPE, "The key");

    private static final String FS_CREATE_NAME = "create";
    static final FunctionSignature FS_CREATE_CACHE = functionSignature(
            FS_CREATE_NAME,
            "Explicitly create a cache with a specific configuration",
            returns(Type.BOOLEAN, "true if the cache was created, false if the cache already exists"),
            FS_PARAM_CACHE_NAME,
            param("config", Type.MAP, "A map with configuration for the cache. At present cache LRU and permission groups may be specified, for operations on the cache. `maximumSize` is optional and specifies the maximum number of entries. `expireAfterAccess` is optional and specifies the expiry period for infrequently accessed entries (in milliseconds). If a permission group is not specified for an operation, then permissions are not checked for that operation. Should have the format: map { \"maximumSize\": 1000, \"expireAfterAccess\": 120000, \"permissions\": map { \"put-group\": \"group1\", \"get-group\": \"group2\", \"remove-group\": \"group3\", \"clear-group\": \"group4\"} }")
    );

    private static final String FS_NAMES_NAME = "names";
    static final FunctionSignature FS_NAMES = functionSignature(
            FS_NAMES_NAME,
            "Get the names of all current caches",
            returnsOptMany(Type.STRING, "The names of all caches currently in use.")
    );

    private static final String FS_PUT_NAME = "put";
    static final FunctionSignature FS_PUT = functionSignature(
            FS_PUT_NAME,
            "Put data with a key into the identified cache. Returns the previous value associated with the key",
            returnsOptMany(Type.ITEM, "The previous value associated with the key"),
            FS_PARAM_CACHE_NAME,
            FS_PARAM_KEY,
            optManyParam("value", Type.ITEM, "The value")
    );

    private static final String FS_LIST_NAME = "list";
    static final FunctionSignature FS_LIST = functionSignature(
            FS_LIST_NAME,
            "List all values (for the associated keys) stored in a cache.",
            returnsOptMany(Type.ITEM, "The values associated with the keys"),
            FS_PARAM_CACHE_NAME,
            optManyParam("keys", Type.ANY_TYPE, "The keys, if none are specified, all values are returned")
    );

    private static final String FS_KEYS_NAME = "keys";
    static final FunctionSignature FS_KEYS = functionSignature(
            FS_KEYS_NAME,
            "List all keys stored in a cache. Note this operation is expensive.",
            returnsOptMany(Type.STRING, "The keys in the cache. Note these will be returned in serialized string form, as that is used internally."),
            FS_PARAM_CACHE_NAME
    );

    private static final String FS_GET_NAME = "get";
    static final FunctionSignature FS_GET = functionSignature(
            FS_GET_NAME,
            "Get data from identified global cache by key",
            returnsOptMany(Type.ITEM, "The value associated with the key"),
            FS_PARAM_CACHE_NAME,
            FS_PARAM_KEY
    );

    private static final String FS_REMOVE_NAME = "remove";
    static final FunctionSignature FS_REMOVE = functionSignature(
            FS_REMOVE_NAME,
            "Remove data from the identified cache by the key. Returns the value that was previously associated with key",
            returnsOptMany(Type.ITEM, "The value that was previously associated with the key"),
            FS_PARAM_CACHE_NAME,
            FS_PARAM_KEY
    );

    private static final String FS_CLEAR_NAME = "clear";
    static final FunctionSignature[] FS_CLEAR = functionSignatures(
            FS_CLEAR_NAME,
            "Clears all key/values from either all caches or the named cache",
            returnsNothing(),
            arities(
                    arity(),
                    arity(
                            FS_PARAM_CACHE_NAME
                    )
            )
    );

    private static final String FS_CLEANUP_NAME = "cleanup";
    static final FunctionSignature FS_CLEANUP = functionSignature(
            FS_CLEANUP_NAME,
            "Eviction policy work of the cache is performed asynchronously. Performs any pending maintenance operations needed by the cache, on the current thread. Typically not needed by users, and only used for testing scenarios. Requires 'clear' permissions.",
            returnsNothing(),
            FS_PARAM_CACHE_NAME
    );

    private static final String FS_DESTROY_NAME = "destroy";
    static final FunctionSignature FS_DESTROY = functionSignature(
            FS_DESTROY_NAME,
            "Destroys a cache entirely",
            returnsNothing(),
            FS_PARAM_CACHE_NAME
    );

    public CacheFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String cacheName;
        if(args.length > 0) {
            cacheName = args[0].itemAt(0).getStringValue();
        } else {
            cacheName = null;
        }

        switch (getName().getLocalPart()) {

            case FS_CREATE_NAME:
                if(CacheModule.caches.containsKey(cacheName)) {
                    return BooleanValue.FALSE;
                }
                return BooleanValue.valueOf(createCache(cacheName, extractCacheConfig((MapType)args[1])));

            case FS_NAMES_NAME:
                return cacheNames();

            case FS_PUT_NAME:
                // lazy create cache if it doesn't exist
                if(!CacheModule.caches.containsKey(cacheName)) {
                    lazilyCreateCache(cacheName);
                }
                final String putKey = toMapKey(args[1]);
                final Sequence value = args[2];
                return put(cacheName, putKey, value);

            case FS_LIST_NAME:
                // lazy create cache if it doesn't exist
                if(!CacheModule.caches.containsKey(cacheName)) {
                    lazilyCreateCache(cacheName);
                }
                final String[] keys = toMapKeys(args[1]);
                return list(cacheName, keys);

            case FS_KEYS_NAME:
                // lazy create cache if it doesn't exist
                if(!CacheModule.caches.containsKey(cacheName)) {
                    lazilyCreateCache(cacheName);
                }
                return listKeys(cacheName);

            case FS_GET_NAME:
                // lazy create cache if it doesn't exist
                if(!CacheModule.caches.containsKey(cacheName)) {
                    lazilyCreateCache(cacheName);
                }
                final String getKey = toMapKey(args[1]);
                return get(cacheName, getKey);

            case FS_REMOVE_NAME:
                // lazy create cache if it doesn't exist
                if(!CacheModule.caches.containsKey(cacheName)) {
                    lazilyCreateCache(cacheName);
                }
                final String removeKey = toMapKey(args[1]);
                return remove(cacheName, removeKey);

            case FS_CLEAR_NAME:
                if(args.length == 0) {
                    // clear all caches
                    clearAll();
                } else {
                    // clear specific cache
                    if(CacheModule.caches.containsKey(cacheName)) {
                       // only clear the cache if it exists
                       clear(cacheName);
                    }
                }
                return Sequence.EMPTY_SEQUENCE;

            case FS_CLEANUP_NAME:
                if(CacheModule.caches.containsKey(cacheName)) {
                    // only cleanup the cache if it exists
                    cleanup(cacheName);
                }
                return Sequence.EMPTY_SEQUENCE;

            case FS_DESTROY_NAME:
                // destroy specific cache
                final Cache oldCache = CacheModule.caches.remove(cacheName);
                if(oldCache != null) {
                    // only clear the cache after we have removed it
                    oldCache.clear();
                }
                return Sequence.EMPTY_SEQUENCE;

            default:
                throw new XPathException(this, "No function: " + getName() + "#" + getSignature().getArgumentCount());
        }
    }

    private CacheConfig extractCacheConfig(final MapType configMap) throws XPathException {
        final Sequence permsSeq = configMap.get(new StringValue("permissions"));

        final Optional<CacheConfig.Permissions> permissions;
        if(permsSeq != null && permsSeq.getItemCount() > 0) {
            final MapType permsMap = (MapType)permsSeq.itemAt(0);
            final Optional<String> putGroup = getStringValue("put-group", permsMap);
            final Optional<String> getGroup = getStringValue("get-group", permsMap);
            final Optional<String> removeGroup = getStringValue("remove-group", permsMap);
            final Optional<String> clearGroup = getStringValue("clear-group", permsMap);
            permissions = Optional.of(new CacheConfig.Permissions(putGroup, getGroup, removeGroup, clearGroup));
        } else {
            permissions = Optional.empty();
        }

        final Sequence maximumSizeSeq = configMap.get(new StringValue("maximumSize"));
        final Optional<Long> maximumSize;
        if(maximumSizeSeq != null && maximumSizeSeq.getItemCount() == 1) {
            final long l = maximumSizeSeq.itemAt(0).toJavaObject(Long.class);
            maximumSize = Optional.of(l);
        } else {
            maximumSize = Optional.empty();
        }

        final Sequence expireAfterAccessSeq = configMap.get(new StringValue("expireAfterAccess"));
        final Optional<Long> expireAfterAccess;
        if(expireAfterAccessSeq != null && expireAfterAccessSeq.getItemCount() == 1) {
            final long l = expireAfterAccessSeq.itemAt(0).toJavaObject(Long.class);
            expireAfterAccess = Optional.of(l);
        } else {
            expireAfterAccess = Optional.empty();
        }

        return new CacheConfig(permissions, maximumSize, expireAfterAccess);
    }

    private Optional<String> getStringValue(final String key, final AbstractMapType map) {
        return Optional.ofNullable(map.get(new StringValue(key))).filter(v -> !v.isEmpty()).flatMap(v -> Optional.ofNullable(((StringValue)v).getStringValue()));
    }

    private boolean createCache(final String cacheName, final CacheConfig config) {
        // we must test for preemption, i.e the cache may have already been created
        final Cache newOrExisting = CacheModule.caches.computeIfAbsent(cacheName, key -> new Cache(config));

        // is new
        return newOrExisting.getConfig() == config;
    }

    private void lazilyCreateCache(final String cacheName) throws XPathException {
        final CacheModule cacheModule = (CacheModule) getParentModule();
        final Optional<CacheConfig> maybeLazyCacheConfig = cacheModule.getLazyCacheConfig();

        if (!maybeLazyCacheConfig.isPresent()) {
            throw new XPathException(this, LAZY_CREATION_DISABLED, "There is no such named cache: " + cacheName + ", and lazy creation of the cache has been disabled.");
        }

        createCache(cacheName, maybeLazyCacheConfig.get());
    }

    private Sequence cacheNames() throws XPathException {
        final Sequence result = new ValueSequence();
        for(final String cacheName : CacheModule.caches.keySet()) {
            result.add(new StringValue(cacheName));
        }
        return result;
    }

    private Sequence put(final String cacheName, final String key, final Sequence value) throws XPathException {
        final Cache cache = CacheModule.caches.get(cacheName);

        // check permissions
        if(!context.getEffectiveUser().hasDbaRole()) {
            final Optional<String> putGroup = cache.getConfig().getPermissions().flatMap(CacheConfig.Permissions::getPutGroup);
            if (putGroup.isPresent()) {
                if (!context.getEffectiveUser().hasGroup(putGroup.get())) {
                    throw new XPathException(this, INSUFFICIENT_PERMISSIONS, "User does not have the appropriate permissions to put data into this cache");
                }
            }
        }

        return cache.put(key, value);
    }

    private Sequence list(final String cacheName, final String[] keys) throws XPathException {
        final Cache cache = CacheModule.caches.get(cacheName);

        // check permissions
        if(!context.getEffectiveUser().hasDbaRole()) {
            final Optional<String> getGroup = cache.getConfig().getPermissions().flatMap(CacheConfig.Permissions::getGetGroup);
            if (getGroup.isPresent()) {
                if (!context.getEffectiveUser().hasGroup(getGroup.get())) {
                    throw new XPathException(this, INSUFFICIENT_PERMISSIONS, "User does not have the appropriate permissions to list data in this cache");
                }
            }
        }

        return cache.list(keys);
    }

    private Sequence listKeys(final String cacheName) throws XPathException {
        final Cache cache = CacheModule.caches.get(cacheName);

        // check permissions
        if(!context.getEffectiveUser().hasDbaRole()) {
            final Optional<String> getGroup = cache.getConfig().getPermissions().flatMap(CacheConfig.Permissions::getGetGroup);
            if (getGroup.isPresent()) {
                if (!context.getEffectiveUser().hasGroup(getGroup.get())) {
                    throw new XPathException(this, INSUFFICIENT_PERMISSIONS, "User does not have the appropriate permissions to list data in this cache");
                }
            }
        }

        return cache.listKeys();
    }

    private Sequence get(final String cacheName, final String key) throws XPathException {
        final Cache cache = CacheModule.caches.get(cacheName);

        // check permissions
        if(!context.getEffectiveUser().hasDbaRole()) {
            final Optional<String> getGroup = cache.getConfig().getPermissions().flatMap(CacheConfig.Permissions::getGetGroup);
            if (getGroup.isPresent()) {
                if (!context.getEffectiveUser().hasGroup(getGroup.get())) {
                    throw new XPathException(this, INSUFFICIENT_PERMISSIONS, "User does not have the appropriate permissions to get data from this cache");
                }
            }
        }

        return cache.get(key);
    }

    private Sequence remove(final String cacheName, final String key) throws XPathException {
        final Cache cache = CacheModule.caches.get(cacheName);

        // check permissions
        if(!context.getEffectiveUser().hasDbaRole()) {
            final Optional<String> removeGroup = cache.getConfig().getPermissions().flatMap(CacheConfig.Permissions::getRemoveGroup);
            if (removeGroup.isPresent()) {
                if (!context.getEffectiveUser().hasGroup(removeGroup.get())) {
                    throw new XPathException(this, INSUFFICIENT_PERMISSIONS, "User does not have the appropriate permissions to remove data from this cache");
                }
            }
        }

        return cache.remove(key);
    }

    private void clearAll() throws XPathException {
        final Collection<Cache> caches = CacheModule.caches.values();

        // check all permissions first
        if(!context.getEffectiveUser().hasDbaRole()) {
            for (final Cache cache : caches) {
                final Optional<String> clearGroup = cache.getConfig().getPermissions().flatMap(CacheConfig.Permissions::getClearGroup);
                if (clearGroup.isPresent()) {
                    if (!context.getEffectiveUser().hasGroup(clearGroup.get())) {
                        throw new XPathException(this, INSUFFICIENT_PERMISSIONS, "User does not have the appropriate permissions to clear data from all caches");
                    }
                }
            }
        }

        // finally clear the caches
        for(final Cache cache: caches) {
            cache.clear();
        }
    }

    private void clear(final String cacheName) throws XPathException {
        final Cache cache = CacheModule.caches.get(cacheName);

        // check permissions
        if(!context.getEffectiveUser().hasDbaRole()) {
            final Optional<String> clearGroup = cache.getConfig().getPermissions().flatMap(CacheConfig.Permissions::getClearGroup);
            if (clearGroup.isPresent()) {
                if (!context.getEffectiveUser().hasGroup(clearGroup.get())) {
                    throw new XPathException(this, INSUFFICIENT_PERMISSIONS, "User does not have the appropriate permissions to clear data from this cache");
                }
            }
        }

        cache.clear();
    }

    private void cleanup(final String cacheName) throws XPathException {
        final Cache cache = CacheModule.caches.get(cacheName);

        // check permissions
        if(!context.getEffectiveUser().hasDbaRole()) {
            final Optional<String> clearGroup = cache.getConfig().getPermissions().flatMap(CacheConfig.Permissions::getClearGroup);
            if (clearGroup.isPresent()) {
                if (!context.getEffectiveUser().hasGroup(clearGroup.get())) {
                    throw new XPathException(this, INSUFFICIENT_PERMISSIONS, "User does not have the appropriate permissions to clear data from this cache");
                }
            }
        }

        cache.cleanup();
    }

    private String toMapKey(final Sequence key) throws XPathException {
        if(key.getItemCount() == 1) {
            final Item item1 = key.itemAt(0);
            if (item1.getType() == Type.STRING) {
                return item1.getStringValue();
            }
        }
        return serializeKey(key);
    }

    private String[] toMapKeys(final Sequence keys) throws XPathException {
        final String[] mapKeys = new String[keys.getItemCount()];

        final Serializer serializer = context.getBroker().getSerializer();
        serializer.reset();
        try {
            serializer.setProperties(OUTPUT_PROPERTIES);
            int i = 0;
            for (final SequenceIterator it = keys.iterate(); it.hasNext(); ) {
                final Item item = it.nextItem();
                try {
                    final NodeValue node = (NodeValue) item;
                    mapKeys[i] = serializer.serialize(node);
                } catch (final ClassCastException e) {
                    mapKeys[i] = item.getStringValue();
                }
                i++;
            }
        } catch (final SAXException e) {
            throw new XPathException(this, KEY_SERIALIZATION, e);
        }

        return mapKeys;
    }

    private String serializeKey(final Sequence key) throws XPathException {
        final StringBuilder builder = new StringBuilder();
        final Serializer serializer = context.getBroker().getSerializer();
        serializer.reset();
        try {
            serializer.setProperties(OUTPUT_PROPERTIES);
            for (final SequenceIterator i = key.iterate(); i.hasNext(); ) {
                final Item item = i.nextItem();
                try {
                    final NodeValue node = (NodeValue) item;
                    builder.append(serializer.serialize(node));
                } catch (final ClassCastException e) {
                    builder.append(item.getStringValue());
                }
            }
            return builder.toString();
        } catch (final SAXException e) {
            throw new XPathException(this, KEY_SERIALIZATION, e);
        }
    }
}
