/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery;

import net.jcip.annotations.Immutable;
import org.exist.dom.QName;
import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;

import java.util.function.Supplier;

@Immutable
public class DynamicVariable implements Variable {

    private final QName name;
    private final Supplier<Sequence> valueSupplier;

    public DynamicVariable(final QName name, final Supplier<Sequence> valueSupplier) {
        this.name = name;
        this.valueSupplier = valueSupplier;
    }

    @Override
    public void setValue(final Sequence val) {
        throwImmutable();
    }

    @Override
    public Sequence getValue() {
        return valueSupplier.get();
    }

    @Override
    public QName getQName() {
        return name;
    }

    @Override
    public int getType() {
        return valueSupplier.get().getItemType();
    }

    @Override
    public void setSequenceType(final SequenceType type) {
        throwImmutable();
    }

    @Override
    public SequenceType getSequenceType() {
        final Sequence value = getValue();
        return new SequenceType(value.getItemType(), value.getCardinality());
    }

    @Override
    public void setStaticType(final int type) {
        throwImmutable();
    }

    @Override
    public int getStaticType() {
        return getType();
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public void setIsInitialized(final boolean initialized) {
    }

    @Override
    public int getDependencies(final XQueryContext context) {
        return 0;
    }

    @Override
    public int getCardinality() {
        return getValue().getCardinality();
    }

    @Override
    public void setStackPosition(final int position) {
    }

    @Override
    public DocumentSet getContextDocs() {
        return DocumentSet.EMPTY_DOCUMENT_SET;
    }

    @Override
    public void setContextDocs(final DocumentSet docs) {
        throwImmutable();
    }

    @Override
    public void checkType() {

    }

    private static void throwImmutable() {
        throw new UnsupportedOperationException("Changing a dynamic variable is not permitted");
    }
}
