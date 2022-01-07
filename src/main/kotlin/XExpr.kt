sealed class Expr (val tk: Tk) {
    data class Unit  (val tk_: Tk.Sym): Expr(tk_)
    data class Var   (val tk_: Tk.Str): Expr(tk_)
    data class Nat   (val tk_: Tk.Str, val type: Type?): Expr(tk_)
    data class TCons (val tk_: Tk.Chr, val arg: Array<Expr>): Expr(tk_)
    data class UCons (val tk_: Tk.Num, val type: Type?, val arg: Expr): Expr(tk_)
    data class TDisc (val tk_: Tk.Num, val tup: Expr): Expr(tk_)
    data class UDisc (val tk_: Tk.Num, val uni: Expr): Expr(tk_)
    data class UPred (val tk_: Tk.Num, val uni: Expr): Expr(tk_)
    data class New   (val tk_: Tk.Key, val scope: Tk.Scope?, val arg: Expr.UCons): Expr(tk_)
    data class Dnref (val tk_: Tk,     val ptr: Expr): Expr(tk_)
    data class Upref (val tk_: Tk.Chr, val pln: Expr): Expr(tk_)
    data class Inp   (val tk_: Tk.Key, val lib: Tk.Str, val type: Type?): Expr(tk_)
    data class Out   (val tk_: Tk.Key, val lib: Tk.Str, val arg: Expr): Expr(tk_)
    data class Call  (val tk_: Tk.Key, val sout: Tk.Scope?, val f: Expr, val sinps: Array<Tk.Scope>, val arg: Expr): Expr(tk_)
    data class Func  (val tk_: Tk.Key, val ups: Array<Tk.Str>, val type: Type.Func, val block: Stmt.Block) : Expr(tk_)
}

fun Expr.tostr (): String {
    return when (this) {
        is Expr.Unit -> "()"
        is Expr.Var  -> this.tk_.str
        else -> TODO()
    }
}