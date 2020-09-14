/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
