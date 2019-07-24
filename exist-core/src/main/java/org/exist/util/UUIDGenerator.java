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
package org.exist.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.NameBasedGenerator;
import com.fasterxml.uuid.impl.RandomBasedGenerator;

/**
 * UUID generator.
 *
 * See <a href="http://en.wikipedia.org/wiki/UUID">UUID</a>
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author Dannes Wessels
 */
public class UUIDGenerator {

    private final static UUID EXISTDB_UUID_NAMESPACE = UUID.fromString("a99c2b03-67c4-49fb-b812-aa4c12099e65");

    private final static RandomBasedGenerator UUIDv4_GENERATOR = Generators.randomBasedGenerator();
    private final static NameBasedGenerator UUIDv3_GENERATOR;
    static {
        try {
            UUIDv3_GENERATOR = Generators.nameBasedGenerator(EXISTDB_UUID_NAMESPACE, MessageDigest.getInstance("MD5"));
        } catch (final NoSuchAlgorithmException e) {
            // NOTE: very very unlikely, MD5 is widely supported in various JDKs
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    
    /**
     * Generate random UUID code.
     *
     * @return UUID code, formatted as f271ec43-bf1f-4030-a269-b11576538f71
     */
    public static String getUUID() {
        return getUUIDversion4();
    }

    /**
     * Generate a version 4 UUID.
     *
     * See <a href="http://en.wikipedia.org/wiki/Universally_Unique_Identifier#Version_4_.28random.29">http://en.wikipedia.org/wiki/Universally_Unique_Identifier#Version_4_.28random.29</a>.
     *
     * @return a Version 4 UUID
     */
    public static String getUUIDversion4() {
        return UUIDv4_GENERATOR.generate().toString();
    }

    /**
     * Generate a version 3 UUID code.
     *
     * See <a href="http://en.wikipedia.org/wiki/Universally_Unique_Identifier#Version_3_.28MD5_hash.29">http://en.wikipedia.org/wiki/Universally_Unique_Identifier#Version_3_.28MD5_hash.29</a>
     *
     * @param name the name to generate a UUID for.
     *
     * @return a Version 3 UUID
     */
    public static String getUUIDversion3(final String name) {
        return UUIDv3_GENERATOR.generate(name).toString();
    }
}
