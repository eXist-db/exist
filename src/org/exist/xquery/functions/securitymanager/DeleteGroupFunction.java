package org.exist.xquery.functions.securitymanager;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;

/**
 *
 * @author aretter
 */
public class DeleteGroupFunction extends BasicFunction {

    public DeleteGroupFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("delete-group", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX),
            "Deletes an existing group identified by $group-id. Any resources owned by the group will be moved to the 'guest' group.",
            new SequenceType[]{
                new FunctionParameterSequenceType("group-id", Type.STRING, Cardinality.EXACTLY_ONE, "The group-id to delete")
            },
            new SequenceType(Type.ITEM, Cardinality.EMPTY)
        ),

        new FunctionSignature(
            new QName("delete-group", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX),
            "Deletes an existing group identified by $group-id, any resources owned by the group will be moved to the group indicated by $successor-group-id.",
            new SequenceType[]{
                new FunctionParameterSequenceType("group-id", Type.STRING, Cardinality.EXACTLY_ONE, "The group-id to delete"),
                new FunctionParameterSequenceType("successor-group-id", Type.STRING, Cardinality.EXACTLY_ONE, "The group-id that should take over ownership of any resources")
            },
            new SequenceType(Type.ITEM, Cardinality.EMPTY)
        )
    };


    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        SecurityManager sm = context.getBroker().getBrokerPool().getSecurityManager();
        Subject currentSubject = context.getBroker().getSubject();

        try {
            final Group group = sm.getGroup(args[0].itemAt(0).getStringValue());
            if(group.isManager(currentSubject)) {
                throw new PermissionDeniedException("Only a group manager may delete a group");
            }

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

            //TODO how to handle user which are members of this group
            //also how to reassign resources that are allocated to this group to another group

            try {
                sm.deleteGroup(group.getName());
            } catch(EXistException ee) {
                throw new XPathException(this, ee);
            }

            return Sequence.EMPTY_SEQUENCE;
        } catch(PermissionDeniedException pde) {
            throw new XPathException(this, pde);
        }
    }

}
