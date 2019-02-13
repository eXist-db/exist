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
 * @author Adam Retter <adam@exist-db.org>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD, ElementType.PARAMETER})
public @interface PermissionRequired {
    
    //int mode() default UNDEFINED;
    int user() default UNDEFINED;
    int group() default UNDEFINED;
    int mode() default UNDEFINED;

    public final static int UNDEFINED = 0;

    //user flags
    public final static int IS_DBA = 04;
    public final static int IS_OWNER = 02;

    //group flags
    public final static int IS_MEMBER = 40;
    
    //mode flags
    public final static int ACL_WRITE = 04;
    public final static int IS_SET_UID = 02;
    public final static int IS_SET_GID = 01;
}