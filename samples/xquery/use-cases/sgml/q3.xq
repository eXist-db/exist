<result>
  {
    for $c in doc("report.xml")//chapter
    where empty($c/intro)
    return $c/section/intro/para
  }
</result>
