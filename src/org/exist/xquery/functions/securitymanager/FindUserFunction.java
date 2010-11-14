/*
 *  eXist Image Module Extension
 *  Copyright (C) 2010 Adam Retter <adam@existsolutions.com>
 *  www.adamretter.co.uk
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

import java.util.Collections;
import java.util.List;
import org.exist.dom.QName;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;


/**
 *
 * @author aretter
 */
public class FindUserFunction extends BasicFunction {

    private final static QName qnFindUsersByUsername = new QName("find-users-by-username", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnFindUsersByName = new QName("find-users-by-name", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            qnFindUsersByUsername,
            "Finds users whoose username starts with a matching string",
            new SequenceType[] {
                new FunctionParameterSequenceType("starts-with", Type.STRING, Cardinality.EXACTLY_ONE, "The starting string against which to match usernames")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The list of matcing usernames")
        ),
        new FunctionSignature(
            qnFindUsersByName,
            "Finds users whoose personal name starts with a matching string",
            new SequenceType[] {
                new FunctionParameterSequenceType("starts-with", Type.STRING, Cardinality.EXACTLY_ONE, "The starting string against which to match a persomal name")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The list of matcing usernames")
        )
    };


    public FindUserFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        final String startsWith = args[0].getStringValue();
        DBBroker broker = getContext().getBroker();
        Subject currentUser = broker.getSubject();
        SecurityManager securityManager = broker.getBrokerPool().getSecurityManager();

        List<String> usernames;
        if(isCalledAs(qnFindUsersByUsername.getLocalName())) {
            usernames = securityManager.findUsernamesWhereUsernameStarts(currentUser, startsWith);
        } else if(isCalledAs(qnFindUsersByName.getLocalName())) {
            usernames = securityManager.findUsernamesWhereNameStarts(currentUser, startsWith);
        } else {
            throw new XPathException("Unknown functions");
        }

        //order a-z
        Collections.sort(usernames);

        Sequence result = new ValueSequence();
        for(String username : usernames) {
            result.add(new StringValue(username));
        }
        return result;
    }
}