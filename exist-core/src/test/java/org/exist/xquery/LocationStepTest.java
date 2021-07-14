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
package org.exist.xquery;

import org.exist.xquery.util.ExpressionDumper;
import org.junit.Test;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertArrayEquals;

public class LocationStepTest {

    @Test
    public void insertPredicateNoPrevious() {
        final XQueryContext mockContext = mock(XQueryContext.class);
        expect(mockContext.nextExpressionId()).andReturn(Expression.EXPRESSION_ID_INVALID);

        final Predicate mockPredicate1 = mock(Predicate.class);
        final Predicate mockPredicate2 = mock(Predicate.class);
        final Predicate mockPredicate3 = mock(Predicate.class);
        final Predicate mockNotPreviousPredicate = mock(Predicate.class);

        mockPredicate1.dump(anyObject(ExpressionDumper.class));
        mockPredicate2.dump(anyObject(ExpressionDumper.class));
        mockNotPreviousPredicate.dump(anyObject(ExpressionDumper.class));

        replay(mockContext, mockPredicate1, mockPredicate2, mockPredicate3, mockNotPreviousPredicate);

        final LocationStep locationStep = new LocationStep(mockContext, Constants.UNKNOWN_AXIS);

        locationStep.addPredicate(mockPredicate1);
        locationStep.addPredicate(mockPredicate2);

        locationStep.insertPredicate(mockNotPreviousPredicate, mockPredicate3);
        assertArrayEquals(new Predicate[]{ mockPredicate1, mockPredicate2}, locationStep.getPredicates());

        verify(mockContext, mockPredicate1, mockPredicate2, mockPredicate3, mockNotPreviousPredicate);
    }

    @Test
    public void insertPredicateInMiddleOddFirst() {
        final XQueryContext mockContext = mock(XQueryContext.class);
        expect(mockContext.nextExpressionId()).andReturn(Expression.EXPRESSION_ID_INVALID);

        final Predicate mockPredicate1 = mock(Predicate.class);
        final Predicate mockPredicate2 = mock(Predicate.class);
        final Predicate mockPredicate3 = mock(Predicate.class);

        replay(mockContext, mockPredicate1, mockPredicate2, mockPredicate3);

        final LocationStep locationStep = new LocationStep(mockContext, Constants.UNKNOWN_AXIS);

        locationStep.addPredicate(mockPredicate1);
        locationStep.addPredicate(mockPredicate2);

        locationStep.insertPredicate(mockPredicate1, mockPredicate3);
        assertArrayEquals(new Predicate[]{ mockPredicate1, mockPredicate3, mockPredicate2}, locationStep.getPredicates());

        verify(mockContext, mockPredicate1, mockPredicate2, mockPredicate3);
    }

    @Test
    public void insertPredicateInMiddleOddSecond() {
        final XQueryContext mockContext = mock(XQueryContext.class);
        expect(mockContext.nextExpressionId()).andReturn(Expression.EXPRESSION_ID_INVALID);

        final Predicate mockPredicate1 = mock(Predicate.class);
        final Predicate mockPredicate2 = mock(Predicate.class);
        final Predicate mockPredicate3 = mock(Predicate.class);

        replay(mockContext, mockPredicate1, mockPredicate2, mockPredicate3);

        final LocationStep locationStep = new LocationStep(mockContext, Constants.UNKNOWN_AXIS);

        locationStep.addPredicate(mockPredicate1);
        locationStep.addPredicate(mockPredicate2);

        locationStep.insertPredicate(mockPredicate2, mockPredicate3);
        assertArrayEquals(new Predicate[]{ mockPredicate1, mockPredicate2, mockPredicate3}, locationStep.getPredicates());

        verify(mockContext, mockPredicate1, mockPredicate2, mockPredicate3);
    }

    @Test
    public void insertPredicateInMiddleEvenFirst() {
        final XQueryContext mockContext = mock(XQueryContext.class);
        expect(mockContext.nextExpressionId()).andReturn(Expression.EXPRESSION_ID_INVALID);

        final Predicate mockPredicate1 = mock(Predicate.class);
        final Predicate mockPredicate2 = mock(Predicate.class);
        final Predicate mockPredicate3 = mock(Predicate.class);
        final Predicate mockPredicate4 = mock(Predicate.class);

        replay(mockContext, mockPredicate1, mockPredicate2, mockPredicate3, mockPredicate4);

        final LocationStep locationStep = new LocationStep(mockContext, Constants.UNKNOWN_AXIS);

        locationStep.addPredicate(mockPredicate1);
        locationStep.addPredicate(mockPredicate2);
        locationStep.addPredicate(mockPredicate3);

        locationStep.insertPredicate(mockPredicate1, mockPredicate4);
        assertArrayEquals(new Predicate[]{ mockPredicate1, mockPredicate4, mockPredicate2, mockPredicate3}, locationStep.getPredicates());

        verify(mockContext, mockPredicate1, mockPredicate2, mockPredicate3, mockPredicate4);
    }

    @Test
    public void insertPredicateInMiddleEvenSecond() {
        final XQueryContext mockContext = mock(XQueryContext.class);
        expect(mockContext.nextExpressionId()).andReturn(Expression.EXPRESSION_ID_INVALID);

        final Predicate mockPredicate1 = mock(Predicate.class);
        final Predicate mockPredicate2 = mock(Predicate.class);
        final Predicate mockPredicate3 = mock(Predicate.class);
        final Predicate mockPredicate4 = mock(Predicate.class);

        replay(mockContext, mockPredicate1, mockPredicate2, mockPredicate3, mockPredicate4);

        final LocationStep locationStep = new LocationStep(mockContext, Constants.UNKNOWN_AXIS);

        locationStep.addPredicate(mockPredicate1);
        locationStep.addPredicate(mockPredicate2);
        locationStep.addPredicate(mockPredicate3);

        locationStep.insertPredicate(mockPredicate2, mockPredicate4);
        assertArrayEquals(new Predicate[]{ mockPredicate1, mockPredicate2, mockPredicate4, mockPredicate3}, locationStep.getPredicates());

        verify(mockContext, mockPredicate1, mockPredicate2, mockPredicate3, mockPredicate4);
    }

    @Test
    public void insertPredicateInMiddleEvenThird() {
        final XQueryContext mockContext = mock(XQueryContext.class);
        expect(mockContext.nextExpressionId()).andReturn(Expression.EXPRESSION_ID_INVALID);

        final Predicate mockPredicate1 = mock(Predicate.class);
        final Predicate mockPredicate2 = mock(Predicate.class);
        final Predicate mockPredicate3 = mock(Predicate.class);
        final Predicate mockPredicate4 = mock(Predicate.class);

        replay(mockContext, mockPredicate1, mockPredicate2, mockPredicate3, mockPredicate4);

        final LocationStep locationStep = new LocationStep(mockContext, Constants.UNKNOWN_AXIS);

        locationStep.addPredicate(mockPredicate1);
        locationStep.addPredicate(mockPredicate2);
        locationStep.addPredicate(mockPredicate3);

        locationStep.insertPredicate(mockPredicate3, mockPredicate4);
        assertArrayEquals(new Predicate[]{ mockPredicate1, mockPredicate2, mockPredicate3, mockPredicate4}, locationStep.getPredicates());

        verify(mockContext, mockPredicate1, mockPredicate2, mockPredicate3, mockPredicate4);
    }
}
