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
package org.exist.util.serializer;

import org.apache.commons.pool.BaseKeyedPoolableObjectFactory;

public class SerializerObjectFactory extends BaseKeyedPoolableObjectFactory<Class<?>, Object> {

    @Override
    public Object makeObject(final Class<?> key) {
        if (key == SAXSerializer.class) {
            return new SAXSerializer();
        } else if (key == DOMStreamer.class) {
            return new ExtendedDOMStreamer();
        }
        return null;
    }

    // TODO(AR) we likely need only call #reset() on #passivateObject(Class, Object)!
    @Override
    public void activateObject(final Class<?> key, final Object obj) {
        if (key == SAXSerializer.class) {
            ((SAXSerializer) obj).reset();
        } else if (key == DOMStreamer.class) {
            ((DOMStreamer) obj).reset();
        }
    }

    @Override
    public void passivateObject(final Class<?> key, final Object obj) {
        if (key == SAXSerializer.class) {
            ((SAXSerializer) obj).reset();
        } else if (key == DOMStreamer.class) {
            ((DOMStreamer) obj).reset();
        }
    }
}
