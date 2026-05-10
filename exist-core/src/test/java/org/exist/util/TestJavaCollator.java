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

package org.exist.util;

import com.ibm.icu.text.CollationKey;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RawCollationKey;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.util.VersionInfo;

/**
 * Test-only custom collator for java: URI resolution checks.
 */
public class TestJavaCollator extends Collator {

    private final Collator delegate = Collator.getInstance(ULocale.ENGLISH);

    @Override
    public int compare(final String source, final String target) {
        return delegate.compare(source, target);
    }

    @Override
    public CollationKey getCollationKey(final String source) {
        return delegate.getCollationKey(source);
    }

    @Override
    public RawCollationKey getRawCollationKey(final String source, final RawCollationKey key) {
        return delegate.getRawCollationKey(source, key);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public int getStrength() {
        return delegate.getStrength();
    }

    @Override
    public void setStrength(final int newStrength) {
        delegate.setStrength(newStrength);
    }

    @Override
    public int getDecomposition() {
        return delegate.getDecomposition();
    }

    @Override
    public void setDecomposition(final int decompositionMode) {
        delegate.setDecomposition(decompositionMode);
    }

    @Override
    public int setVariableTop(final String varTop) {
        return delegate.setVariableTop(varTop);
    }

    @Override
    public void setVariableTop(final int varTop) {
        delegate.setVariableTop(varTop);
    }

    @Override
    public int getVariableTop() {
        return delegate.getVariableTop();
    }

    @Override
    public VersionInfo getVersion() {
        return delegate.getVersion();
    }

    @Override
    public VersionInfo getUCAVersion() {
        return delegate.getUCAVersion();
    }

    @Override
    public boolean isFrozen() {
        return delegate.isFrozen();
    }

    @Override
    public Collator freeze() {
        delegate.freeze();
        return this;
    }

    @Override
    public Collator cloneAsThawed() {
        return delegate.cloneAsThawed();
    }
}

