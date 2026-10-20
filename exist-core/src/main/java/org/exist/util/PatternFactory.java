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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.regex.Pattern;

/**
 * A simple Java Regular Expression Pattern Factory.
 *
 * Patterns are Cached in a LRU like Cache
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class PatternFactory {

    private static final PatternFactory instance = new PatternFactory();

    private final Cache<PatternKey, Pattern> cache;

    private PatternFactory() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .build();
    }

    public static PatternFactory getInstance() {
        return instance;
    }

    public Pattern getPattern(final String pattern) {
        return getPattern(pattern, 0);
    }

    public Pattern getPattern(final String pattern, final int flags) {
        return cache.get(new PatternKey(pattern, flags), key -> Pattern.compile(key.pattern(), key.flags()));
    }

    /**
     * Identifies a compiled pattern by both its source and its flags. Concatenating the two into a
     * string would let different pairs collide, e.g. {@code ("a1", 0)} and {@code ("a", 10)}.
     */
    private record PatternKey(String pattern, int flags) {
    }
}
