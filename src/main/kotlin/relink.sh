#!/bin/sh

FS="All.kt Check_00.kt Check_01.kt Envs.kt Expr.kt Lexer.kt Parser.kt Scps.kt Tostr.kt Type.kt Ups.kt Visit.kt"

for f in $FS; do
    echo $f
    rm -f $f
    ln ../../../../ce0/src/main/kotlin/$f
done
