# betterFORM eXist-db Integration

Since eXist-db 5.0.0, the full betterFORM XForms package is no longer enabled by default, instead just the `ResourceServlet` is present (as this is needed by eXide and a bunch of other apps).


## Install Instructions

By default only the betterForm ResourceServlet is installed. If you want the full betterForm XForms experience, then:

1. Stop eXist-db (if it is running).

2. Set the property `include.feature.betterform` in your `$EXIST_HOME/extensions/local.build.properties` file. See `$EXIST_HOME/extensions/build.properties` for details.

3. `cd $EXIST_HOME/extensions/betterform`.

4. Run the following: `../../build.sh install` (or you can use `..\..\build.bat install` if you are on Windows).

5. Start eXist-db.


## Uninstall Instructions

1. Stop eXist-db (if it is running).

2. `cd $EXIST_HOME/extensions/betterform`.

3. Run the following: `../../build.sh clean` (or you can use `..\..\build.bat clean` if you are on Windows).

4. Start eXist-db.
