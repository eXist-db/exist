call setenv.bat
%java_exe% -cp "%wrapper_home%\wrapper.jar;%wrapper_home%\lib\extended\yajsw\srvmgr.jar" org.rzo.yajsw.srvmgr.server.ServerBooter 8899
pause