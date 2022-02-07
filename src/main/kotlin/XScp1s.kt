fun Stmt.xinfScp1s () {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Pointer -> {
                if (tp.xscp1==null && tp.ups_first { it is Type.Func }==null) {
                    tp.xscp1 = Tk.Id(TK.XID, tp.tk.lin, tp.tk.col, "LOCAL")
                }
            }
            is Type.Func -> {
                // TODO: inp_pub_out
                // set increasing @a_X to each pointer in [inp]->[out]
                val inp_out = let {
                    var c = 'h'     // first is 'i'
                    (tp.inp.flattenLeft() + tp.out.flattenLeft())
                        .filter { it is Type.Pointer }
                        .let { it as List<Type.Pointer> }
                        .map { ptr ->
                            ptr.xscp1 = ptr.xscp1 ?: let {
                                // infer implicit scope incrementally
                                c += 1
                                Tk.Id(TK.XID, it.tk.lin, it.tk.col, c + "")
                            }

                            // do not return constant scopes and local outer/lexical scopes
                            if (ptr.xscp1!!.isscopecst()) {
                                // do not add constant scopes
                                null
                            } else {
                                // do not add if already in outer Func
                                //      var outer = func {@a1}->... {          // receives env
                                //          return func @a1->()->/_int@a1 {   // holds/uses outer env
                                val outers = tp.ups_tolist().filter { it is Expr.Func } as List<Expr.Func>
                                val me = ptr.xscp1!!
                                if (outers.any { it.type.xscp1s.second?.any { it.id==me.id } ?: false }) {
                                    null
                                } else {
                                    me
                                }
                            }
                        }
                        .filterNotNull()
                }

                // task needs to add implicit closure @LOCAL if absent
                val first = tp.xscp1s.first ?: if (tp.tk.enu==TK.FUNC) null else {
                    Tk.Id(TK.XID, tp.tk.lin, tp.tk.col, "LOCAL")
                }

                // {closure} + {explicit scopes} + implicit inp_out
                val second = let {
                    // if returns closure, label must go to input scope
                    //      var f: func () -> (func @a1->()->())
                    //      var f: func {@a1} -> () -> (func @a1->()->())
                    val clo = if (tp.out is Type.Func && tp.out.xscp1s.first!=null && tp.out.xscp1s.first?.enu==TK.XID) {
                        arrayOf(tp.out.xscp1s.first!!)
                    } else {
                        emptyArray()
                    }

                    (clo + (tp.xscp1s.second ?: emptyArray()) + inp_out)
                        .distinctBy { it.id }
                        .toTypedArray()
                }
                tp.xscp1s = Triple(first, second, tp.xscp1s.third ?: emptyArray())
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
                        .map { (e.env(it.id)!!.toType() as Type.Pointer?) }
                        .filterNotNull()
                        .minByOrNull { it.toScp2().depth!! }.let {
                            if (it?.xscp1?.id == "LOCAL") {
                                val blk =  it.ups_first { it is Stmt.Block } as Stmt.Block?
                                if (blk?.xscp1 == null) {
                                    // if necessary, add label to enclosing block
                                    blk?.xscp1 = Tk.Id(TK.XID, this.tk.lin, this.tk.col, "SS")
                                }
                                blk?.xscp1 ?: Tk.Id(TK.XID, this.tk.lin, this.tk.col, "GLOBAL")
                            } else {
                                it?.xscp1
                            }
                        }
                    e.type.xscp1s = Triple(ups, e.type.xscp1s.second, TODO())
                }
            }
        }
    }
    this.visit(null, ::fe, ::ft)
}
