@if [%1]==[] goto missingparameters

@java -cp gitblit.jar;"%CD%\lib\*" com.gitblit.MakeRepository --create %1
@goto end

:missingparameters
@echo Usage:
@echo    makerepository path_to_repository
@echo.

:end