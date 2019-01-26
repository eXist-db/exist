#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# install YAJSW daemon script
#

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
EXECUTABLE=installDaemonNoPriv.sh

# set java and conf file
source "$PRGDIR"/setenv.sh
export PRGDIR

# Check that target executable exists
if [ ! -x "$PRGDIR"/"$EXECUTABLE" ]; then
  echo "Cannot find $PRGDIR/$EXECUTABLE"
  echo "This file is needed to run this program"
  exit 1
fi

# Locate JAVA_HOME (if not set in env)
if [ -z "${JAVA_HOME}" ]; then
  echo -e "\nNo JAVA_HOME environment variable found!"
  echo "Attempting to determine JAVA_HOME (if this fails you must manually set it)..."
  if [ "$(uname -s)" == "Darwin" ]; then
      java_bin=$(readlink `which java`)
  else
      java_bin=$(readlink -f `which java`)
  fi
  java_bin_dir=$(dirname "${java_bin}")
  JAVA_HOME=$(dirname "${java_bin_dir}")
  echo -e "Found JAVA_HOME=${JAVA_HOME}\n"
fi

# Set EXIST_HOME (if not set in env)
if [ -z "${EXIST_HOME}" ]; then
  echo -e "\nNo EXIST_HOME environment variable found!"
  echo "Attempting to derive EXIST_HOME (if this fails you must manually set it)..."
  exist_tools_dir=$(dirname "${wrapper_home}")
  EXIST_HOME=$(dirname "${exist_tools_dir}")
  echo -e "Derived EXIST_HOME=${EXIST_HOME}\n"
fi

function review_systemd_config {
    read -p "Would you like to review the systemd config (Y/n)? " eval_response;
    case $eval_response in
        [Yy])
            echo -e "$(eval "echo -e \"`<${wrapper_home}/templates/systemd.vm`\"")";

            read -p "Continue (Y/n)? " eval_response;
            case $eval_response in
                [Yy])
                    ;;
                *)
                    exit
                    ;;
            esac
            ;;
        *)
            ;;
    esac
}

function install_systemd_config {
    systemd_sys_dir="/etc/systemd/system"
    if [ ! -d "${systemd_sys_dir}" ]; then
        echo "Creating directory \"${systemd_sys_dir}\"";
        sudo mkdir -p "${systemd_sys_dir}";
    fi
    systemd_service="${systemd_sys_dir}/eXist-db.service"
    echo "Installing template ${wrapper_home}/templates/systemd.vm as non-privileged service ${systemd_service}";
    eval "echo -e \"`<${wrapper_home}/templates/systemd.vm`\"" | sudo tee "${systemd_service}" > /dev/null
    sudo chmod 664 "${systemd_service}"
    echo -e "\nEnabling the service...\n";
    sudo systemctl daemon-reload
    sudo systemctl enable eXist-db;
    echo -e "\nYou can now start it with: \n===============================";
    echo -e "sudo systemctl start eXist-db\n===============================\n";
}

function systemd_user {
    if [ -z "${RUN_AS_USER}" ]; then
        echo -e "\nNo RUN_AS_USER environment variable found!"
        read -p "Which user should eXist run as for the systemd service (${USER})? " eval_response;
        case $eval_response in
            "")
                RUN_AS_USER="${USER}"
                ;;
             *)
                RUN_AS_USER="${eval_response}"
                ;;
        esac
    fi
}

# systemd stuff 20160901/ljo
function use_systemd {
    echo "Using systemd with template non-privileged"

    systemd_user;

    echo -e "\nPlease note that the environment variables JAVA_HOME, EXIST_HOME, and RUN_AS_USER are used for the systemd service setup."
    echo -e "Please review them below and if they are not set correctly, please exit and set them in your environment before rerunning this script.\n\n"

    echo "JAVA_HOME=${JAVA_HOME}"
    echo "EXIST_HOME=${EXIST_HOME}"
    echo -e "RUN_AS_USER=${RUN_AS_USER}\n"
    if [ -z ${WRAPPER_UNATTENDED} ]; then
      read -p "Continue (Y/n)? " eval_response;
      case $eval_response in
          [Yy])
              review_systemd_config;
              install_systemd_config;
              ;;
          *)
              exit;
              ;;
      esac
    else
      install_systemd_config;
    fi
}

w_wrapper_pid_file="${wrapper_home}/work/wrapper.eXist-db.pid";
if [ ! -e "$w_wrapper_pid_file" ]; then
    mkdir "$w_wrapper_pid_file";
fi
export w_wrapper_pid_file;

if [ `pgrep -P 1 systemd | head -n 1` ]; then
    echo -e "Detected systemd running.\n";
    if [ -n ${WRAPPER_UNATTENDED} -a -z ${WRAPPER_USE_SYSTEMD} -a -z ${WRAPPER_USE_SYSTEMV} ]; then
      read -p "Do you want to use it (Y=Run service with non-privileged systemd/N=continue with privileged systemV-init)? " systemd_response;
      case $systemd_response in
      	[Yy][Ee][Ss]|[YyJj])
      	    use_systemd;
      	    ;;
      	[Nn][Oo]|[Nn])
      	    sudo "$PRGDIR"/"$EXECUTABLE"
      	    ;;
      esac
    else
      if [ -z ${WRAPPER_USE_SYSTEMD} ]; then
        sudo "$PRGDIR"/"$EXECUTABLE"
      else
        use_systemd;
      fi
    fi
else
    sudo "$PRGDIR"/"$EXECUTABLE"
fi
