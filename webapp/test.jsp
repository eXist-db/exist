<%@ taglib uri="http://exist-db.org/exist" prefix="e" %>

<html>
    <head>
        <title>JSP Test</title>
    </head>
    <body>
        <e:collection var="c" uri="xmldb:exist:///db"/>
        <p>Collection: <%= c.getName() %>.</p>
    </body>
</html>
