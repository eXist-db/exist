package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import org.xmldb.api.base.XMLDBException;

import org.exist.security.internal.aider.GroupAider;


/**
 * Created by IntelliJ IDEA. User: lcahlander Date: Aug 25, 2010 Time: 3:03:00 PM To change this template use File | Settings | File Templates.
 */
public class AddGroupTask extends UserTask
{
    private String name;

    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
    public void execute() throws BuildException
    {
        super.execute();

        if( name == null ) {
            throw( new BuildException( "Must specify a group name" ) );
        }

        try {
            final GroupAider group = new GroupAider( name );

            log( "Adding group " + name, Project.MSG_INFO );
            service.addGroup( group );

        }
        catch( final XMLDBException e ) {
            final String msg = "XMLDB exception caught: " + e.getMessage();

            if( failonerror ) {
                throw( new BuildException( msg, e ) );
            } else {
                log( msg, e, Project.MSG_ERR );
            }

        }
    }


    public void setName( String name )
    {
        this.name = name;
    }
}
