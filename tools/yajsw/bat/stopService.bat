pushd %~dp0
call setenv.bat
%wrapper_bat% -p %conf_file%
popd



