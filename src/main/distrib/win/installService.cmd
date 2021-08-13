@REM Install Gitblit as a Windows service.

@REM gitblitw.exe (prunmgr.exe) is a GUI application for monitoring 
@REM and configuring the Gitblit procrun service.
@REM
@REM By default this tool launches the service properties dialog
@REM but it also has some other very useful functionality.
@REM
@REM http://commons.apache.org/daemon/procrun.html

@SETLOCAL

@REM arch = x86, amd64, or ia32
SET ARCH=amd64

@SET gbhome=%~dp0
@SET gbhome=%gbhome:~0,-1%

@REM Be careful not to introduce trailing whitespace after the ^ characters.
@REM Use ; or # to separate values in the --StartParams parameter.
"%gbhome%\%ARCH%\gitblit.exe"  //IS//gitblit ^
		 --DisplayName="gitblit" ^
		 --Description="a pure Java Git solution" ^
		 --Startup=auto ^
		 --LogPath="%gbhome%\logs" ^
		 --LogLevel=INFO ^
		 --LogPrefix=gitblit ^
		 --StdOutput=auto ^
		 --StdError=auto ^
		 --StartPath="%gbhome%" ^
		 --StartClass=com.gitblit.GitBlitServer ^
		 --StartMethod=main ^
		 --StartParams="--storePassword;gitblit;--baseFolder;%gbhome%\data" ^
		 --StartMode=jvm ^
		 --StopPath="%gbhome%" ^
		 --StopClass=com.gitblit.GitBlitServer ^
		 --StopMethod=main ^
		 --StopParams="--stop;--baseFolder;%gbhome%\data" ^
		 --StopMode=jvm ^
		 --Classpath="%gbhome%\gitblit.jar;%gbhome%\ext\*" ^
		 --Jvm=auto ^
		 --JvmMx=1024

@ENDLOCAL
