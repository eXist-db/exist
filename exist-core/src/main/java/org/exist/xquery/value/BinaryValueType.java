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

import org.exist.xquery.XPathException;

import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.util.function.BiFunction;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public abstract class BinaryValueType<T extends FilterOutputStream> {

    private final int xqueryType;
    private final BiFunction<OutputStream, Boolean, T> coderFactory;

    public BinaryValueType(final int xqueryType, final BiFunction<OutputStream, Boolean, T> coderFactory) {
        this.xqueryType = xqueryType;
        this.coderFactory = coderFactory;
    }

    public int getXQueryType() {
        return xqueryType;
    }

    public T getEncoder(final OutputStream os) {
        return coderFactory.apply(os, true);
    }

    public T getDecoder(final OutputStream os) {
        return coderFactory.apply(os, false);
    }

    public String verifyAndFormatString(String str) throws XPathException {
        str = str.replaceAll("\\s", "");
        verifyString(str);
        return formatString(str);
    }

    protected abstract void verifyString(String str) throws XPathException;

    protected abstract String formatString(String str);
}