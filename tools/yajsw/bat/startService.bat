pushd %~dp0
call setenv.bat
%wrapper_bat% -t %conf_file%
popd



