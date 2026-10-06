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
package xquery.modules.expathrepo;

import org.exist.test.xqsuite.XQSuite;

/**
 * The files of this suite run concurrently, see {@link org.exist.test.xqsuite.XQSuite#parallel()} for what they must not share.
 * Files that install packages share the package registry of the database, whose changes are serialized by {@code ExistRepository}.
 */
@XQSuite(value = {
        "src/test/xquery/modules/expathrepo"
}, parallel = true)
public class ExpathRepoTests {
}
