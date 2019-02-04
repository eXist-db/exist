xquery version "1.0";

import module namespace sql="http://exist-db.org/xquery/sql";
import module namespace oracle="http://exist-db.org/xquery/oracle";

(:
    This XQuery code demonstrates how to retrieve an XML result set from a function or stored procedure in an Oracle database
    using the oracle:execute() function. This function is designed specifically for situations where an Oracle cursor is being
    returned at one of the parameter positions. The cursor position must be given in the 4th input parameter to the oracle:execute()
    function. 
    
    Input parameters are passed to the stored procedure using <oracle:parameters/> with the position, type and value of
    each input parameter specified in the individual <oracle:param/> elements.
    
    The function allows for an optional return code at the begining of the stored procedure (i.e. { ? = call...})
    which is assumed to indicate whether or not the function completed successfully*. If this is required then the expected success
    code integer value should be passed to the oracle:execute() function at the 6th position. If any other value is returned by the
    PL/SQL the execution is considered to have failed and details of the return code given will be entered in
    the eXist log.
    
    At present, the oracle:execute() function only works for one output cursor. Future development could allow for multiple output
    cursors being returned in one XML result.
    
    * Please note that if you expect your result set as the PL/SQL function return value then you need to use the sql:execute() extension
    module function. The oracle:execute is designed specifically for PL/SQL functions and procedures which return an Oracle cursor.
:)

let $connection := sql:get-connection("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@//127.0.0.1:1521/mydatabase", "username", "password"),
$stored-procedure := "{ ? = call my_schema.my_package.my_stored_procedure(?, ?, ?) }",
$oracleCursorPos := 4,
$parameters :=
     <oracle:parameters>
        <oracle:param oracle:pos="2" oracle:type="integer">56100650</oracle:param>
        <oracle:param oracle:pos="3" oracle:type="string">RFC</oracle:param>
    </oracle:parameters> return
        oracle:execute($connection, $stored-procedure, $parameters, $oracleCursorPos, true(), 1)