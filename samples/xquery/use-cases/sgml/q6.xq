<result>
  {
    for $s in doc("report.xml")//section/@shorttitle
    return <stitle>{ $s }</stitle>
  }
</result>
