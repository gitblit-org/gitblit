@SETLOCAL

@SET gbhome=%~dp0
@SET gbhome=%gbhome:~0,-1%

@java -cp "%gbhome%\gitblit.jar";"%gbhome%\ext\*" com.gitblit.GitBlitServer --baseFolder "%gbhome%\data" %*

@ENDLOCAL
