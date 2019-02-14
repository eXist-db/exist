/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2005-2011 The eXist-db Project
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
 *  $Id: Restore.java 15109 2011-08-09 13:03:09Z deliriumsky $
 */
package org.exist.backup.restore.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

/**
 * @author Adam Retter <adam@exist-db.org>
 */
public abstract class AbstractRestoreListener implements RestoreListener {

    private final List<Problem> problems = new ArrayList<>();
    private String currentCollectionName;
    private String currentResourceName;
    private List<Observable> observables;

    @Override
    public void restoreStarting() {
        info("Starting restore of backup...");
    }

    @Override
    public void restoreFinished() {
        info("Finished restore of backup.");
    }

    @Override
    public void createCollection(final String collection) {
        info("Creating collection " + collection);
    }

    @Override
    public void setCurrentBackup(final String currentBackup) {
        info("Processing backup: " + currentBackup);
    }

    @Override
    public void setCurrentCollection(final String currentCollectionName) {
        this.currentCollectionName = currentCollectionName;
    }

    @Override
    public void setCurrentResource(final String currentResourceName) {
        this.currentResourceName = currentResourceName;
    }

    @Override
    public void observe(final Observable observable) {

        if (observables == null) {
            observables = new ArrayList<>();
        }

        if (!observables.contains(observable)) {
            observables.add(observable);
        }
    }

    @Override
    public void restored(final String resource) {
        info("Restored " + resource);
    }

    @Override
    public void warn(final String message) {
        problems.add(new Warning(message));
    }

    @Override
    public void error(final String message) {
        problems.add(new Error(message));
    }

    @Override
    public boolean hasProblems() {
        return !problems.isEmpty();
    }

    @Override
    public String warningsAndErrorsAsString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("------------------------------------\n");
        builder.append("Problems occured found during restore:\n");
        for (final Problem problem : problems) {
            builder.append(problem.toString());
            builder.append(System.getProperty("line.separator"));
        }
        return builder.toString();
    }

    @Override
    public void setNumberOfFiles(long nr){
        // NOP
    }

    @Override
    public void incrementFileCounter(){
        // NOP
    }

    private abstract class Problem {
        private final String message;

        public Problem(final String message) {
            this.message = message;
        }

        protected String getMessage() {
            return message;
        }
    }

    private class Error extends Problem {
        public Error(final String message) {
            super(message);
        }

        @Override
        public String toString() {
            return "ERROR: " + getMessage();
        }
    }

    private class Warning extends Problem {
        public Warning(final String message) {
            super(message);
        }

        @Override
        public String toString() {
            return "WARN: " + getMessage();
        }
    }
}