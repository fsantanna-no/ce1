fun Stmt.xinfFuncs () {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Func -> {
                // set increasing @a_X to each pointer
                var c = 'a'
                var i = 1
                tp.xscp1s = Pair (
                    null,
                    (tp.inp.flatten() + tp.out.flatten())
                        .filter { it is Type.Ptr }
                        .let { it as List<Type.Ptr> }
                        .map {
                            it.xscp1 = Tk.Scp1(TK.XSCOPE, it.tk.lin, it.tk.col, c+"", i)
                            c += 1
                            i += 1
                            it.xscp1
                        }
                        .toTypedArray()
                )
            }
        }
    }
    this.visit(false, null, null, ::ft)
}

fun Stmt.xinfCalls () {
    fun fe (e: Expr) {
        when (e) {
            is Expr.Call -> {
                e.xscp1s = Pair(
                    (e.arg.wtype!!.flatten() + e.wtype!!.flatten())
                        .filter { it is Type.Ptr }
                        .let { it as List<Type.Ptr> }
                        .map { it.xscp1 }
                        .toTypedArray(),
                    null
                )
            }
        }
    }
    this.visit(false, null, ::fe, null)
}
