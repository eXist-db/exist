# -*-Shell-script-*-
# Common eXist script functions and settings for getopt
# $Id$

CLIENT_OPTS="|-u|--user|-P|--password|-p|--parse|-C|--config|-r|--remove|-c|--collection|-f|--resource|-g|--get|-m|--mkcol|-R|--rmcol|-x|--xpath|-n|--howmany|-o|--option|-O|--output|-F|--file|-t|--threads|-X|--xupdate|-T|--trace|"

STANDALONESERVER_OPTS="|-p|--http-port|-t|--threads|"

JETTYCONTAINER_OPTS=""

JMX_OPTS="|-j|--jmx|"
JMX_SHORT="-j"
JMX_SHORT_EQUAL="-j="
JMX_LONG="--jmx"
JMX_LONG_EQUAL="--jmx="

NR_JAVA_OPTS=0
declare -a JAVA_OPTS

substring() {
  [ "${1#*$2*}" = "$1" ] && return 1
  return 0
}

is_integer() {
 [ $1 -eq 1 ] 2> /dev/null; 
 if [ $? -eq 2 ]; then
     echo "Port need to be an integer"
     exit 1
 else
     return 1;
 fi
}

is_jmx_switch() {
    if substring ${JMX_OPTS} "|$1|"; then
	JMX_ENABLED=1;
	[ "x$2" != "x" ] && ! substring "${2}" $"-" && JMX_PORT="$2";
	return 2;
    elif substring "|$1|" $JMX_SHORT_EQUAL; then
	JMX_ENABLED=1;
	JMX_PORT="${1#${JMX_SHORT_EQUAL}}";
	return 1;
    elif substring "|$1|" ${JMX_LONG_EQUAL}; then
	JMX_ENABLED=1;
	JMX_PORT="${1#${JMX_LONG_EQUAL}}";
	return 1;
    elif substring "|$1|" ${JMX_SHORT}; then
	JMX_ENABLED=1;
	JMX_PORT="${1#${JMX_SHORT}}";
	return 1;
    elif substring "|$1|" $${JMX_LONG}; then
	JMX_ENABLED=1;
	JMX_PORT="${1#${JMX_LONG}}";
	return 1;
    fi
    return 0;
}

get_opts() {
    ALL_OPTS="$1 --"
    ARG_OPTS=$2

    eval set -- "${ALL_OPTS}"
    while true; do
	if substring ${JMX_OPTS} "|$1|"; then
	    JMX_ENABLED=1;
	    [ "x$2" != "x" ] && ! substring "${2}" $"-" && JMX_PORT="$2" \
		&& is_integer ${JMX_PORT};
	    shift 2;
	elif substring "|$1|" ${JMX_SHORT_EQUAL}; then
	    JMX_ENABLED=1;
	    JMX_PORT="${1#${JMX_SHORT_EQUAL}}" && is_integer ${JMX_PORT};
	    shift;
	elif substring "|$1|" ${JMX_LONG_EQUAL}; then
	    JMX_ENABLED=1;
	    JMX_PORT="${1#${JMX_LONG_EQUAL}}" && is_integer ${JMX_PORT};
	    shift;
	elif substring "|$1|" ${JMX_SHORT}; then
	    JMX_ENABLED=1;
	    JMX_PORT="${1#${JMX_SHORT}}" && is_integer ${JMX_PORT};
	    shift; 
	elif substring "|$1|" ${JMX_LONG}; then
	    JMX_ENABLED=1;
	    JMX_PORT="${1#${JMX_LONG}}" && is_integer ${JMX_PORT};
	    shift;
	elif substring ${ARG_OPTS} "|$1|"; then
            JAVA_OPTS[${NR_JAVA_OPTS}]="$1 $2";
	    let "NR_JAVA_OPTS += 1";
	    shift 2 ;
	elif substring $"--" $1; then
	    break;
	else
            JAVA_OPTS[${NR_JAVA_OPTS}]="$1";
	    let "NR_JAVA_OPTS += 1";
	    shift;
	    
	fi
    done
    
    echo ${JAVA_OPTS[@]};
}
