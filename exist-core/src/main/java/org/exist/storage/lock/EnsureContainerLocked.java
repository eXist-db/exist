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

package org.exist.storage.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation for indicating that certain locks
 * must be held on the containing object before
 * a method may be called.
 *
 * As well as explicitly expressing intention, this annotation can be used
 * with {@link EnsureLockingAspect} to compile into the code runtime checks
 * which will enforce the locking policy.
 *
 * Typically this is used on methods within implementations of {@link org.exist.collections.Collection}
 * and {@link org.exist.dom.persistent.DocumentImpl}.
 * The typical use is to ensure that a container holds appropriate locks (by URI)
 * when calling the method accessors on their internal state.
 *
 * <pre>
 * public class MyCollectonImpl implements Collection {
 *     final XmldbURI uri;
 *     public MyCollectionImpl(@EnsureLocked(mode=LockMode.READ_LOCK, type=LockType.COLLECTION) final XmldbURI uri) {
 *         this.uri = uri;
 *     }
 *
 *     public XmldbURI getUri() {
 *         return uri;
 *     }
 *
 *     ...
 *
 *     <code>@EnsureContainerLocked(mode=LockMode.READ_LOCK)</code>
 *     public int countDocuments() {
 *         return documents.size();
 *     }
 * }</pre>
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface EnsureContainerLocked {

    /**
     * Specifies the mode of the held lock.
     *
     * {@link Lock.LockMode#NO_LOCK} is used as the default, to allow {@code modeParam}
     * to be used instead.
     *
     * If neither {@code mode} or {@code modeParam} are specified, and there is not a
     * single {@link Lock.LockMode} type parameter that can be used
     * then an IllegalArgumentException will be generated if {@link EnsureLockingAspect}
     * detects this situation.
     * @return  the lock mode
     */
    Lock.LockMode mode() default Lock.LockMode.NO_LOCK;

    /**
     * Specifies that the mode of the held lock is informed
     * by a parameter to the method.
     *
     * The value of this attribute is the (zero-based) index
     * of the parameter within the method signature.
     *
     * @return the mode
     */
    short modeParam() default NO_MODE_PARAM;

    short NO_MODE_PARAM = -1;

}
