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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class JDKCompatibility {

    /**
     * Gets the {@code modifiers} field from Field.class.
     *
     * Compatible with JDK 8 through 13.
     *
     * @return the modifiers field
     *
     * @throws NoSuchFieldException if the modifiers field does not exist
     * @throws IllegalAccessException if access is not permitted
     */
    public static Field getModifiersField() throws NoSuchFieldException, IllegalAccessException {
        Field modifiersField = null;
        try {
            // JDK 11, 10, 9, 8
            modifiersField = Field.class.getDeclaredField("modifiers");
        } catch (final NoSuchFieldException e) {
            // JDK 12+
            try {
                final Method getDeclaredFields0 = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
                final boolean accessibleBeforeSet = getDeclaredFields0.isAccessible();
                getDeclaredFields0.setAccessible(true);
                final Field[] fields = (Field[]) getDeclaredFields0.invoke(Field.class, false);
                getDeclaredFields0.setAccessible(accessibleBeforeSet);
                for (final Field field : fields) {
                    if ("modifiers".equals(field.getName())) {
                        modifiersField = field;
                        break;
                    }
                }
                if (modifiersField == null) {
                    throw e;
                }
            } catch (final NoSuchMethodException | InvocationTargetException ex) {
                e.addSuppressed(ex);
                throw e;
            }
        }

        return modifiersField;
    }
}
