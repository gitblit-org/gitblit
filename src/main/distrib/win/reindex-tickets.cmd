@REM --------------------------------------------------------------------------
@REM This is for reindexing Tickets with Lucene.
@REM
@REM Since the Tickets feature is undergoing massive churn it may be necessary 
@REM to reindex tickets due to model or index changes.
@REM
@REM Always use forward-slashes for the path separator in your parameters!!
@REM
@REM Set FOLDER to the baseFolder.
@REM --------------------------------------------------------------------------
@SET FOLDER=c:/gitblit/data

@java -cp gitblit.jar;"%CD%\ext\*" com.gitblit.ReindexTickets --baseFolder %FOLDER%
