xquery version "1.0";

(:
    This XQuery can be registered with the scheduler to poll
    revision log entries from a given SVN repository. The log
    entries are stored into an XML document. Whenever new revisions
    are available, they are appended to the existing document.
    
    For an example configuration (to be added to conf.xml) see below:
    
    <job type="user" class="/db/svn/svntask.xql"
        cron-trigger="40 * * * * ?">
        <parameter name="bindingPrefix" value="svnu"/>
        <parameter name="collection" value="/db/svn"/>
        <parameter name="adminPasswd" value=""/>
        <parameter name="uri" value="https://exist.svn.sourceforge.net/svnroot/exist/trunk/eXist"/>
        <parameter name="startRevision" value="7233"/>
    </job>
:)

declare namespace svnu="http://exist-db.org/svnutil";

import module namespace svn="http://exist-db.org/xquery/svn"
at "java:org.exist.xquery.modules.svn.SVNModule";
import module namespace xdb="http://exist-db.org/xquery/xmldb";

declare variable $svnu:collection external;
declare variable $svnu:adminPasswd external;
declare variable $svnu:uri external;
declare variable $svnu:startRevision external;

declare variable $svnu:USER := "anonymous";
declare variable $svnu:PASS := "anonymous";

declare function svnu:update() {
    let $log := /log[@uri = $svnu:uri][@start = $svnu:startRevision]
    return
        util:catch("java.lang.Exception",
            if ($log) then
                let $lastRev := max(for $rev in $log/entry/@rev return xs:integer($rev))
                let $updated := svn:log(xs:anyURI($svnu:uri), $svnu:USER, $svnu:PASS, $lastRev, ())
                let $entries := $updated/entry[@rev != $lastRev]
                return
                    if (count($entries) gt 0) then
                        update insert $entries into $log
                    else
                        ()
            else
                let $all := 
                    svn:log(xs:anyURI($svnu:uri), $svnu:USER, $svnu:PASS, 
                        xs:integer($svnu:startRevision), -1)
                let $l := util:log("DEBUG", $all)
                return
                    xdb:store($svnu:collection, (), $all),
            ()
        )
};

xdb:login($svnu:collection, "admin", $svnu:adminPasswd),
svnu:update()
