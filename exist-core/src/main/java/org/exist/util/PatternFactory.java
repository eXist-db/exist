/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.regex.Pattern;

/**
 * A simple Java Regular Expression Pattern Factory.
 *
 * Patterns are Cached in a LRU like Cache
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class PatternFactory {

    private static final PatternFactory instance = new PatternFactory();

    private final Cache<String, Pattern> cache;

    private PatternFactory() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .build();
    }

    public static PatternFactory getInstance() {
        return instance;
    }

    public Pattern getPattern(final String pattern) {
        return cache.get(pattern, ptn -> Pattern.compile(ptn));
    }

    public Pattern getPattern(final String pattern, final int flags) {
        return cache.get(pattern + flags, key -> Pattern.compile(pattern, flags));
    }
}
