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
package org.exist.xquery;

import org.exist.security.Subject;

/**
 * Base class for XQuery functions which switch the current user
 *
 * Provides the function {@link #switchUser(Subject)} to allow us
 * to switch the current broker to a user and then have it switched
 * back when the XQuery expression is reset
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public abstract class UserSwitchingBasicFunction extends BasicFunction {

    /**
     * Flag which indicates how many subjects we have pushed, and so we must later
     * pop the same number of subjects when the expression is reset, see {@link UserSwitchingBasicFunction#resetState(boolean)}
     */
    private int pushedSubjects = 0;

    public UserSwitchingBasicFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    /**
     * Switches the current broker to the provided user
     *
     * @param user the user to switch to
     */
    protected void switchUser(final Subject user) {
        context.getBroker().pushSubject(user);
        pushedSubjects++;
    }

    /**
     * Takes care to switch the broker back from the switched
     * user before calling @{link super#resetState(boolean)}
     */
    @Override
    public void resetState(final boolean postOptimization) {
        //if we pushed a subject, we must pop it
        while (pushedSubjects > 0) {
            context.getBroker().popSubject();
            pushedSubjects--;
        }

        super.resetState(postOptimization);
    }
}
