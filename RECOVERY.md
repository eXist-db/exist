# RECOVERY
If an index got corrupted, this is how to recover:

- stop database
- in the "data" directory remove
  - all ".dbx" files EXCEPT dom.dbx, collections.dbx and symbols.dbx
  - the "lucene" and "range" directories
  - all ".log" transaction log files
  - all ".lck" lock files
- start database

After starting the database all indices will be recreated.

# COMPLETELY CLEAN DATABASE

**! WARNING: this will delete all your data !**

If it is required to have a completely new database (e.g. in case of restoring a database from a backup), this is how to do:

- stop database
- remove all files and directories from the "data" directory
- restore backup



