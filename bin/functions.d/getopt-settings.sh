# -*-Shell-script-*-
# Common eXist script functions and settings for getopt
# $Id$

CLIENT_OPTS="|-u|--user|-P|--password|-p|--parse|-C|--config|-r|--remove|-c|--collection|-f|--resource|-g|--get|-m|--mkcol|-R|--rmcol|-x|--xpath|-n|--howmany|-o|--option|-O|--output|-F|--file|-t|--threads|-X|--xupdate|-T|--trace|"

STANDALONESERVER_OPTS="|-p|--http-port|-t|--threads|"

JETTYCONTAINER_OPTS=""

BACKUP_OPTS="|-u|--user|-p|--password|-P|--dba-password|-b|--backup|-d|--dir|-r|--restore|-o|--option|"

JMX_OPTS="|-j|--jmx|"
JMX_SHORT="-j"
JMX_SHORT_EQUAL="-j="
JMX_LONG="--jmx"
JMX_LONG_EQUAL="--jmx="

QUIET_OPTS="|-q|--quiet|"
QUIET_ENABLED=0

NR_JAVA_OPTS=0
declare -a JAVA_OPTS
declare -a CLIENT_PROPS

substring() {
    [ "${1#*$2*}" = "$1" ] && return 1
    return 0
}

is_integer() {
    [ $1 -eq 1 ] 2> /dev/null;
    if [ $? -eq 2 ]; then
	echo "Port need to be an integer" > /dev/stderr;
	exit 1
    fi
    return 0
}

is_jmx_switch() {
    if substring "${JMX_OPTS}" "|$1|"; then
	JMX_ENABLED=1;
	return 0;
    elif substring "|$1|" "$JMX_SHORT_EQUAL"; then
	JMX_ENABLED=1;
	JMX_PORT="${1#${JMX_SHORT_EQUAL}}" && is_integer "${JMX_PORT}";
	return 0;
    elif substring "|$1|" "${JMX_LONG_EQUAL}"; then
	JMX_ENABLED=1;
	JMX_PORT="${1#${JMX_LONG_EQUAL}}" && is_integer "${JMX_PORT}";
	return 0;
    elif substring "|$1|" "${JMX_SHORT}"; then
	JMX_ENABLED=1;
	JMX_PORT="${1#${JMX_SHORT}}" && is_integer "${JMX_PORT}";
	return 0;
    elif substring "|$1|" "${JMX_LONG}"; then
	JMX_ENABLED=1;
	JMX_PORT="${1#${JMX_LONG}}" && is_integer "${JMX_PORT}";
	return 0;
    fi
    return 1;
}

check_quiet_switch() {
    if substring "${QUIET_OPTS}" "|$1|"; then
	QUIET_ENABLED=1;
    fi
}

get_opts() {
    local -a ALL_OPTS=( "$@" )
    local found_jmx_opt
    
    for OPT in "${ALL_OPTS[@]}" ; do
	if [ -n "$found_jmx_opt" ] ; then
	    unset found_jmx_opt
	    local found_jmx_opt
	    if ! substring "${OPT}" $"-" && is_integer "${OPT}"; then
		JMX_PORT="$OPT"
		continue
	    fi
	fi
	if is_jmx_switch "$OPT"; then
	    found_jmx_opt=1
	else
	    check_quiet_switch "$OPT";
	    JAVA_OPTS[${NR_JAVA_OPTS}]="$OPT";
	    let "NR_JAVA_OPTS += 1";
	fi
    done
    
    if [ "${QUIET_ENABLED}" -eq 0 ]; then
	echo "${JAVA_OPTS[@]}";
    fi
}

get_client_props() {
    shopt -s extglob
    while IFS="=" read -r key value
    do
	case "${key}" in
	    \#* ) ;;
            * )
                #echo "Read client properties key: ${key}, value: ${value}"
		CLIENT_PROPS["${key}"]="${value}"
		;;
  esac
done < ${EXIST_HOME}/client.properties

}
