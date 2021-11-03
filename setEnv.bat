@rem A mininum environmental set-up to allow console to
@rem run different ANT and different JDK than rest of the build system.
@rem Run this batch script ONCE per console.

@rem Prequisites:
@rem  	- JDK 7 needs to be used to compile and provide class files.
@rem 	- either ANT 1.9 line needs to be used to handle compilation
@rem	 when JRE from JDK 7 is to be used or
@rem	- java must be pointed out directly to version which is compatible
@rem	 with Your ANT system.

@echo Seting up path to java jdk home folder
@SET JAVA_HOME=c:\jdk1.7.0_80

@echo Seting up path to java.exe in JRE home folder
@SET JAVACMD=c:\jdk8\jre\bin\java.exe

@echo Removing old ANT path from 
@set PATH=%PATH:C:\ant\bin=%

@echo Adding new ANT path
@set PATH=c:\ant\bin;%PATH%

@echo --------------------------
@echo JAVA_HOME is "%JAVA_HOME%"
@echo --------------------------
@echo PATH is "%PATH%"
@echo --------------------------
@echo JRE version which will be running all non-ANT jobs is:
@java -version
@echo --------------------------
@echo JRE version which will be running ANT is:
@%JAVACMD% -version
@echo --------------------------
@echo Check if JRE version is at least 7.171, 8.161, or 9.148 
@echo Otherwise You may expect some SSL exceptions when scripts will
@echo attempt to connect to github to fetch dependencies.