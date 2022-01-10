fun Stmt.xsetFuncs () {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Func -> {
                // set increasing @a_X to each pointer
                var c = 'a'
                var i = 1
                tp.scps = (tp.inp.flatten() + tp.out.flatten())
                    .filter { it is Type.Ptr }
                    .let { it as List<Type.Ptr> }
                    .map {
                        it.scope = Tk.Scope(TK.XSCOPE, it.tk.lin, it.tk.col, c+"", i)
                        c += 1
                        i += 1
                        it.scope
                    }
                    .toTypedArray()
            }
        }
    }
    this.visit(null, null, ::ft)
}