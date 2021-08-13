@REM --------------------------------------------------------------------------
@REM This is for reindexing Tickets with Lucene.
@REM
@REM Since the Tickets feature is undergoing massive churn it may be necessary 
@REM to reindex tickets due to model or index changes.
@REM
@REM usage:
@REM     reindex-tickets <baseFolder>
@REM
@REM --------------------------------------------------------------------------
@if [%1]==[] goto nobasefolder

@java -cp "%~dp0gitblit.jar";"%~dp0ext\*" com.gitblit.ReindexTickets --baseFolder %1
@goto end

:nobasefolder
@echo Please specify your baseFolder!
@echo.
@echo     reindex-tickets c:/gitblit-data
@echo.

:end
