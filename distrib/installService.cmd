@REM Install Gitblit as a Windows service.

@REM gitblitw.exe (prunmgr.exe) is a GUI application for monitoring 
@REM and configuring the Gitblit procrun service.
@REM
@REM By default this tool launches the service properties dialog
@REM but it also has some other very useful functionality.
@REM
@REM http://commons.apache.org/daemon/procrun.html

@REM arch = x86, amd64, or ia32
SET ARCH=amd64

@REM Be careful not to introduce trailing whitespace after the ^ characters.
@REM Use ; or # to separate values in the --StartParams parameter.
"%CD%\%ARCH%\gitblit.exe"  //IS//gitblit ^
		 --DisplayName="gitblit" ^
		 --Description="a pure Java Git solution" ^
		 --Startup=auto ^
		 --LogPath="%CD%\logs" ^
		 --LogLevel=INFO ^
		 --LogPrefix=gitblit ^
		 --StdOutput=auto ^
		 --StdError=auto ^
		 --StartPath="%CD%" ^
		 --StartClass=com.gitblit.Launcher ^
		 --StartMethod=main ^
		 --StartParams="--storePassword;gitblit" ^
		 --StartMode=jvm ^
		 --StopPath="%CD%" ^
		 --StopClass=com.gitblit.Launcher ^
		 --StopMethod=main ^
		 --StopParams="--stop" ^
		 --StopMode=jvm ^
		 --Classpath="%CD%\gitblit.jar" ^
		 --Jvm=auto ^
		 --JvmMx=1024
		 