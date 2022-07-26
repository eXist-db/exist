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

package org.exist.xquery.functions.fn;

import com.evolvedbinary.j8fu.tuple.Tuple3;
import io.lacuna.bifurcan.IEntry;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.serialize.CharacterMap;
import net.sf.saxon.serialize.CharacterMapIndex;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.z.IntHashMap;
import org.exist.util.CodePointString;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import java.util.*;
import java.util.function.BiFunction;

class SerializationParameters {

    static private class ParameterInfo {
        final String defaultValue;
        final boolean hasMany;
        final List<Integer> types;

        ParameterInfo(final String defaultValue, final boolean hasMany, final int... types) {

            this.defaultValue = defaultValue;
            this.hasMany = hasMany;
            this.types = new ArrayList<>();
            for (final int type : types) this.types.add(type);
        }

        ParameterInfo(final String defaultValue, final int... types) {
            this(defaultValue, false, types);
        }
    }

    static String asValue(final Sequence sequence, final String defaultValue) throws XPathException {
        if (sequence.isEmpty()) {
            return defaultValue;
        }

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sequence.getItemCount(); i++) {
            final Item item = sequence.itemAt(i);
            if (Type.subTypeOf(item.getType(), Type.BOOLEAN)) {
                sb.append(' ').append(((BooleanValue) item).getValue() ? "yes" : "no");
            } else if (Type.subTypeOf(item.getType(), Type.QNAME)) {
                sb.append(' ').append(((QNameValue)item).getQName().toURIQualifiedName());
            } else {
                sb.append(' ').append(item.getStringValue());
            }
        }
        return sb.toString().trim();
    }

    static final Map<String, Param> keysAndTypes = new HashMap<>();
    static {
        for (final Param param : Param.values()) {
            keysAndTypes.put(param.key, param);
        }
    }

    enum Param {
        ALLOW_DUPLICATE_NAMES(Type.BOOLEAN, "no"),
        BYTE_ORDER_MARK(Type.BOOLEAN, "no"),
        CDATA_SECTION_ELEMENTS(Type.QNAME, "()", true),
        DOCTYPE_PUBLIC(Type.STRING, "absent"),
        DOCTYPE_SYSTEM(Type.STRING, "absent"),
        ENCODING(Type.STRING,"utf-8"),
        ESCAPE_URI_ATTRIBUTES(Type.BOOLEAN, "yes"),
        HTML_VERSION(Type.DECIMAL, "5"),
        INCLUDE_CONTENT_TYPE(Type.BOOLEAN, "yes"),
        INDENT(Type.BOOLEAN, "no"),
        ITEM_SEPARATOR(Type.STRING, "absent"),
        //JSON_NODE_OUTPUT_METHOD
        MEDIA_TYPE(Type.STRING, ""),
        METHOD(Type.STRING, "xml"),
        NORMALIZATION_FORM(Type.STRING, "none"),
        OMIT_XML_DECLARATION(Type.BOOLEAN, "yes"),
        STANDALONE(Type.BOOLEAN, "omit"),
        SUPPRESS_INDENTATION(Type.QNAME, "()", true),
        UNDECLARE_PREFIXES(Type.BOOLEAN, "no"),
        USE_CHARACTER_MAPS(Type.MAP, "map{}"),
        VERSION(Type.STRING, "1.0");

        private final ParameterInfo info;
        private final String key;

        Param(final int type, final String defaultValue, final boolean hasMany) {
            this.info = new ParameterInfo(defaultValue, hasMany, type);
            this.key = this.name().toLowerCase(Locale.ROOT).replaceAll("_","-");
        }

        Param(final int type, final String defaultValue) {
            this(type, defaultValue, false);
        }
    }

    static private String getKeyValue(final IEntry<AtomicValue, Sequence> entry,
                                      final BiFunction<ErrorCodes.ErrorCode, String, XPathException> errorBuilder) throws XPathException {
        if (!Type.subTypeOf(entry.key().getType(), Type.STRING)) {
            throw errorBuilder.apply(ErrorCodes.XPTY0004,
                    "The key: " + entry.key() + " has type " + Type.getTypeName(entry.key().getType()) + " not a subtype of " + Type.getTypeName(Type.STRING));
        }
        return entry.key().getStringValue();
    }

    static private Sequence getEntryValue(final IEntry<AtomicValue, Sequence> entry,
                                          final ParameterInfo parameterInfo,
                                          final BiFunction<ErrorCodes.ErrorCode, String, XPathException> errorBuilder) throws XPathException {

        if (entry.value().isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final int count = entry.value().getItemCount();
        if (count == 1 || parameterInfo.hasMany) {
            for (int i = 0; i < count; i++) {
                final Item item = entry.value().itemAt(i);
                boolean typeChecked = false;
                for (final int possibleType : parameterInfo.types) {
                    if (Type.subTypeOf(item.getType(), possibleType)) {
                        typeChecked = true;
                    }
                }
                if (!typeChecked) {
                    throw errorBuilder.apply(ErrorCodes.XPTY0004,
                            "The value: " + entry.key() + " has type " + Type.getTypeName(item.getType()) + " for item " + i + ", not any of the expected types");
                }
            }

            return entry.value();
        }

        throw errorBuilder.apply(ErrorCodes.XPTY0004,
                "The value: " + entry.key() + " has multiple values in the sequence, and is required to have none or one.");
    }

    static private Tuple3<String, Sequence, Param> getEntry(
            final IEntry<AtomicValue, Sequence> entry,
            final BiFunction<ErrorCodes.ErrorCode, String, XPathException> errorBuilder) throws XPathException {

        final String key = getKeyValue(entry, errorBuilder);
        final Param param = keysAndTypes.get(key);
        if (param == null) {
            throw errorBuilder.apply(ErrorCodes.XPTY0004,
                    "The key: " + entry.key() + " is not a known serialization parameter.");
        }
        final Sequence value = getEntryValue(entry, param.info, errorBuilder);
        return new Tuple3<>(key, value, param);
    }

    static ParameterInfo characterMapEntryInfo = new ParameterInfo("", Type.STRING);

    static SerializationProperties getAsSerializationProperties(final MapType params,
                                                                final BiFunction<ErrorCodes.ErrorCode, String, XPathException> errorBuilder) throws XPathException {

        final SerializationProperties serializationProperties = new SerializationProperties(
                new Properties(),
                new CharacterMapIndex());

        for (final IEntry<AtomicValue, Sequence> param : params) {
            final Tuple3<String, Sequence, Param> entry = getEntry(param, errorBuilder);

            final String key = entry._1;

            if (entry._1.equals(Param.USE_CHARACTER_MAPS.key)) {
                final IntHashMap<String> characterMaps = new IntHashMap<>();

                final MapType mapType = (MapType) entry._2;
                for (final IEntry<AtomicValue, Sequence> mapEntry : mapType) {
                    final String mapKey = getKeyValue(mapEntry, errorBuilder);
                    final Sequence mapValue = getEntryValue(mapEntry, characterMapEntryInfo, errorBuilder);
                    final int codePoint = new CodePointString(mapKey).codePointAt(0);
                    characterMaps.put(codePoint, mapValue.getStringValue());
                }

                final CharacterMap characterMap = new CharacterMap(qNameCharacterMap, characterMaps);
                serializationProperties.getCharacterMapIndex().putCharacterMap(qNameCharacterMap, characterMap);
                serializationProperties.setProperty(Param.USE_CHARACTER_MAPS.key, qNameCharacterMap.getClarkName());
            } else {
                serializationProperties.setProperty(key, asValue(entry._2, entry._3.info.defaultValue));
            }
        }

        return serializationProperties;
    }

    static final StructuredQName qNameCharacterMap = new StructuredQName("", "http://www.exist-db.org", "fn-transform-charactermap");

    /**
     * {@link CharacterMap} doesn't provide a method of naming a combined map
     *
     * We need our combined map to have a name to find it in a {@link CharacterMapIndex}
     */
    static class NamedCombinedCharacterMap extends CharacterMap {

        private final StructuredQName name;

        public NamedCombinedCharacterMap(final StructuredQName name, final Iterable<CharacterMap> list) {
            super(list);
            this.name = name;
        }

        @Override public StructuredQName getName() {
            return this.name;
        }
    }

    /**
     * Combine the serialization properties from a compiled stylesheet
     * with the serialization properties supplied as parameters to fn:transform
     * so that a serializer can be configured to serialize using the correct combination
     * of both sets of parameters.
     *
     * There is an explicit step to combine character maps, which doesn't happen
     * using the raw {@link SerializationProperties#combineWith(SerializationProperties)} method.
     *
     * This has to be done as a separate step because the fn:transform serialization properties
     * are read before compilation, during the initial phase of reading parameters.
     * We have to wait to compile the stylesheet before we can get its serialization properties.
     *
     * That order could be changed for the fn:transform serialization property parameters..
     *
     * @param baseProperties
     * @param overrideProperties
     *
     * @return the carefully combined properties
     */
    static SerializationProperties combinePropertiesAndCharacterMaps(
            final SerializationProperties baseProperties,
            final SerializationProperties overrideProperties) {

        final SerializationProperties combinedProperties = overrideProperties.combineWith(baseProperties);

        final List<String> baseCharacterMapKeys = new ArrayList<>();
        final Optional<String[]> baseCharacterMapString = Optional.ofNullable(baseProperties.getProperty("use-character-maps")).map(s -> s.split(" "));
        if (baseCharacterMapString.isPresent()) {
            for (final String s : baseCharacterMapString.get()) {
                if (!s.isEmpty()) {
                    baseCharacterMapKeys.add(s);
                }
            }
        }

        final Optional<String> combinedCharacterMapKey = Optional.ofNullable(combinedProperties.getProperty("use-character-maps")).map(String::trim);
        if (combinedCharacterMapKey.isPresent()) {
            final List<CharacterMap> allMaps = new ArrayList<>();
            for (final String baseCharacterMapKey : baseCharacterMapKeys) {
                final CharacterMap baseCharacterMap = baseProperties.getCharacterMapIndex().getCharacterMap(new StructuredQName("", "", baseCharacterMapKey));
                allMaps.add(baseCharacterMap);
            }
            final CharacterMap combinedMap = combinedProperties.getCharacterMapIndex().getCharacterMap(qNameCharacterMap);
            allMaps.add(combinedMap);
            final CharacterMap repairedCombinedMap = new NamedCombinedCharacterMap(qNameCharacterMap, allMaps);
            combinedProperties.getCharacterMapIndex().putCharacterMap(
                    qNameCharacterMap,
                    repairedCombinedMap);
            combinedProperties.setProperty("use-character-maps", qNameCharacterMap.getClarkName());
        }

        return combinedProperties;
    }
}
