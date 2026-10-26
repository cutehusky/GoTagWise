@echo off
setlocal
set "STRUCT_TAG_JAVA=java.exe"
if defined JAVA_HOME set "STRUCT_TAG_JAVA=%JAVA_HOME%\bin\java.exe"
"%STRUCT_TAG_JAVA%" -classpath "%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
