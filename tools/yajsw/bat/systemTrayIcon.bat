pushd %~dp0
call setenv.bat
%wrapper_bat% -y %conf_file%
popd



