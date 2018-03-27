#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# install YAJSW daemon script
#
# -----------------------------------------------------------------------------

set -e

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done
 
PRGDIR=`dirname "$PRG"`
EXECUTABLE=wrapper.sh

# set java and conf file
source "$PRGDIR"/setenv.sh
export PRGDIR

# Check that target executable exists
if [ ! -x "$PRGDIR"/"$EXECUTABLE" ]; then
  echo "Cannot find $PRGDIR/$EXECUTABLE"
  echo "This file is needed to run this program"
  exit 1
fi

if [ `pgrep -P 1 systemd | head -n 1` ]; then
    echo "You can also use the systemd template for privileged use"
    echo -e "$(eval "echo -e \"`<$PRGDIR/../templates/systemd.vm`\"")";
fi

exec "$PRGDIR"/"$EXECUTABLE" -i "$conf_file"
 
