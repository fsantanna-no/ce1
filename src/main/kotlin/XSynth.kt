fun Expr.tostr (): String {
    return when (this) {
        is Expr.Unit  -> "()"
        is Expr.Var   -> this.tk_.str
        is Expr.Nat   -> "_" + this.tk_.str
        is Expr.Inp   -> "input " + this.lib.str + ": " + AUX.tps[this]!!.tostr()
        is Expr.TCons -> "[" + this.arg.map { it.tostr() }.joinToString(",") + "]"
        is Expr.UCons -> "<." + this.tk_.num + " " + this.arg.tostr() + ">: " + AUX.tps[this]!!.tostr()
        else -> TODO(this.toString())
    }
}

fun Stmt.tostr (): String {
    return when (this) {
        is Stmt.Var -> "var " + this.tk_.str + ": " + (this.type ?: INF[this])!!.tostr() + "\n"
        is Stmt.Set -> "set " + this.dst.tostr() + " = " + this.src.tostr() + "\n"
        is Stmt.Seq -> this.s1.tostr() + this.s2.tostr()
        else -> TODO()
    }
}