#!/bin/bash
sudo cp service-ubuntu.sh /etc/init.d/gitblit
sudo update-rc.d gitblit defaults
