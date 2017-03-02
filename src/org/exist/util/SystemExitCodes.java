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

/**
 * Definitions of codes to use with {@link System#exit(int)}
 */
public class SystemExitCodes {

    public final static int OK_EXIT_CODE = 0;

    public final static int CATCH_ALL_GENERAL_ERROR_EXIT_CODE = 1;

    public final static int INVALID_ARGUMENT_EXIT_CODE = 3;
    public final static int NO_BROKER_EXIT_CODE = 4;
    public final static int TERMINATED_EARLY_EXIT_CODE = 5;
    public final static int PERMISSION_DENIED_EXIT_CODE = 6;
    public final static int IO_ERROR_EXIT_CODE = 7;
}
