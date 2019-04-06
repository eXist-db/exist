package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import org.xmldb.api.base.XMLDBException;

import org.exist.security.Group;


/**
 * Created by IntelliJ IDEA. User: lcahlander Date: Aug 25, 2010 Time: 3:09:13 PM To change this template use File | Settings | File Templates.
 */
public class RemoveGroupTask extends UserTask
{
    private String name = null;

    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
    public void execute() throws BuildException
    {
        super.execute();

        if( name == null ) {
            throw( new BuildException( "You have to specify a name" ) );
        }

        log( "Removing group " + name, Project.MSG_INFO );

        try {
            final Group group = service.getGroup( name );

            if( group != null ) {
                service.removeGroup( group );
            } else {
                log( "Group " + name + " does not exist.", Project.MSG_INFO );
            }

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
