@REM --------------------------------------------------------------------------
@REM This is for migrating Tickets from one service to another.
@REM
@REM usage:
@REM     migrate-tickets <outputservice> <baseFolder>
@REM
@REM --------------------------------------------------------------------------
@if [%1]==[] goto help

@if [%2]==[] goto help

@java -cp "%~dp0gitblit.jar";"%~dp0ext\*" com.gitblit.MigrateTickets %1 --baseFolder %2
@goto end

:help
@echo Please specify the output ticket service and your baseFolder!
@echo.
@echo    e.g.: migrate-tickets com.gitblit.tickets.RedisTicketService "c:/gitblit data"
@echo.

:end
