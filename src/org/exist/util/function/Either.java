/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.util.function;

import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * A disjoint union, more basic than but similar to {@link scala.util.Either}
 *
 * @param <L> Type of left parameter
 * @param <R> Type of right parameter
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public abstract class Either<L, R> {
    
    private final boolean isLeft;
    
    Either(final boolean isLeft) {
        this.isLeft = isLeft;
    }
    
    public final boolean isLeft() {
        return isLeft;
    }

    public final boolean isRight() {
        return !isLeft;
    }
    
    public final LeftProjection<L, R> left() {
        return new LeftProjection(this);
    }
    
    public final RightProjection<L, R> right() {
        return new RightProjection(this);
    }

    /**
     * Map on the right-hand-side of the disjunction
     *
     * @param f The function to map with
     */
    public final <T> Either<L, T> map(final Function<R, T> f) {
        if(isLeft()) {
            return (Left<L, T>)this;
        } else {
            return Right(f.apply(((Right<L, R>)this).value));
        }
    }

    /**
     * Bind through on the right-hand-side of this disjunction
     *
     * @param f the function to bind through
     */
    public final <LL extends L, T> Either<LL, T> flatMap(final Function<R, Either<LL, T>> f) {
        if(isLeft) {
            return (Left<LL, T>)this;
        } else {
            return f.apply(((Right<L, R>)this).value);
        }
    }

    /**
     * Map on the left-hand-side of the disjunction
     *
     * @param f The function to map with
     */
    public final <T> Either<T, R> leftMap(final Function<L, T> f) {
        if(isLeft) {
            return Left(f.apply(((Left<L, R>)this).value));
        } else {
            return (Right<T, R>)this;
        }
    }

    /**
     * Catamorphism. Run the first given function if left,
     * otherwise the second given function
     *
     *
     * @param <T> The result type from performing the fold
     * @param lf A function that may be applied to the left-hand-side
     * @param rf A function that may be applied to the right-hand-side
     */
    public final <T> T fold(final Function<L, T> lf, final Function<R, T> rf) {
        if(isLeft) {
            return lf.apply(((Left<L, R>)this).value);
        } else {
            return rf.apply(((Right<L, R>)this).value);
        }
    }

    public final static <L, R> Either<L, R> Left(final L value) {
        return new Left<>(value);
    }

    public final static <L, R> Either<L, R> Right(final R value) {
        return new Right<>(value);
    }

    public final static class Left<L, R> extends Either<L, R> {
        final L value;
        private Left(final L value) {
            super(true);
            this.value = value;
        }
    }

    public final static class Right<L, R> extends Either<L, R> {
        final R value;
        private Right(final R value) {
            super(false);
            this.value = value;
        }
    }
    
    public final class LeftProjection<L, R> {
        final Either<L, R> e;
        private LeftProjection(final Either<L, R> e) {
            this.e = e;
        }
        
        public final L get() {
            if(e.isLeft()) {
                return ((Left<L, R>)e).value;
            } else {
                throw new NoSuchElementException("Either.left value on Right");
            }
        }
    }
    
    public final class RightProjection<L, R> {
        final Either<L, R> e;
        private RightProjection(final Either<L, R> e) {
            this.e = e;
        }
        
        public final R get() {
            if(e.isRight()) {
                return ((Right<L, R>)e).value;
            } else {
                throw new NoSuchElementException("Either.right value on Left");
            }
        }
    }
}
