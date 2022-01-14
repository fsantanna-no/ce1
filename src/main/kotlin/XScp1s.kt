fun Stmt.xinfScp1s () {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Ptr -> {
                if (tp.xscp1==null && tp.ups_first { it is Type.Func }==null) {
                    tp.xscp1 = Tk.Scp1(TK.XSCPCST, tp.tk.lin, tp.tk.col, "LOCAL", null)
                }
            }
            is Type.Func -> {
                // if returns closure, label must go to input scope
                //      var f: func () -> (func @a1->()->())
                //      var f: func {@a1} -> () -> (func @a1->()->())
                val clo = if (tp.out is Type.Func && tp.out.xscp1s.first!=null && tp.out.xscp1s.first?.enu==TK.XSCPVAR) {
                    arrayOf(tp.out.xscp1s.first!!)
                } else {
                    emptyArray()
                }

                // set increasing @a_X to each pointer in [inp]->[out]
                val inp_out = let {
                    var c = 'i'
                    var i = 1

                    (tp.inp.flatten() + tp.out.flatten())
                        .filter { it is Type.Ptr }
                        .let { it as List<Type.Ptr> }
                        .map {
                            when {
                                (it.xscp1 == null) -> {                 // add implicit
                                    it.xscp1 = Tk.Scp1(TK.XSCPVAR, it.tk.lin, it.tk.col, c + "", i)
                                    c += 1
                                    //i += 1        // TODO: i
                                    it.xscp1!!
                                }
                                (it.xscp1!!.enu == TK.XSCPCST) -> null  // do not add constant scopes
                                else -> {
                                    // do not add if already in outer Func
                                    //      var outer = func {@a1}->... {          // receives env
                                    //          return func @a1->()->/_int@a1 {   // holds/uses outer env
                                    val outers = tp.ups_tolist().filter { it is Expr.Func } as List<Expr.Func>
                                    val me = it.xscp1!!
                                    if (outers.any { it.type.xscp1s.second?.any { it.lbl == me.lbl && it.num == me.num } != null }) {
                                        null
                                    } else {
                                        it.xscp1!!
                                    }
                                }
                            }
                        }
                        .filterNotNull()
                }

                // {closure} + {explicit scopes} + implicit inp_out
                val scp1s = (clo + (tp.xscp1s.second ?: emptyArray()) + inp_out)
                    .distinctBy { Pair(it.lbl,it.num) }
                    .toTypedArray()
                tp.xscp1s = Pair(tp.xscp1s.first, scp1s)
            }
        }
    }
    fun fe (e: Expr) {
        when (e) {
            is Expr.Func -> {
                if (e.type.xscp1s.first==null && e.ups.size>0) {
                    // take the largest scope among ups
                    val ups = e.ups
                        // Type.Ptr of all ups, find the one with deepest scope, return its scp1
                        .map { (e.env(it.str)!!.toType() as Type.Ptr?) }
                        .filterNotNull()
                        .minByOrNull { it.toScp2().depth }.let {
                            if (it?.xscp1?.lbl == "LOCAL") {
                                val blk =  it.ups_first { it is Stmt.Block } as Stmt.Block?
                                if (blk?.xscp1 == null) {
                                    // if necessary, add label to enclosing block
                                    blk?.xscp1 = Tk.Scp1(TK.XSCPCST, this.tk.lin, this.tk.col, "SS", null)
                                }
                                blk?.xscp1 ?: Tk.Scp1(TK.XSCPCST, this.tk.lin, this.tk.col, "GLOBAL", null)
                            } else {
                                it?.xscp1
                            }
                        }
                    e.type.xscp1s = Pair(ups, e.type.xscp1s.second)
                }
            }
        }
    }
    this.visit(false, null, ::fe, ::ft)
}
