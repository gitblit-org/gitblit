#!/bin/bash
# chkconfig: 3 21 91
# description: Starts and Stops gitblit
# Source function library.
. /etc/init.d/functions

# change theses values (default values)
GITBLIT_PATH=/opt/gitblit
GITBLIT_BASE_FOLDER=/opt/gitblit/data
GITBLIT_HTTP_PORT=0
GITBLIT_HTTPS_PORT=8443
GITBLIT_LOG=/var/log/gitblit.log
source ${GITBLIT_PATH}/java-proxy-config.sh
JAVA="java -server -Xmx1024M ${JAVA_PROXY_CONFIG} -Djava.awt.headless=true -cp"

RETVAL=0

case "$1" in
  start)
    if [ -f $GITBLIT_PATH/gitblit.jar ];
      then
      echo $"Starting gitblit server"
      cd $GITBLIT_PATH
      $JAVA "$GITBLIT_PATH/gitblit.jar:$GITBLIT_PATH/ext/*" com.gitblit.GitBlitServer --httpsPort $GITBLIT_HTTPS_PORT --httpPort $GITBLIT_HTTP_PORT --baseFolder $GITBLIT_BASE_FOLDER --dailyLogFile &
      echo "."
      exit $RETVAL
    fi
  ;;

  stop)
    if [ -f $GITBLIT_PATH/gitblit.jar ];
      then
      echo $"Stopping gitblit server"
      cd $GITBLIT_PATH
      $JAVA "$GITBLIT_PATH/gitblit.jar:$GITBLIT_PATH/ext/*" com.gitblit.GitBlitServer --baseFolder $GITBLIT_BASE_FOLDER --stop > /dev/null &
      echo "."
      exit $RETVAL
    fi
  ;;
  
  force-reload|restart)
      $0 stop
      sleep 5
      $0 start
  ;;

  *)
    echo $"Usage: /etc/init.d/gitblit {start|stop|restart|force-reload}"
    exit 1
  ;;
esac

exit $RETVAL
