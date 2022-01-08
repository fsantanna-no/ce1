fun Expr.tostr (): String {
    return when (this) {
        is Expr.Unit -> "()"
        is Expr.Var  -> this.tk_.str
        is Expr.Inp  -> "input " + this.lib.str + ": " + AUX.tps[this]!!.tostr()
        else -> TODO()
    }
}

fun Stmt.tostr (): String {
    return when (this) {
        is Stmt.Var -> "var " + this.tk_.str + ": " + (this.type ?: AUX.inf[this])!!.tostr() + "\n"
        is Stmt.Set -> "set " + this.dst.tostr() + " = " + this.src.tostr() + "\n"
        is Stmt.Seq -> this.s1.tostr() + this.s2.tostr()
        else -> TODO()
    }
}