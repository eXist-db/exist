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
package org.exist.xquery;

import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;

/**
 * Marks an expression as being Materializable as per the
 * Materialization query execution model.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public interface Materializable {

    /**
     * Materialize the result.
     *
     * Depending on the context in which this expression is executed,
     * either the context sequence, the context item or both of them may
     * be set. An implementing class should know how to handle this.
     *
     * The general contract is as follows: if the {@link Dependency#CONTEXT_ITEM}
     * bit is set in the bit field returned by {@link Expression#getDependencies()}, the eval method will
     * be called once for every item in the context sequence. The <b>contextItem</b>
     * parameter will be set to the current item. Otherwise, the eval method will only be called
     * once for the whole context sequence and <b>contextItem</b> will be null.
     *
     * eXist-db tries to process the entire context set in one, single step whenever
     * possible. Thus, most classes only expect context to contain a list of
     * nodes which represents the current context of the expression.
     *
     * The position() function in XPath is an example for an expression,
     * which requires both, context sequence and context item to be set.
     *
     * The context sequence might be a node set, a sequence of atomic values or a single
     * node or atomic value.
     *
     * @param contextSequence the current context sequence, or null if there is no context sequence.
     * @param contextItem a single item, taken from context, or null if there is no context item.
     *                    This defines the item, the expression should work on.
     *
     * @return the result sequence.
     *
     * @throws XPathException if an error occurs during evaluation.
     */
    Sequence eval(@Nullable Sequence contextSequence, @Nullable Item contextItem) throws XPathException;
}
