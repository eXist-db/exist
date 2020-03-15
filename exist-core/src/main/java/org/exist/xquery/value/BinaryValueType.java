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
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.function.BiFunction;

import static java.lang.invoke.MethodType.methodType;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public abstract class BinaryValueType<T extends FilterOutputStream> {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final int xqueryType;
    private final Class<T> coder;

    public BinaryValueType(int xqueryType, Class<T> coder) {
        this.xqueryType = xqueryType;
        this.coder = coder;
    }

    public int getXQueryType() {
        return xqueryType;
    }

    public T getEncoder(OutputStream os) throws IOException {
        return instantiateCoder(os, true);
    }

    public T getDecoder(OutputStream os) throws IOException {
        return instantiateCoder(os, false);
    }

    private T instantiateCoder(OutputStream stream, boolean encoder) throws IOException {
        try {
            final MethodHandle methodHandle = LOOKUP.findConstructor(coder, methodType(void.class, OutputStream.class, boolean.class));
            // NOTE we have to explicitly replace boolean.class with Boolean.class for the implementation
            final BiFunction<OutputStream, Boolean, T> c = (BiFunction<OutputStream, Boolean, T>)
                    LambdaMetafactory.metafactory(
                            LOOKUP,
                            "apply",
                            methodType(BiFunction.class),
                            methodHandle.type().changeParameterType(1, Boolean.class).erase(),
                            methodHandle,
                            methodHandle.type().changeParameterType(1, Boolean.class)
                    ).getTarget().invokeExact();
            final T f = c.apply(stream, encoder);
            return f;
        } catch (final Throwable e) {
            if (e instanceof InterruptedException) {
                // NOTE: must set interrupted flag
                Thread.currentThread().interrupt();
            }

            throw new IOException("Unable to get binary coder '" + coder.getName() + "': " + e.getMessage(), e);
        }
    }

    public String verifyAndFormatString(String str) throws XPathException {
        str = str.replaceAll("\\s", "");
        verifyString(str);
        return formatString(str);
    }

    protected abstract void verifyString(String str) throws XPathException;

    protected abstract String formatString(String str);
}