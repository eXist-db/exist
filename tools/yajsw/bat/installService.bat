pushd %~dp0
call setenv.bat
%wrapper_bat% -i %conf_file%
popd



