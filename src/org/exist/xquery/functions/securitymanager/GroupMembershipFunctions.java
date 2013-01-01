package org.exist.xquery.functions.securitymanager;

import java.util.List;
import org.exist.dom.QName;
import org.exist.security.Account;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
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
 * @author Adam Retter <adam@existsolutions.com>
 */
public class GroupMembershipFunctions extends BasicFunction {

    private final static QName qnGetGroupManagers = new QName("get-group-managers", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnGetGroupMembers = new QName("get-group-members", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);

    public final static FunctionSignature FNS_GET_GROUP_MANAGERS = new FunctionSignature(
        qnGetGroupManagers,
        "Gets a list of the group managers. Can only be called by a group manager.",
        new SequenceType[] {
            new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The group name to retrieve the list of managers for.")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "The list of group managers for the group $group")
    );
    
    public final static FunctionSignature FNS_GET_GROUP_MEMBERS = new FunctionSignature(
        qnGetGroupMembers,
        "Gets a list of the group members.",
        new SequenceType[] {
            new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The group name to retrieve the list of members for.")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "The list of group members for the group $group")
    );

    public GroupMembershipFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }


    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        Sequence result = Sequence.EMPTY_SEQUENCE;

        final String groupName = args[0].itemAt(0).getStringValue();

        final SecurityManager manager = context.getBroker().getBrokerPool().getSecurityManager();

        try {

            if(isCalledAs(qnGetGroupManagers.getLocalName())) {
                final Group group = manager.getGroup(groupName);
                final ValueSequence seq = new ValueSequence();
                for(final Account groupManager : group.getManagers()) {
                    seq.add(new StringValue(groupManager.getName()));
                }
                result = seq;
            } else if(isCalledAs(qnGetGroupMembers.getLocalName())) {

                final List<String> groupMembers = manager.findAllGroupMembers(groupName);

                final ValueSequence seq = new ValueSequence();
                for(final String groupMember : groupMembers) {
                    seq.add(new StringValue(groupMember));
                }
                result = seq;
            }
        } catch(final PermissionDeniedException pde) {
            throw new XPathException(this, pde);
        }

        return result;
    }
}