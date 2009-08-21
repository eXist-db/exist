module namespace backup="http://exist-db.org/xquery/admin-interface/backup";

import module namespace backups="http://exist-db.org/xquery/backups"
at "java:org.exist.backup.xquery.BackupModule";
import module namespace date="http://exist-db.org/xquery/admin-interface/date" at "dates.xqm";
import module namespace system="http://exist-db.org/xquery/system";

declare variable $backup:BACKUP_DIR := "export";

declare function backup:main() as element() {
    let $action := lower-case(request:get-parameter("action", "refresh"))
    return
        <div class="panel">
            <h1>Backups</h1>
            {
                if ($action eq "trigger") then
                    backup:trigger()
                else
                    ()
            }
            { backup:display() }
        </div>
};

declare function backup:get-directory() {
    let $home := system:get-exist-home()
    return
        if (ends-with($home, "WEB-INF")) then
            concat($home, "/data/", $backup:BACKUP_DIR)
        else
            concat($home, "/webapp/WEB-INF/data/", $backup:BACKUP_DIR)
};
    
declare function backup:trigger() {
    let $incremental := request:get-parameter("inc", ())
    let $params :=
        <parameters>
            <param name="output" value="{$backup:BACKUP_DIR}"/>
            <param name="backup" value="yes"/>
            <param name="incremental" value="{if ($incremental) then 'yes' else 'no'}"/>
        </parameters>
    return (
        system:trigger-system-task("org.exist.storage.ConsistencyCheckTask", $params),
        <div class="process">
            A backup has been triggered on the server. It will be processed once all running
            transactions have returned.
        </div>
    )
};

declare function backup:display() {
    let $backupDir := backup:get-directory()
    return
        <form action="{session:encode-url(request:get-uri())}" method="GET">
            <div class="inner-panel">
                <h2>Available DB Backups</h2>
                { backup:list-backups($backupDir) }
                <button class="refresh" type="submit" name="action" value="refresh">Refresh</button>
            </div>
            
            <div class="inner-panel">
    		    <h2>Trigger Backup</h2>
    		    
    	        <span class="spacing">
    	            <button type="submit" name="action" value="trigger">Trigger</button>
                </span>
                <input type="checkbox" name="inc"/> Incremental
                <input type="hidden" name="panel" value="backup"/>
    	    </div>
	    </form>
};

declare function backup:list-backups($dir as xs:string) as element(table) {
    <table class="browse backups" cellspacing="0">
        <tr>
            <th>Name</th>
            <th>Created</th>
            <th>Incremental</th>
        </tr>
        {
            let $backups := backups:list($dir)/exist:backup
            return
                if (count($backups) eq 0) then
                    <tr>
                        <td colspan="4">No backup archives found.</td>
                    </tr>
                else
                    for $backup in $backups
                    let $date := xs:dateTime($backup/exist:date)
                    let $download := concat(session:encode-url(xs:anyURI("backups/")), $backup/@file/string())
                    order by $date descending
                    return
                        <tr>
                            <td>
                                <a href="{$download}">{$backup/@file/string()}</a>
                            </td>
                            <td>{date:format-dateTime($date)}</td>
                            <td class="last">{$backup/exist:incremental/text()}</td>
                        </tr>
        }
    </table>
};
