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

import se.softhouse.jargo.Argument;
import se.softhouse.jargo.ParsedArguments;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility functions for working with Jargo
 */
public class ArgumentUtil {

    /**
     * Get the value of an optional argument.
     *
     * @param <T> the type of the argument.
     *
     * @param parsedArguments The arguments which have been parsed
     * @param argument The argument that we are looking for
     *
     * @return Some value or {@link Optional#empty()} if the
     *     argument was not supplied
     */
    public static <T> Optional<T> getOpt(final ParsedArguments parsedArguments, final Argument<T> argument) {
        if(parsedArguments.wasGiven(argument)) {
            return Optional.of(parsedArguments.get(argument));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Get the values of an optional argument.
     *
     * @param <T> the type of the argument.
     *
     * @param parsedArguments The arguments which have been parsed
     * @param argument The argument that we are looking for
     *
     * @return A list of the provided argument values, or
     *     an empty list if the argument was not supplied
     */
    public static <T> List<T> getListOpt(final ParsedArguments parsedArguments, final Argument<List<T>> argument) {
        return getOpt(parsedArguments, argument)
                .orElseGet(() -> Collections.emptyList());
    }

    /**
     * Get the value of an optional file argument
     *
     * @param parsedArguments The arguments which have been parsed
     * @param argument The argument that we are looking for
     *
     * @return Some {@link java.nio.file.Path} or
     *     {@link Optional#empty()} if the argument was not supplied
     */
    public static Optional<Path> getPathOpt(final ParsedArguments parsedArguments, final Argument<File> argument) {
        return getOpt(parsedArguments, argument).map(File::toPath);
    }

    public static List<Path> getPathsOpt(final ParsedArguments parsedArguments, final Argument<List<File>> argument) {
        try(final Stream<File> files = getListOpt(parsedArguments, argument).stream()) {
            return files.map(File::toPath)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Get the value of an option argument
     *
     * @param parsedArguments The arguments which have been parsed
     * @param argument The option argument that we are looking for
     *
     * @return true if the option was set, false otherwise
     */
    public static boolean getBool(final ParsedArguments parsedArguments, final Argument<Boolean> argument) {
        return getOpt(parsedArguments, argument)
                .flatMap(Optional::ofNullable)
                .orElse(false);
    }
}
