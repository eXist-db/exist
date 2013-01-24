/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2013 The eXist-db team
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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id: UserXQueryJob.java 18087 2013-01-22 22:54:40Z deliriumsky $
 */
package org.exist.scheduler;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public enum JobType {
    USER,
    SYSTEM
}