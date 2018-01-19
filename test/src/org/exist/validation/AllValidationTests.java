/*
 *
 * eXist Open Source Native XML Database
 * Copyright (C) 2009 The eXist team
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
 *
 * $Id$
 */

package org.exist.validation;

import com.googlecode.junittoolbox.ParallelSuite;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 *
 * @author Adam Retter <adam@exist-db.org>
 */

@RunWith(ParallelSuite.class)
@Suite.SuiteClasses({
    DatabaseInsertResources_NoValidation_Test.class,
    DatabaseInsertResources_WithValidation_Test.class
})
public class AllValidationTests
{
}