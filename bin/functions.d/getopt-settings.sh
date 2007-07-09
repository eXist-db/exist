# -*-Shell-script-*-
# Common eXist script functions and settings for getopt
# $Id$

SHORT_CLIENT_OPTS="h,j::,l,C:,u:,P:,p:,o:,r:,c:,f:,g:,m:,R:,n:,O:,F:,t:,X:,T:,v,q,d,s,i,Q,N,x::"
LONG_CLIENT_OPTS="help,port:,jmx::,local,config:,user:,password:,parse:,option:,remove:,collection:,resource:,get:,mkcol:,rmcol:,howmany:,output:,file:,threads:,xupdate:,trace:,verbose,quiet,recurse-dirs,no-gui,reindex,query,no-embedded-mode,xpath::"

SHORT_STANDALONESERVER_OPTS="h,j::,d,p:,t:"
LONG_STANDALONESERVER_OPTS="help,jmx::,debug,http-port:,threads:"

SHORT_JETTYCONTAINER_OPTS="j::"
LONG_JETTYCONTAINER_OPTS="jmx::"

NR_JAVA_OPTS=0
declare -a JAVA_OPTS

get_client_getopts() {
    if `getopt -T >/dev/null 2>&1` ; [ $? = 4 ] ; then
	ALL_OPTS=`getopt -a -o ${SHORT_CLIENT_OPTS} --long ${LONG_CLIENT_OPTS} \
	    -n 'client.sh' -- "$@"`
    else
	ALL_OPTS=`getopt ${SHORT_CLIENT_OPTS} $*`
    fi
# fixme! option -p|--parse takes 1..n arguments!

    eval set -- "$ALL_OPTS"
    while true ; do
	case "$1" in
            -j|--jmx) JMX_ENABLED=1; [ "x$2" != "x" ] && JMX_PORT="$2"; shift ;;
            -u|--user|-P|--password|-p|--parse|-C|--config|-r|--remove|-c|--collection|-f|--resource|-g|--get|-m|--mkcol|-R|--rmcol|-x|--xpath|-n|--howmany|-o|--option|-O|--output|-F|--file|-t|--threads|-X|--xupdate|-T|--trace) JAVA_OPTS[$NR_JAVA_OPTS]="$1 $2"; let "NR_JAVA_OPTS += 1"; shift 2 ;;
            --) shift ; break ;;
            *) JAVA_OPTS[$NR_JAVA_OPTS]="$1"; let "NR_JAVA_OPTS += 1"; shift ;;
	esac
    done

# Collect the remaining arguments
    for arg; do
	JAVA_OPTS[$NR_JAVA_OPTS]="$arg";
	let "NR_JAVA_OPTS += 1";
    done
    
    echo ${JAVA_OPTS[@]};
}

get_standaloneserver_getopts() {
if `getopt -T >/dev/null 2>&1` ; [ $? = 4 ] ; then
    NON_JAVA_OPTS=`getopt -a -o ${SHORT_STANDALONESERVER_OPTS} --long ${LONG_STANDALONESERVER_OPTS} \
	-n 'server.sh' -- "$@"`
else
    ALL_OPTS=`getopt ${SHORT_STANDALONESERVER_OPTS} $*`
fi

eval set -- "${ALL_OPTS}"
while true ; do
    case "$1" in
        -j|--jmx) JMX_ENABLED=1; [ "x$2" != "x" ] && JMX_PORT="$2"; shift ;;
        -p|--http-port|-t|--threads) JAVA_OPTS[$NR_JAVA_OPTS]="$1 $2"; let "NR_JAVA_OPTS += 1"; shift 2 ;;
        --) shift ; break ;;
        *) JAVA_OPTS[$NR_JAVA_OPTS]="$1"; let "NR_JAVA_OPTS += 1"; shift ;;
    esac
done
# Collect the remaining arguments
for arg; do
    JAVA_OPTS[$NR_JAVA_OPTS]="$arg";
    let "NR_JAVA_OPTS += 1";
done
}

get_jettycontainer_getopts() {
if `getopt -T >/dev/null 2>&1` ; [ $? = 4 ] ; then
    NON_JAVA_OPTS=`getopt -a -o ${SHORT_JETTYCONTAINER_OPTS} --long ${LONG_JETTYCONTAINER_OPTS} \
	-n 'startup.sh' -- "$@"`
else
    NON_JAVA_OPTS=`getopt ${SHORT_JETTYCONTAINER_OPTS} $*`
fi
eval set -- "$NON_JAVA_OPTS"
while true ; do
    case "$1" in
        -j|--jmx) JMX_ENABLED=1; [ "x$2" != "x" ] && JMX_PORT="$2"; shift ;;
        --) shift ; break ;;
        *) JAVA_OPTS[$NR_JAVA_OPTS]="$1"; let "NR_JAVA_OPTS += 1"; shift ;;
    esac
done
# Collect the remaining arguments
for arg; do
    JAVA_OPTS[$NR_JAVA_OPTS]="$arg";
    let "NR_JAVA_OPTS += 1";
done
}
