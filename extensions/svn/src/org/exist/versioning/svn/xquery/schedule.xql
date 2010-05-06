xquery version "1.0";
(: $Id$ :)

import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace scheduler="http://exist-db.org/xquery/scheduler"
at "java:org.exist.xquery.modules.scheduler.SchedulerModule";

scheduler:schedule-xquery-cron-job("/db/svn/svntask.xql", "* */5 * * * ?", "svn-stable-1.2",
    <parameters>
        <param name="bindingPrefix" value="svnu"/>
        <param name="collection" value="/db/svn"/>
        <param name="adminPasswd" value=""/>
        <param name="uri" value="https://exist.svn.sourceforge.net/svnroot/exist/branches/eXist-stable-1.2"/>
        <param name="startRevision" value="8072"/>
    </parameters>
),
scheduler:schedule-xquery-cron-job("/db/svn/svntask.xql", "* */5 * * * ?", "svn-trunk",
    <parameters>
        <param name="bindingPrefix" value="svnu"/>
        <param name="collection" value="/db/svn"/>
        <param name="adminPasswd" value=""/>
        <param name="uri" value="https://exist.svn.sourceforge.net/svnroot/exist/trunk/eXist"/>
        <param name="startRevision" value="8072"/>
    </parameters>
)
