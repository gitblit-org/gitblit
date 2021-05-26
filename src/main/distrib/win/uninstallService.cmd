
@REM arch = x86, amd64, or ia32
SET ARCH=amd64

@REM Delete the gitblit service
"%~dp0%ARCH%\gitblit.exe" //DS//gitblit
