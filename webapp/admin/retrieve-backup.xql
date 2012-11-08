xquery version "1.0";

import module namespace backup="http://exist-db.org/xquery/admin-interface/backup" at "backup.xqm";

import module namespace backups="http://exist-db.org/xquery/backups"
at "java:org.exist.backup.xquery.BackupModule";

declare function local:download() {
    let $backupDir := backup:get-directory()
    let $archive := request:get-parameter("archive", ())
    return
        if ($archive) then
            backups:retrieve($backupDir, $archive)
        else
            ()
};

local:download()