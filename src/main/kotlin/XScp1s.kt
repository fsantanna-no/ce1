// set increasing @i to each pointer in type
fun List<Type>.increasing (): List<Scope> {
    var c = 'h'     // first is 'i'
    return this
        .filter { it is Type.Pointer || it is Type.Alias }
        //.let { it as List<Type.Pointer> }
        .map { tp ->
            when (tp) {
                is Type.Pointer -> {
                    tp.xscp = tp.xscp ?: let {
                        c += 1  // infer implicit scope incrementally
                        Scope(Tk.Id(TK.XID, tp.tk.lin, tp.tk.col, c + ""), null)
                    }
                    listOf(tp.xscp!!)
                }
                is Type.Alias -> {
                    //assert(tp.xscp1s == null)
                    val isself = tp.ups_first { it is Stmt.Typedef }.let {
                        (it != null) && ((it as Stmt.Typedef).tk_.id == tp.tk_.id)
                    }
                    when {
                        isself -> emptyList()              // do not infer inside self declaration (it is inferred there)
                        (tp.xscps != null) -> tp.xscps!!  // just forward existing? (TODO: assert above failed)
                        else -> {
                            val def = tp.env(tp.tk_.id)!! as Stmt.Typedef
                            tp.xscps = def.xscp1s.first!!.map {
                                c += 1  // infer implicit scope incrementally
                                Scope(Tk.Id(TK.XID, tp.tk.lin, tp.tk.col, c + ""), null)
                            }
                            tp.xscps!!
                        }
                    }
                }
                else -> error("bug found")
            }
            .filter { it.scp1.isscopepar() }
            //.let { print("111: "); println(it) ; it}
            .filter { scp ->
                // do not return constant scopes and local outer/lexical scopes
                // do not add if already in outer Func
                //      var outer = func {@a1}->... {          // receives env
                //          return func @a1->()->/_int@a1 {   // holds/uses outer env
                tp.ups_tolist()
                    .filter { it is Type.Func || it is Stmt.Typedef }
                    //.let { println(it) ; it }
                    .any {
                        //println(it.toType().tostr())
                        (it !is Type.Func) || (it.xscps.second?.none { it.scp1.id==scp.scp1.id } ?: true)
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
                if (tp.xscps==null && tp.ups_first { it is Stmt.Typedef || it is Type.Func }==null) {
                    val def = tp.env(tp.tk_.id) as Stmt.Typedef
                    val size = def.xscp1s.first.let { if (it == null) 0 else it.size }
                    tp.xscps = List(size) { Scope(Tk.Id(TK.XID, tp.tk.lin, tp.tk.col, "LOCAL"), null) }
                } else {
                    // do not infer inside func/typedef declaration (it is inferred there)
                }
            }
            is Type.Pointer -> {
                // do not infer to LOCAL if inside function/typedef declaration
                if (tp.xscp==null && tp.ups_first { it is Type.Func || it is Stmt.Typedef }==null) {
                    tp.xscp = Scope(Tk.Id(TK.XID, tp.tk.lin, tp.tk.col, "LOCAL"), null)
                }
            }
            is Type.Func -> {
                val inp_pub_out = (tp.inp.flattenLeft() + (tp.pub?.flattenLeft() ?: emptyList()) + tp.out.flattenLeft()).increasing()

                // task needs to add implicit closure @LOCAL if absent
                val first = tp.xscps.first ?: if (tp.tk.enu==TK.FUNC) null else {
                    Scope(Tk.Id(TK.XID, tp.tk.lin, tp.tk.col, "LOCAL"), null)
                }

                // {closure} + {explicit scopes} + implicit inp_out
                val second = let {
                    // if returns closure, label must go to input scope
                    //      var f: func () -> (func @a1->()->())
                    //      var f: func {@a1} -> () -> (func @a1->()->())
                    val clo = if (tp.out is Type.Func && tp.out.xscps.first!=null && tp.out.xscps.first?.scp1?.enu==TK.XID) {
                        listOf(tp.out.xscps.first!!)
                    } else {
                        emptyList()
                    }

                    (clo + (tp.xscps.second ?: emptyList()) + inp_pub_out)
                        .distinctBy { it.scp1.id }
                        
                }
                tp.xscps = Triple(first, second, tp.xscps.third)
            }
        }
    }
    fun fe (e: Expr) {
        when (e) {
            is Expr.Func -> {
                if (e.type.xscps.first==null && e.ups.size>0) {
                    // take the largest scope among ups
                    val ups = e.ups
                        // Type.Ptr of all ups, find the one with deepest scope, return its scp1
                        .map { (e.env(it.id)!!.toType() as Type.Pointer?) }
                        .filterNotNull()
                        .firstOrNull()
                        ?.xscp
                            /*
                        .minByOrNull { it.toScp2().depth!! }
                        .let {
                            if (it?.xscp?.scp1?.id == "LOCAL") {
                                val blk =  it.ups_first { it is Stmt.Block } as Stmt.Block?
                                if (blk?.scp1 == null) {
                                    // if necessary, add label to enclosing block
                                    blk?.scp1 = Tk.Id(TK.XID, this.tk.lin, this.tk.col, "SS")
                                }
                                Scope(blk?.scp1 ?: Tk.Id(TK.XID, this.tk.lin, this.tk.col, "GLOBAL"), null)
                            } else {
                                it?.xscp
                            }
                        }
                             */
                    e.type.xscps = Triple(ups, e.type.xscps.second, TODO())
                }
            }
        }
    }
    fun fs (s: Stmt) {
        when (s) {
            is Stmt.Typedef -> {
                val tps  = s.type.flattenLeft()
                val scps = tps.increasing()
                val fst  = ((s.xscp1s.first?.map { Scope(it,null) } ?: emptyList()) + scps)
                    .distinctBy { it.scp1.id }
                    
                //println(tps)
                tps.filter { it is Type.Alias && it.xisrec }.let { it as List<Type.Alias> }.forEach {
                    it.xscps = fst
                }
                s.xscp1s = Pair(fst.map { it.scp1 }, s.xscp1s.second ?: emptyList())
            }
        }
    }
    this.visit(::fs, ::fe, ::ft, null)
}
