@REM
@REM  Copyright Lealone Database Group.
@REM  Licensed under the Server Side Public License, v 1.
@REM  Initial Developer: zhh

@echo off
java -cp ../lib/lealone-opscenter-1.0.0.jar^
     org.lealone.opscenter.main.OpsCenterSqlScript^
     -serviceDir ../sql
