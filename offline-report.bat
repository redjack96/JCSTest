@echo off

set PATH_JACOCO_CLI_JAR="jacoco\lib"
set PATH_JCS_SRC="jcs_source\java"
set PATH_JCS_JAR="jcs_jar"
set PATH_JCS_FAT_JAR="jcs_fat_jar"

rem 2) CREAZIONE CON JACOCO DA CLI DEL REPORT
rem (https://www.jacoco.org/jacoco/trunk/doc/cli.html)

rem la cartella corrispondente in target
mkdir target\jacoco-gen\jcs-coverage\

move jacoco.exec target\jacoco.exec

java -jar %PATH_JACOCO_CLI_JAR%\jacococli.jar report target\jacoco.exec --classfiles %PATH_JCS_JAR%\jcs-1.3.jar --sourcefiles %PATH_JCS_SRC% --html target\jacoco-gen\jcs-coverage\ --xml target\jacoco-gen\jcs-coverage\file.xml --csv target\jacoco-gen\jcs-coverage\file.csv

rem java -jar jacoco\lib\jacococli.jar report target\jacoco.exec --classfiles jcs_jar\jcs-1.3.jar --sourcefiles jcs_source\java --html target\jacoco-gen\jcs-coverage\ --xml target\jacoco-gen\jcs-coverage\file.xml --csv target\jacoco-gen\jcs-coverage\file.csv