pushd %~dp0
call setenv.bat
%wrapper_bat% -c %conf_file%
popd

