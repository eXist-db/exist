# RECOVERY
For full documentation on backup, restore, consistency checks, and emergency
export procedures, see <https://exist-db.org/exist/apps/doc/backup>.

## Create (emergency) backup
With the "Emergency Export tool" (see <https://exist-db.org/exist/apps/doc/backup#emergency-export-tool>) 
it is possible to create a 'regular' backup of the database, but it can also handle many situations with 
a database corruption. During the export process the database should not be running.

    ```sh
    ./bin/export.sh --direct --check-docs
    ```

By default a ZIP file is written into the 'export' directory. Please check `./bin/export.sh --help` 
for available command-line options.

## Recovering from a corrupted index
To recover from a corrupted index, perform the following steps:

1. Stop the running eXist database instance
2. Change into directory `$EXIST_HOME/data` or another directory you specified
   as data directory in the configuration (`$EXIST_HOME/etc/conf.xml`). 
3. Delete
   the following resources:
   - All ".dbx" files EXCEPT blob.dbx, collections.dbx, dom.dbx, and symbols.dbx
   - All ".lck" lock files
   - All ".log" transaction log files
   - The "lucene" and "range" directories
4. Trigger a reindex in order to reconstruct the secondary indexes, via the
   eXist-db XQuery function `xmldb:reindex("/db")`. This can be executed directly
   via the Java admin client, or, alternatively, on the command line via the
   following command:
   
    ```sh
    ./bin/client.sh --xpath "xmldb:reindex('/db')" --optionuri=xmldb:exist:///
    ```

Please check `./bin/client.sh --help` for available command-line options,
e.g., for supplying a username and password.

## Deleting all data in the database

> **! WARNING: This really will delete the data in the database !**

To completely wipe a database—for example, in order to restore a backup onto
a clean database—perform the following steps:

1. Stop the running eXist database instance
2. Delete all files and directories from the "data" directory
   
> Note that restoring from a backup (or parts of it) does not automatically
> delete all data in the database, nor does it necessarily require manually
> wiping the existing data using this procedure. If a restore is performed
> on a database with data, the restore process will upload the collections
> and documents contained in the backup. Collections and documents which exist
> in the database but are not part of the backup will not be modified.

## Restoring from a backup

To restore the database files from a backup, you can use either the Java Admin
Client or the backup command line utility. A backup file (or directory) can be
efficiently restored via the command-line:

```sh
./bin/backup.sh --restore full20210925-1600.zip --dba-password \
    <backup-password> --rebuild --optionuri=xmldb:exist:///
```

Note that for this command the database should not run as a server.

Please check `./bin/backup.sh --help` for available command-line options.