pushd %~dp0
call setenv.bat
%wrapperw_bat% -c %conf_file%
popd
