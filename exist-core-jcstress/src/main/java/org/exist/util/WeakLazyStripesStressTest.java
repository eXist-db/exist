/**
 * eXist Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Project
 * info@exist-db.org
 * http://www.exist-db.org
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
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 */
package org.exist.util;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.Z_Result;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class WeakLazyStripesStressTest {

    @JCStressTest
    @Outcome(id = "false", expect = Expect.FORBIDDEN, desc = "Different Object for Same Key")
    @Outcome(id = "true", expect = Expect.ACCEPTABLE, desc = "Same Object for Same Key")
    @State
    public static class SameObjectForSameKey {
        WeakLazyStripes<String, Lock> lockMap = new WeakLazyStripes<>((key) -> new ReentrantLock());
        Object ar1;
        Object ar2;
        Object ar3;
        Object ar4;
        Object ar5;
        Object ar6;
        Object ar7;
        Object ar8;


        @Actor
        public void actor1() {
            ar1 = lockMap.get("key1");
        }

        @Actor
        public void actor2() {
            ar2 = lockMap.get("key1");
        }

        @Actor
        public void actor3() {
            ar3 = lockMap.get("key1");
        }

        @Actor
        public void actor4() {
            ar4 = lockMap.get("key1");
        }

        @Actor
        public void actor5() {
            ar5 = lockMap.get("key1");
        }

        @Actor
        public void actor6() {
            ar6 = lockMap.get("key1");
        }

        @Actor
        public void actor7() {
            ar7 = lockMap.get("key1");
        }

        @Actor
        public void actor8() {
            ar8 = lockMap.get("key1");
        }

        @Arbiter
        public void arbiter(Z_Result r) {
            r.r1 = ar1 == ar2 && ar2 == ar3 && ar3 == ar4 && ar4 == ar5 && ar5 == ar6 && ar6 == ar7 && ar7 == ar8;
        }
    }

    @JCStressTest
    @Outcome(id = "false", expect = Expect.FORBIDDEN, desc = "Same Object for Different Keys")
    @Outcome(id = "true", expect = Expect.ACCEPTABLE, desc = "Different Object for Different Keys")
    @State
    public static class DifferentObjectForDifferentKeys {
        WeakLazyStripes<String, Lock> lockMap = new WeakLazyStripes<>((key) -> new ReentrantLock());
        Object ar1;
        Object ar2;
        Object ar3;
        Object ar4;
        Object ar5;
        Object ar6;
        Object ar7;
        Object ar8;


        @Actor
        public void actor1() {
            ar1 = lockMap.get("key1");
        }

        @Actor
        public void actor2() {
            ar2 = lockMap.get("key2");
        }

        @Actor
        public void actor3() {
            ar3 = lockMap.get("key3");
        }

        @Actor
        public void actor4() {
            ar4 = lockMap.get("key4");
        }

        @Actor
        public void actor5() {
            ar5 = lockMap.get("key5");
        }

        @Actor
        public void actor6() {
            ar6 = lockMap.get("key6");
        }

        @Actor
        public void actor7() {
            ar7 = lockMap.get("key7");
        }

        @Actor
        public void actor8() {
            ar8 = lockMap.get("key8");
        }

        @Arbiter
        public void arbiter(Z_Result r) {
            r.r1 = ar1 != ar2 && ar2 != ar3 && ar3 != ar4 && ar4 != ar5 && ar5 != ar6 && ar6 != ar7 && ar7 != ar8;
        }
    }

}
