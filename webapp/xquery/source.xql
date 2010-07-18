xquery version "1.0";

declare option exist:serialize "method=xhtml media-type=text/html";

<html>
    <head>
        <title>eXist-db Open Source Native XML Database</title>
        <link href="resources/exist_icon_16x16.ico" rel="shortcut icon" />
        <link type="image/png" href="resources/exist_icon_16x16.png" rel="icon"/>
        <script type="text/javascript" src="scripts/syntax/shCore.js"></script>
        <script type="text/javascript" src="scripts/syntax/shBrushXQuery.js"></script>
        <script type="text/javascript" src="scripts/syntax/shBrushXml.js"></script>
        <link type="text/css" rel="stylesheet"
            href="styles/syntax/shCore.css"/>
        <link type="text/css" rel="Stylesheet"
            href="styles/syntax/shThemeDefault.css" id="theme"/>
        <script type="text/javascript">
            SyntaxHighlighter.config.stripBrs = true;
            SyntaxHighlighter.defaults[ 'auto-links'] = false;
            SyntaxHighlighter.defaults[ 'wrap-lines'] = false;
            SyntaxHighlighter.all();
        </script>
    </head>
    <body bgcolor="#FFFFFF">
        <h1>{request:get-attribute("resource")}</h1>
        <pre class="brush: {request:get-parameter('mode', 'xquery')};">{ request:get-data() }</pre>
    </body>
</html>