@REM --------------------------------------------------------------------------
@REM This is for Lucene search integration.
@REM
@REM Allows you to add an indexed branch specification to the repository config
@REM for all matching repositories in the specified folder.
@REM
@REM All repositories are included unless excluded using a --skip parameter.
@REM --skip supports simple wildcard fuzzy matching however only 1 asterisk is
@REM allowed per parameter.
@REM
@REM Always use forward-slashes for the path separator in your parameters!!
@REM
@REM Set FOLDER to the server's git.repositoriesFolder
@REM Set BRANCH ("default" or fully qualified ref - i.e. refs/heads/master)
@REM Set EXCLUSIONS for any repositories that you do not want to change
@REM --------------------------------------------------------------------------
@SETLOCAL
@SET FOLDER=data/git
@SET EXCLUSIONS=--skip test.git --skip group/test*
@SET BRANCH=default
@PUSHD %~dp0
@java -cp gitblit.jar;"%CD%\ext\*" com.gitblit.AddIndexedBranch --repositoriesFolder %FOLDER% --branch %BRANCH% %EXCLUSIONS% %*
@POPD
@ENDLOCAL
