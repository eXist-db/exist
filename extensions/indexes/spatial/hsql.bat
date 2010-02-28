set EXIST_HOME=..\..\..
java -Xmx200m -cp .\lib\hsqldb-1.8.1.2.jar org.hsqldb.util.DatabaseManagerSwing --url jdbc:hsqldb:%EXIST_HOME%\webapp\WEB-INF\data\spatial_index 
