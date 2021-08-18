@echo off

set PATH_JACOCO_CLI_JAR="jacoco\lib"
set PATH_JCS_JAR="jcs_jar"
set PATH_JCS_FAT_JAR="jcs_fat_jar"

rem CREAZIONE CON JACOCO DA CLI DEL FAT-JAR INSTUMENTATO:
rem (https://www.jacoco.org/jacoco/trunk/doc/cli.html)

java -jar ${PATH_JACOCO_CLI_JAR}\jacococli.jar instrument ${PATH_JCS_JAR}\jcs-1.3.jar --dest ${PATH_JCS_FAT_JAR}

rem java -jar jacoco\lib\jacococli.jar instrument jcs_jar\jcs-1.3.jar --dest jcs_fat_jar\

