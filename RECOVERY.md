# RECOVERY
If an index got corrupted, this is how to recover:

- stop database
- in the "data" directory remove
  - all ".dbx" files EXCEPT blob.dbx, collections.dbx, dom.dbx and symbols.dbx
  - the "lucene" and "range" directories
  - all ".log" transaction log files
  - all ".lck" lock files
- start database

After starting the database the (secondary) indices must be reconstructed using the 
eXist-db xquery function `xmldb:reindex('/db)`. This can be executed e.g. the command-line via 

`./bin/client.sh --xpath "xmldb:reindex('/db')" --optionuri=xmldb:exist:///`

Please check`./bin/client.sh --help` for available command-line options, e.g. for 
setting username and password.

# COMPLETELY CLEAN DATABASE

**! WARNING: this will delete all your data !**

If it is required to have a completely new database (e.g. in case of restoring a database 
from a backup), this is how to do:

- stop database
- remove all files and directories from the "data" directory
- restore backup

# RESTORE BACKUP
A backup file (or directory) can be most efficiently restored via the command-line:

`./bin/backup.sh --restore full20210925-1600.zip --dba-password <backup-password> --rebuild --optionuri=xmldb:exist:///`

Note that for this command the database should not run as a server.

Please check`./bin/backup.sh --help` for available command-line options.
