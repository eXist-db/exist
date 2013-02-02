/*
 *  eXist-db SecurityManager Module Extension
 *  Copyright (C) 2013 Adam Retter <adam@existsolutions.com>
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.xquery.functions.securitymanager;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class DeleteGroupFunction extends BasicFunction {

    public DeleteGroupFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    
    public final static FunctionSignature FNS_DELETE_GROUP = new FunctionSignature(
        new QName("delete-group", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX),
        "Deletes an existing group identified by $group-id. Any resources owned by the group will be moved to the 'guest' group.",
        new SequenceType[]{
            new FunctionParameterSequenceType("group-id", Type.STRING, Cardinality.EXACTLY_ONE, "The group-id to delete")
        },
        new SequenceType(Type.ITEM, Cardinality.EMPTY)
    );

    //TODO implement later
    /* public final static FunctionSignature FNS_DELETE_GROUP_WITH_SUCCESSOR = new FunctionSignature(
        new QName("delete-group", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX),
        "Deletes an existing group identified by $group-id, any resources owned by the group will be moved to the group indicated by $successor-group-id.",
        new SequenceType[]{
            new FunctionParameterSequenceType("group-id", Type.STRING, Cardinality.EXACTLY_ONE, "The group-id to delete"),
            new FunctionParameterSequenceType("successor-group-id", Type.STRING, Cardinality.EXACTLY_ONE, "The group-id that should take over ownership of any resources")
        },
        new SequenceType(Type.ITEM, Cardinality.EMPTY)
    ); */


    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        final SecurityManager sm = context.getBroker().getBrokerPool().getSecurityManager();
        final Subject currentSubject = context.getBroker().getSubject();

        try {
            final String name = args[0].itemAt(0).getStringValue();

            final Group successorGroup;
            if(getArgumentCount() == 2) {
                final String successorGroupName = args[1].itemAt(0).getStringValue();
                if(!currentSubject.hasGroup(successorGroupName)) {
                    throw new PermissionDeniedException("You must be a member of the group for which permissions should be inherited by");
                }
                successorGroup = sm.getGroup(successorGroupName);

            } else {
            	successorGroup = sm.getGroup("guest");
            }

            try {
                sm.deleteGroup(name);
            } catch(final EXistException ee) {
                throw new XPathException(this, ee);
            }

            return Sequence.EMPTY_SEQUENCE;
        } catch(final PermissionDeniedException pde) {
            throw new XPathException(this, pde);
        }
    }
}