<result>
  {
    let $x := doc("report.xml")//xref[@xrefid = "top4"],
        $t := doc("report.xml")//title[. << $x]
    return $t[last()]
  }
</result>
