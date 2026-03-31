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

/**
 * Definitions of codes to use with {@link System#exit(int)}
 */
public class SystemExitCodes {

    public static final int OK_EXIT_CODE = 0;

    public static final int CATCH_ALL_GENERAL_ERROR_EXIT_CODE = 1;

    public static final int INVALID_ARGUMENT_EXIT_CODE = 3;
    public static final int NO_BROKER_EXIT_CODE = 4;
    public static final int TERMINATED_EARLY_EXIT_CODE = 5;
    public static final int PERMISSION_DENIED_EXIT_CODE = 6;
    public static final int IO_ERROR_EXIT_CODE = 7;
}
