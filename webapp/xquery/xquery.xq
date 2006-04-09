xquery version "1.0";

import module namespace utils="http://exist-db.org/xquery/collection-utils"
at "collections.xqm";

(: Namespace for the local functions in this script :)
declare namespace f="http://exist-db.org/xquery/local-functions";

declare namespace session="http://exist-db.org/xquery/session";

(:  Retrieves the names of the current collection
    and all child-collections and returns them as a list of <option>
    elements
:)
declare function f:retrieve-collections()
as element()+
{ 
    for $c in 
        utils:list-collection-names(
            "xmldb:exist:///db", "guest", "guest"
        )
    return
        <option>{$c}</option>
};

(:  Read query examples into the select box 
    - see examples.xml in directory samples/ :)
declare function f:get-examples() as element()
{
    let $queries := /example-queries/query
    return
        if (empty($queries)) then
            <p><small>No examples found. Please store document samples/examples.xml
            into the database to get some examples. There's an XQuery script to install all 
            examples automatically. Just go to the 
            <a href="../admin/admin.xql?user=admin&amp;password=&amp;panel=setup">Examples 
            Setup</a> page.</small></p>
        else
            <form>
                <select onchange="forms['xquery'].query.value=this.value">
                    {for $q in $queries return
                        <option value="{$q/code}">{$q/description}</option>
                    }
                </select>
            </form>
};

(:  Retrieve previous queries from the query history. We
    store the history as an XQuery sequence in the session
    object.
:)
declare function f:get-query-history() as element()*
{
    let $history := session:get-attribute("history")
    for $query in $history return
        <option value="{$query}">{substring($query, 1, 70)}</option>
};

<document xmlns:xi="http://www.w3.org/2001/XInclude">
   	
    <header>
        <logo src="logo.jpg"/>
        <title>Open Source Native XML Database</title>
	    <author email="wolfgang@exist-db.org">Wolfgang M. Meier</author>
        <style href="styles/xquery.css"/>
    </header>    

    <!-- include sidebar -->
    <xi:include href="sidebar.xml"/>
  
    <body>

        <section title="Give it a try!">

        <p>Using the form below, arbitrary XQuery expressions can be send to the
        server. Results are shown in raw XML.</p>

        <p>This application is itself implemented as an XQuery script. For
        getting started, you may select one of the example queries in the
        select box below:</p>

        {f:get-examples()}

        <p></p>

        <form action="process.xq" method="post" name="xquery">
            <table class="query" border="0" bgcolor="#EEEEEE" 
                cellpadding="5" cellspacing="0">
                <tr>
                    <th colspan="2">XQuery Search Form</th>
                </tr>
                <tr>
                    <td colspan="2">
                        <span class="label">Context:</span><br/>
                        <!-- put all collection paths into a select element -->
                        <select name="collection" size="1">
                        { f:retrieve-collections() }
                        </select>
                    </td>
                </tr>
                <tr>
                    <td colspan="2">
                        <span class="label">XQuery:</span><br/>
                        <textarea name="query" cols="80" rows="15"/>
                    </td>
                </tr>
                <tr>
                    <td colspan="2">
                        <span class="label">Query History:</span><br/>
                        <select name="history" size="1"
                            onchange="this.form.query.value=this.value">
                            <option></option>
                            { f:get-query-history() }
                        </select>
                    </td>
                </tr>
                <tr>
                    <td align="left"><input type="submit"/></td>
                    <td align="right">
                        <span class="label">Hits per page:</span><br/>
                        <select name="howmany" size="1">
                            <option>10</option>
                            <option>20</option>
                            <option>50</option>
                        </select>
                    </td>
                </tr>
            </table>
        </form>

        </section>
    </body>
</document>
