rem usage genconfig.bat <pid> [-d <default configuration file>]<output file> 

pushd %~dp0
call setenv.bat

%wrapper_bat% -g %1 -d %conf_default_file% %conf_file%
popd

