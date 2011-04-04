@if [%1]==[] goto missingparameters
@if [%2]==[] goto missingparameters

@java -cp "%CD%\ext\*" org.eclipse.jetty.http.security.Password %1 %2
@goto end

:missingparameters
@echo Usage:
@echo    makepassword username password
@echo.

:end