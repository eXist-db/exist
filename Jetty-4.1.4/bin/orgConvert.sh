
#!/bin/sh

if [ $# -lt 1 ]
then
    echo Usage - $0 file ...
    echo         Converts com.mortbay packages to org.mortbay
    exit 1
fi

perl -pi \
    -e 's/jetty\.mortbay\.com/jetty\.mortbay\.org/g ;' \
    -e 's/com\.mortbay\.Util([^A-Za-z])/org\.mortbay\.util\1/g ;' \
    -e 's/com\.mortbay\.HTTP\.Handler([^A-Za-z])/org\.mortbay\.http\.handler\1/g ;' \
    -e 's/com\.mortbay\.HTTP\.NBIO([^A-Za-z])/org\.mortbay\.http\.nbio\1/g ;' \
    -e 's/com\.mortbay\.HTTP\.SASL([^A-Za-z])/org\.mortbay\.http\.sasl\1/g ;' \
    -e 's/com\.mortbay\.HTTP([^A-Za-z])/org\.mortbay\.http\1/g ;' \
    -e 's/com\.mortbay\.XML([^A-Za-z])/org\.mortbay\.xml\1/g ;' \
    -e 's/com\.mortbay\.Jetty\.Servlet([^A-Za-z])/org\.mortbay\.jetty\.servlet\1/g ;' \
    -e 's/com\.mortbay\.Jetty\.Win32([^A-Za-z])/org\.mortbay\.jetty\.win32\1/g ;' \
    -e 's/com\.mortbay\.Jetty\.JMX([^A-Za-z])/org\.mortbay\.jetty\.jmx\1/g ;' \
    -e 's/com\.mortbay\.Jetty([^A-Za-z])/org\.mortbay\.jetty\1/g ;' \
    -e 's/com\.mortbay\.HTML([^A-Za-z])/org\.mortbay\.html\1/g ;' \
    -e 's/com\.mortbay\.Servlets\.PackageIndex([^A-Za-z])/org\.mortbay\.servlets\.packageindex\1/g ;' \
    -e 's/com\.mortbay\.Servlets([^A-Za-z])/org\.mortbay\.servlets\1/g ;' \
    -e 's/com\.mortbay\.Servlet([^A-Za-z])/org\.mortbay\.servlet\1/g ;' \
    -e 's/com\.mortbay\.FTP([^A-Za-z])/org\.mortbay\.ftp\1/g ;' \
    -e 's/com\.mortbay\.Tools\.Converter([^A-Za-z])/org\.mortbay\.tools\.converter\1/g ;' \
    -e 's/com\.mortbay\.Tools\.Servlet([^A-Za-z])/org\.mortbay\.tools\.servlet\1/g ;' \
    -e 's/com\.mortbay\.Tools\.DataClassTest([^A-Za-z])/org\.mortbay\.tools\.dataclasstest\1/g ;' \
    -e 's/com\.mortbay\.Tools([^A-Za-z])/org\.mortbay\.tools\1/g ;' \
    -e 's/com\.mortbay([^A-Za-z])/org\.mortbay\1/g ;' \
    -e 's/com\/mortbay\/Util([^A-Za-z])/org\/mortbay\/util\1/g ;' \
    -e 's/com\/mortbay\/HTTP\/Handler([^A-Za-z])/org\/mortbay\/http\/handler\1/g ;' \
    -e 's/com\/mortbay\/HTTP\/NBIO([^A-Za-z])/org\/mortbay\/http\/nbio\1/g ;' \
    -e 's/com\/mortbay\/HTTP\/SASL([^A-Za-z])/org\/mortbay\/http\/sasl\1/g ;' \
    -e 's/com\/mortbay\/HTTP([^A-Za-z])/org\/mortbay\/http\1/g ;' \
    -e 's/com\/mortbay\/XML([^A-Za-z])/org\/mortbay\/xml\1/g ;' \
    -e 's/com\/mortbay\/Jetty\/Servlet([^A-Za-z])/org\/mortbay\/jetty\/servlet\1/g ;' \
    -e 's/com\/mortbay\/Jetty\/Win32([^A-Za-z])/org\/mortbay\/jetty\/win32\1/g ;' \
    -e 's/com\/mortbay\/Jetty\/JMX([^A-Za-z])/org\/mortbay\/jetty\/jmx\1/g ;' \
    -e 's/com\/mortbay\/Jetty([^A-Za-z])/org\/mortbay\/jetty\1/g ;' \
    -e 's/com\/mortbay\/HTML([^A-Za-z])/org\/mortbay\/html\1/g ;' \
    -e 's/com\/mortbay\/Servlet([^A-Za-z])/org\/mortbay\/servlet\1/g ;' \
    -e 's/com\/mortbay\/FTP([^A-Za-z])/org\/mortbay\/ftp\1/g ;' \
    -e 's/com\/mortbay\/Tools\/Converter([^A-Za-z])/org\/mortbay\/tools\/converter\1/g ;' \
    -e 's/com\/mortbay\/Tools\/Servlet([^A-Za-z])/org\/mortbay\/tools\/servlet\1/g ;' \
    -e 's/com\/mortbay\/Tools\/DataClassTest([^A-Za-z])/org\/mortbay\/tools\/dataclasstest\1/g ;' \
    -e 's/com\/mortbay\/Tools([^A-Za-z])/org\/mortbay\/tools\1/g ;' \
    -e 's/com\(.\)mortbay([^A-Za-z])/org\1mortbay\2/g ;' \
    -e 's/mortbay\(.\)([^A-Za-z])/mortbay\1org\2/g ;' \
    $*
