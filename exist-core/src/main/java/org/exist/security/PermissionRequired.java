/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist-db Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD, ElementType.PARAMETER})
public @interface PermissionRequired {
    
    // int mode() default UNDEFINED;
    byte user() default UNDEFINED;
    byte group() default UNDEFINED;
    byte mode() default UNDEFINED;

    int UNDEFINED = 0;

    // test that POSIX_CHOWN_RESTRICTED is not set
    byte NOT_POSIX_CHOWN_RESTRICTED = (byte)0x80;

    // user and group flags
    byte IS_MEMBER = 0x4;
    byte IS_DBA = 0x2;
    byte IS_OWNER = 0x1;

    
    // mode flags
    byte ACL_WRITE = 0x4;
    byte IS_SET_UID = 0x2;
    byte IS_SET_GID = 0x1;
}