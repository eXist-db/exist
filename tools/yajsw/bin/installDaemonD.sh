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
EXECUTABLE=installDaemonNoPrivD.sh

# set java and conf file
source "$PRGDIR"/setenv.sh
export PRGDIR

# Check that target executable exists
if [ ! -x "$PRGDIR"/"$EXECUTABLE" ]; then
  echo "Cannot find $PRGDIR/$EXECUTABLE"
  echo "This file is needed to run this program"
  exit 1
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
    systemd_service="${systemd_sys_dir}/${w_name}.service"
    echo "Installing template ${wrapper_home}/templates/systemd.vm as non-privileged service ${systemd_service}";
    eval "echo -e \"`<${wrapper_home}/templates/systemd.vm`\"" | sudo tee "${systemd_service}" > /dev/null
    sudo chmod 664 "${systemd_service}"
    echo -e "\nEnabling the service...\n";
    sudo systemctl daemon-reload
    sudo systemctl enable $w_name;
    echo -e "\nYou can now start it with: \n===============================";
    echo -e "sudo systemctl start ${w_name}\n===============================\n";
}

function systemd_user {
    if [ -z "${RUN_AS_USER}" ]; then
        echo -e "\nNo RUN_AS_USER environment variable found!"
        read -p "Which user should ${w_name} run as for the systemd service (${USER})? " eval_response;
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

function use_systemd {
    echo "Using systemd with template non-privileged"

    systemd_user;

    echo -e "\nPlease note that the environment variables RUN_AS_USER are used for the systemd service setup."
    echo -e "Please review them below and if they are not set correctly, please exit and set them in your environment before rerunning this script.\n\n"

    echo -e "RUN_AS_USER=${RUN_AS_USER}\n"

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
}

if [ `pgrep -P 1 systemd | head -n 1` ]; then
    echo -e "Detected systemd running.\n";
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
    sudo "$PRGDIR"/"$EXECUTABLE"
fi

