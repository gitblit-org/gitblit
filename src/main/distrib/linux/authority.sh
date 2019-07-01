#!/bin/bash
java -javaagent:gitblit.jar -splash:splash.png -cp gitblit.jar com.gitblit.authority.Launcher --baseFolder data
