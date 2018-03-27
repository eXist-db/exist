rem starts a test java program. this can be used to test YAJSW
rem start the program, note its pid (of the java app), call genConfig <pid>, call runConsole, ...

pushd %~dp0
call setenv.bat
%java_exe% -cp %wrapper_jar%;%wrapper_app_jar% test.HelloWorld
popd
