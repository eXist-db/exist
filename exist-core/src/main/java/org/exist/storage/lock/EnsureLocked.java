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

import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.Lock.LockType;

/**
 * An annotation for indicating that certain locks
 * must be held on parameters to a method or return types.
 *
 * As well as explicitly expressing intention, this annotation can be used
 * with {@link EnsureLockingAspect} to compile into the code runtime checks
 * which will enforce the locking policy.
 *
 * Typically this is used with parameters of type {@link org.exist.collections.Collection}
 * and {@link org.exist.dom.persistent.DocumentImpl}. If this annotation is
 * used on an {@link org.exist.xmldb.XmldbURI} then a {@code type} value must
 * also be provided to indicate the type of the lock identified by the uri.
 *
 * For example we may indicate that Collection parameters to methods
 * must already be locked appropriately before the method is called:
 * <pre>
 * public Result copyCollection(
 *          {@code @EnsureLocked(mode=LockMode.READ_LOCK)} final Collection srcCollection,
 *          {@code @EnsureLocked(mode=LockMode.WRITE_LOCK)} final Collection destCollection) {
 *
 *    ...
 *
 * }
 * </pre>
 *
 * We may also indicate that objects returned from a function must have gained an appropriate
 * lock for the calling thread:
 *
 * <pre>
 * public {@code @EnsureLocked(mode=LockMode.READ_LOCK)} Collection openCollection(final XmldbURI uri, final LockMode lockMode) {
 *
 *    ...
 *
 * }
 * }</pre>
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD, ElementType.PARAMETER})
public @interface EnsureLocked {

    /**
     * Specifies the mode of the held lock.
     *
     * {@link LockMode#NO_LOCK} is used as the default, to allow {@code modeParam}
     * to be used instead.
     *
     * If neither {@code mode} or {@code modeParam} are specified, and there is not a
     * single {@link Lock.LockMode} type parameter that can be used
     * then an IllegalArgumentException will be generated if {@link EnsureLockingAspect}
     * detects this situation.
     *
     * @return the lock mode
     */
    Lock.LockMode mode() default LockMode.NO_LOCK;

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

    /**
     * The type of the lock.
     *
     * Only needed if the annotation is not placed on a
     * {@link org.exist.collections.Collection} or {@link org.exist.dom.persistent.DocumentImpl}
     * parameter or return type.
     *
     * @return the lock type
     */
    Lock.LockType type() default LockType.UNKNOWN;
}
