pushd %~dp0
call setenv.bat
%wrapperw_bat% -y %conf_file%
popd