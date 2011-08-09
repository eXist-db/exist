/**
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist-db Project
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
 * \$Id\$
 */
package org.exist.backup.restore.listener;

import java.util.ArrayList;
import java.util.List;


public abstract class AbstractRestoreListener implements RestoreListener {

    private abstract class Problem {
        private final String message;
        public Problem(String message) {
           this.message = message;
        }

        protected String getMessage() {
            return message;
        }
    }

    private class Error extends Problem {
        public Error(String message) {
            super(message);
        }

        @Override
        public String toString() {
            return "ERROR: " + getMessage();
        }
    }

    private class Warning extends Problem {
        public Warning(String message) {
            super(message);
        }

        @Override
        public String toString() {
            return "WARN: " + getMessage();
        }
    }
    
    private final List<Problem> problems = new ArrayList<Problem>();

    @Override
    public void createCollection(String collection) {
        info("creating collection " + collection);
    }

    @Override
    public void restored(String resource) {
        info("restored " + resource);
    }

    @Override
    public void warn(String message) {
        problems.add(new Warning(message));
    }

    @Override
    public void error(String message) {
        problems.add(new Error(message));
    }

    @Override
    public boolean hasProblems() {
        return problems.size() > 0;
    }

    @Override
    public String warningsAndErrorsAsString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("------------------------------------\n");
        builder.append("Problems occured found during restore:\n");
        for(Problem problem : problems) {
            builder.append(problem.toString());
            builder.append(System.getProperty("line.separator"));
        }
        return builder.toString();
    }
}