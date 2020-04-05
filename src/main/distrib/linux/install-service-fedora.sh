#!/bin/bash -x
# This script installs Gitblit on a system running under systemd.
# The script assumes the server is running as user giblit

# First create a file with the default settings
cat > /tmp/gitblit.defaults << EOF
GITBLIT_PATH=/opt/gitblit
GITBLIT_BASE_FOLDER=/opt/gitblit/data
GITBLIT_HTTP_PORT=0
GITBLIT_HTTPS_PORT=8443
EOF
# Create a systemd service file
cat > /tmp/gitblit.service << EOF
[Unit]
Description=Gitblit managing, viewing, and serving Git repositories.
After=network.target

[Service]
User=gitblit
Group=gitblit
Environment="ARGS=-server -Xmx1024M -Djava.awt.headless=true -cp"
EnvironmentFile=-/etc/sysconfig/gitblit
WorkingDirectory=/opt/gitblit
ExecStart=/usr/bin/java \$ARGS gitblit.jar:ext/* com.gitblit.GitBlitServer --httpsPort \$GITBLIT_HTTPS_PORT --httpPort \$GITBLIT_HTTP_PORT --baseFolder \$GITBLIT_BASE_FOLDER --dailyLogFile
ExecStop=/usr/bin/java \$ARGS gitblit.jar:ext/* com.gitblit.GitBlitServer --baseFolder \$GITBLIT_BASE_FOLDER --stop

[Install]
WantedBy=multi-user.target
EOF

# Finally copy the files to the destination and register the systemd unit.
sudo sh -c "cp /tmp/gitblit.defaults /etc/sysconfig/gitblit && cp /tmp/gitblit.service /etc/systemd/system/"
sudo sh -c "systemctl daemon-reload && systemctl enable gitblit.service && systemctl start gitblit.service"
