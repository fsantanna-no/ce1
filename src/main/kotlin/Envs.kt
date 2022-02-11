// s = Stmt.Var (var), Type (arg/ret), Block (@xxx)

fun Any.getEnv (): Any? {
    return when (this) {
        is Type -> this.wenv
        is Expr -> this.wenv
        is Stmt -> this.wenv
        else -> error("bug found")
    }
}

fun Any.toType (): Type {
    return when (this) {
        is Type         -> this
        is Stmt.Var     -> this.xtype!!
        is Stmt.Typedef -> this.type
        else -> error("bug found")
    }
}

fun Any.env_all (): List<Any> {
    return this.getEnv()?.let { listOf(it) + it.env_all() } ?: emptyList()
}

fun Any.env_first (f: (Any)->Boolean): Any? {
    fun aux (env: Any?): Any? {
        return when {
            (env == null) -> null
            f(env) -> env
            else -> aux(env.getEnv())
        }
    }
    return aux (this.getEnv())
}

fun Any.env (id: String, upval: Boolean=false): Any? {
    //print(">>> ") ; println(id)
    return this.env_first {
        //println(it)
        it is Stmt.Typedef && it.tk_.id.toLowerCase()==id.toLowerCase() ||
        it is Stmt.Var     && it.tk_.id.toLowerCase()==id.toLowerCase() ||
        it is Stmt.Block   && it.scp1?.id?.toUpperCase()==id.toUpperCase() ||
        //it is Expr.Func  && (id=="arg" || id=="ret" || id=="evt")
        it is Expr.Func  && (id=="arg" || id=="pub" || id=="ret" || id=="evt" || (upval && it.ups.any { it.id==id }))
    }.let {
        if (it is Expr.Func) {
            when (id) {
                "arg" -> it.type.inp
                "pub" -> it.type.pub!!
                "ret" -> it.type.out
                "evt" -> Type.Nat(Tk.Nat(TK.XNAT, it.tk.lin, it.tk.col, null, "int")).clone(it,it.tk.lin,it.tk.col)
                //else  -> error("bug found")
                else  -> (it.env(id) as Stmt.Var).xtype!!.clone(it,it.tk.lin,it.tk.col)
            }
        } else {
            it
        }
    }
}

//////////////////////////////////////////////////////////////////////////////

fun Stmt.setEnvs (env: Any?): Any? {
    this.wenv = env
    fun ft (tp: Type) { // recursive typedef
        tp.wenv = if (this is Stmt.Typedef) this else env
        when (tp) {
            is Type.Alias -> {
                tp.xisrec = tp.env(tp.tk_.id)?.toType()?.let {
                    it.flattenLeft().any { it is Type.Alias && it.tk_.id==tp.tk_.id }
                } ?: false
            }
        }
    }
    fun fe (e: Expr) {
        e.wenv = env
        when (e) {
            is Expr.Func -> e.block.setEnvs(e)
        }
    }
    return when (this) {
        is Stmt.Nop, is Stmt.Native, is Stmt.Return, is Stmt.Break, is Stmt.Throw -> env
        is Stmt.Var    -> { this.xtype?.visit(::ft,null) ; this }
        is Stmt.Set    -> { this.dst.visit(null,::fe,::ft,null) ; this.src.visit(null,::fe,::ft,null) ; env }
        is Stmt.SCall  -> { this.e.visit(null,::fe,::ft,null) ; env }
        is Stmt.SSpawn -> { this.dst.visit(null,::fe,::ft,null) ; this.call.visit(null,::fe,::ft,null) ; env }
        is Stmt.DSpawn -> { this.dst.visit(null,::fe,::ft,null) ; this.call.visit(null,::fe,::ft,null) ; env }
        is Stmt.Await  -> { this.e.visit(null,::fe,::ft,null) ; env }
        is Stmt.Bcast  -> { this.e.visit(null,::fe,::ft,null) ; env }
        is Stmt.Input  -> { this.dst?.visit(null,::fe,::ft,null) ; this.arg.visit(null,::fe,::ft,null) ; this.xtype?.visit(::ft,null) ; env }
        is Stmt.Output -> { this.arg.visit(null,::fe,::ft,null) ; env }
        is Stmt.Seq -> {
            val e1 = this.s1.setEnvs(env)
            val e2 = this.s2.setEnvs(e1)
            e2
        }
        is Stmt.If -> {
            this.tst.visit(null,::fe,::ft,null)
            this.true_.setEnvs(env)
            this.false_.setEnvs(env)
            env
        }
        is Stmt.Loop  -> { this.block.setEnvs(env) ; env }
        is Stmt.DLoop -> { this.i.visit(null,::fe,::ft,null) ; this.tsks.visit(null,::fe,::ft,null) ; this.block.setEnvs(env) ; env }
        is Stmt.Block -> {
            this.body.setEnvs(this) // also include blocks w/o labels b/c of inference
            env
        }
        is Stmt.Typedef -> { this.type.visit(::ft,null) ; this }
        else -> TODO(this.toString()) // do not remove this line b/c we may add new cases
    }
}