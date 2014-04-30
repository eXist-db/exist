let $params :=
        <parameters>
            <param name="collection" value="/db"/>
            <param name="user" value="admin"/>
            <param name="password" value=""/>
            <param name="dir" value="{substring(current-date(), 1, 10)}.zip"/>
        </parameters>
return
    system:trigger-system-task("org.exist.storage.BackupSystemTask", $params)
