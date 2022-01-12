fun Stmt.xinfScp1s () {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Ptr -> {
                if (tp.xscp1==null && tp.ups_first { it is Type.Func }==null) {
                    tp.xscp1 = Tk.Scp1(TK.XSCOPE, tp.tk.lin, tp.tk.col, "local", null)
                }
            }
            is Type.Func -> {
                // set increasing @a_X to each pointer
                var c = 'i'
                var i = 1
                tp.xscp1s = Pair (
                    null,
                    (tp.inp.flatten() + tp.out.flatten())
                        .filter { it is Type.Ptr }
                        .let { it as List<Type.Ptr> }
                        .map {
                            if (it.xscp1 == null) {
                                it.xscp1 = Tk.Scp1(TK.XSCOPE, it.tk.lin, it.tk.col, c + "", i)
                                c += 1
                                //i += 1
                            }
                            it.xscp1!!
                        }
                        .distinctBy { Pair(it.lbl,it.num) }
                        .toTypedArray()
                )
            }
        }
    }
    fun fe (e: Expr) {
        when (e) {
            is Expr.Func -> {
                if (e.type.xscp1s.first==null && e.ups.size>0) {
                    // take the largest scope among ups
                    val ups = e.ups
                        // (var v, blk of v)
                        .map { Pair(it.str, (e.env(it.str) as Stmt.Var).ups_first { it is Stmt.Block } as Stmt.Block?) }
                        .let { it as List<Pair<String,Stmt.Block?>> }
                        // blk with deepest scope (min number of up blocks)
                        .minByOrNull { it.second?.ups_tolist()?.count { it is Stmt.Block } ?: 0 }!!
                        .let {
                            if (it.second == null) {
                                Tk.Scp1(TK.XSCOPE, this.tk.lin, this.tk.col, "global", null)
                            } else {
                                if (it.second != null && it.second!!.xscp1 == null) {
                                    val lbl = "ss" + it.first
                                    it.second!!.xscp1 = Tk.Scp1(TK.XSCOPE, this.tk.lin, this.tk.col, lbl, null)
                                    println(it.second)
                                }
                                it.second?.xscp1
                            }
                        }
                    e.type.xscp1s = Pair(ups, e.type.xscp1s.second)
                }
            }
        }
    }
    this.visit(false, null, ::fe, ::ft)
}
