// set increasing @i to each pointer in type
fun List<Type>.increasing (): List<Tk.Id> {
    var c = 'h'     // first is 'i'
    return this
        .filter { it is Type.Pointer || it is Type.Alias }
        //.let { it as List<Type.Pointer> }
        .map { tp ->
            when (tp) {
                is Type.Pointer -> {
                    tp.xscp1 = tp.xscp1 ?: let {
                        c += 1  // infer implicit scope incrementally
                        Tk.Id(TK.XID, tp.tk.lin, tp.tk.col, c + "")
                    }
                    arrayOf(tp.xscp1!!)
                }
                is Type.Alias -> {
                    assert(tp.xscp1s == null)
                    val isself = tp.ups_first { it is Stmt.Typedef }.let {
                        (it != null) && ((it as Stmt.Typedef).tk_.id == tp.tk_.id)
                    }
                    when {
                        isself -> emptyArray()              // do not infer inside self declaration (it is inferred there)
                        (tp.xscp1s != null) -> tp.xscp1s!!  // just forward existing? (TODO: assert above failed)
                        else -> {
                            val def = tp.env(tp.tk_.id)!! as Stmt.Typedef
                            tp.xscp1s = def.xscp1s.first!!.map {
                                c += 1  // infer implicit scope incrementally
                                Tk.Id(TK.XID, tp.tk.lin, tp.tk.col, c + "")
                            }.toTypedArray()
                            tp.xscp1s!!
                        }
                    }
                }
                else -> error("bug found")
            }
            .filter { it.isscopepar() }
            //.let { print("111: "); println(it) ; it}
            .filter { tk ->
                // do not return constant scopes and local outer/lexical scopes
                // do not add if already in outer Func
                //      var outer = func {@a1}->... {          // receives env
                //          return func @a1->()->/_int@a1 {   // holds/uses outer env
                tp.ups_tolist()
                    .filter { it is Type.Func || it is Stmt.Typedef }
                    //.let { println(it) ; it }
                    .any {
                        //println(it.toType().tostr())
                        (it !is Type.Func) || (it.xscp1s.second?.none { it.id==tk.id } ?: true)
                    }
            }
            //.let {print("222: "); println(it) ; it}
        }
        .flatten()
}

fun Stmt.xinfScp1s () {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Alias -> {
                if (tp.xscp1s==null && tp.ups_first { it is Stmt.Typedef || it is Type.Func }==null) {
                    val def = tp.env(tp.tk_.id) as Stmt.Typedef
                    val size = def.xscp1s.first.let { if (it == null) 0 else it.size }
                    tp.xscp1s = Array(size) { Tk.Id(TK.XID, tp.tk.lin, tp.tk.col, "LOCAL") }
                } else {
                    // do not infer inside func/typedef declaration (it is inferred there)
                }
            }
            is Type.Pointer -> {
                // do not infer to LOCAL if inside function/typedef declaration
                if (tp.xscp1==null && tp.ups_first { it is Type.Func || it is Stmt.Typedef }==null) {
                    tp.xscp1 = Tk.Id(TK.XID, tp.tk.lin, tp.tk.col, "LOCAL")
                }
            }
            is Type.Func -> {
                val inp_pub_out = (tp.inp.flattenLeft() + (tp.pub?.flattenLeft() ?: emptyList()) + tp.out.flattenLeft()).increasing()

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

                    (clo + (tp.xscp1s.second ?: emptyArray()) + inp_pub_out)
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
    fun fs (s: Stmt) {
        when (s) {
            is Stmt.Typedef -> {
                val tps  = s.type.flattenLeft()
                val scps = tps.increasing()
                val fst  = ((s.xscp1s.first ?: emptyArray()) + scps)
                    .distinctBy { it.id }
                    .toTypedArray()
                //println(tps)
                tps.filter { it is Type.Alias && it.xisrec }.let { it as List<Type.Alias> }.forEach {
                    it.xscp1s = fst
                }
                s.xscp1s = Pair(fst, s.xscp1s.second ?: emptyArray())
            }

        }
    }
    this.visit(::fs, ::fe, ::ft)
}
