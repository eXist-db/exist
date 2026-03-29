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
package org.exist.xquery.value;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Represents an XQuery 4.0 record type: {@code record(name as xs:string, age as xs:integer)}.
 *
 * <p>A record type is a typed map descriptor. It specifies which keys a map
 * must have, what types the values must be, and whether additional keys
 * are allowed (extensible records).</p>
 *
 * <p>Design note: this class is aligned with the Parser branch's
 * SequenceType infrastructure (isRecordType, getFieldDeclarations,
 * isExtensible) so the branches merge cleanly.</p>
 */
public class RecordType {

    /**
     * A single field declaration in a record type.
     */
    public static class FieldDeclaration {
        private final String name;
        private final SequenceType type;
        private final boolean optional;

        public FieldDeclaration(final String name, @Nullable final SequenceType type, final boolean optional) {
            this.name = name;
            this.type = type;
            this.optional = optional;
        }

        public String getName() {
            return name;
        }

        @Nullable
        public SequenceType getType() {
            return type;
        }

        public boolean isOptional() {
            return optional;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(name);
            if (optional) {
                sb.append('?');
            }
            if (type != null) {
                sb.append(" as ").append(type);
            }
            return sb.toString();
        }
    }

    private final List<FieldDeclaration> fields;
    private final boolean extensible;

    public RecordType(final List<FieldDeclaration> fields, final boolean extensible) {
        this.fields = Collections.unmodifiableList(fields);
        this.extensible = extensible;
    }

    public List<FieldDeclaration> getFieldDeclarations() {
        return fields;
    }

    public boolean isExtensible() {
        return extensible;
    }

    /**
     * Check whether the given map matches this record type.
     *
     * @param map the map to check
     * @return true if the map matches all field declarations
     */
    public boolean matches(final org.exist.xquery.functions.map.AbstractMapType map) {
        // Check all required fields exist and have matching types
        for (final FieldDeclaration field : fields) {
            final Sequence value = map.get(new StringValue(field.getName()));
            if (value == null || value.isEmpty()) {
                if (!field.isOptional()) {
                    return false; // required field missing
                }
                continue;
            }
            // Check value type if declared
            if (field.getType() != null) {
                try {
                    if (!field.getType().checkType(value)) {
                        return false;
                    }
                } catch (final org.exist.xquery.XPathException e) {
                    return false;
                }
            }
        }

        // If not extensible, check no extra keys
        if (!extensible) {
            final Set<String> declaredNames = new HashSet<>();
            for (final FieldDeclaration field : fields) {
                declaredNames.add(field.getName());
            }
            for (final io.lacuna.bifurcan.IEntry<AtomicValue, Sequence> entry : map) {
                try {
                    if (!declaredNames.contains(entry.key().getStringValue())) {
                        return false; // extra key not allowed
                    }
                } catch (final org.exist.xquery.XPathException e) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("record(");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(fields.get(i));
        }
        if (extensible) {
            if (!fields.isEmpty()) {
                sb.append(", ");
            }
            sb.append('*');
        }
        sb.append(')');
        return sb.toString();
    }
}
