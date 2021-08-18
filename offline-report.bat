@echo off

set PATH_JACOCO_CLI_JAR="jacococli"
set PATH_JCS_SRC="jcs_source\java"
set PATH_JCS_JAR="jcs_jar"
set PATH_JCS_FAT_JAR="jcs_fat_jar"
set PATH_REPORT="target\site\jacoco"

rem 2) CREAZIONE CON JACOCO DA CLI DEL REPORT
rem (https://www.jacoco.org/jacoco/trunk/doc/cli.html)

rem la cartella corrispondente in target
mkdir %PATH_REPORT%

rem move jacoco.exec target\jacoco.exec

java -jar %PATH_JACOCO_CLI_JAR%\jacococli.jar report target\jacoco.exec --classfiles %PATH_JCS_JAR%\jcs-1.3.jar --sourcefiles %PATH_JCS_SRC% --html %PATH_REPORT% --xml %PATH_REPORT%\jacoco.xml --csv %PATH_REPORT%\jacoco.csv

rem java -jar jacococli\jacococli.jar report target\jacoco.exec --classfiles jcs_jar\jcs-1.3.jar --sourcefiles jcs_source\java --html target\site\jacoco\ --xml target\\site\jacoco\jacoco.xml --csv target\site\jacoco\jacoco.csv