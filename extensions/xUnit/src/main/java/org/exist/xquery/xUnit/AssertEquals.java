/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.xUnit;

import org.exist.xquery.Annotation;
import org.exist.xquery.AnnotationTriggerOnResult;
import org.exist.xquery.LiteralValue;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.junit.Assert;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class AssertEquals implements AnnotationTriggerOnResult {

	public static String name = "assertEquals";
	
	Annotation annotation;
	
	public AssertEquals(Annotation ann) {
		annotation = ann;
	}

	@Override
	public void trigger(Sequence seq) throws XPathException {
		String actual = seq.getStringValue();
		for (LiteralValue v : annotation.getValue()) {
			String expected = v.getValue().getStringValue();

			Assert.assertEquals(expected, actual);
		}
	}

}
