// Need to infer:
//  var x: ? = ...
//  var x: _int = input std: ?
//  var x: <(),()> = <.1>: ?
//  var x: _int = _v: ?

fun Expr.xinfTypes (inf: Type?) {
    this.type = when (this) {
        is Expr.Unit  -> Type.Unit(this.tk_).setUp(this)
        is Expr.Nat   -> this.type_ ?: inf!!.clone(this,this.tk.lin,this.tk.col)
        is Expr.Upref -> {
            All_assert_tk(this.tk, inf==null || inf is Type.Ptr) { "invalid inference : type mismatch"}
            this.pln.xinfTypes((inf as Type.Ptr?)?.pln)
            this.pln.type!!.let {
                Type.Ptr(this.tk_, Tk.Scope(TK.XSCOPE,this.tk.lin,this.tk.col,"var",null), it).setUp(it)
            }
        }
        is Expr.Dnref -> {
            this.ptr.xinfTypes(inf?.let {
                Type.Ptr (
                    Tk.Chr(TK.CHAR,this.tk.lin,this.tk.col,'/'),
                    Tk.Scope(TK.XSCOPE,this.tk.lin,this.tk.col,"local",null),
                    inf.clone(this,this.tk.lin,this.tk.col)
                ).setUp(this)
            })
            this.ptr.type!!.let {
                if (it is Type.Nat) it else {
                    All_assert_tk(this.tk, it is Type.Ptr) {
                        "invalid operand to `\\´ : not a pointer"
                    }
                    (it as Type.Ptr).pln
                }
            }
        }
        is Expr.TCons -> {
            All_assert_tk(this.tk, inf==null || inf is Type.Tuple) {
                "invalid inference : type mismatch"
            }
            this.arg.forEachIndexed { i,e -> e.xinfTypes(inf?.let { (inf as Type.Tuple).vec[i] }) }
            Type.Tuple(this.tk_, this.arg.map { it.type!! }.toTypedArray()).setUp(this)
        }
        is Expr.UCons -> {
            val x = when {
                (this.tk_.num == 0) -> Type.Unit(Tk.Sym(TK.UNIT, this.tk.lin, this.tk.col, "()")).setUp(this)
                (this.type != null) -> (this.type as Type.Union).expand()[this.tk_.num-1]
                else -> {
                    All_assert_tk(this.tk, inf is Type.Union) { "invalid inference : type mismatch"}
                    (inf as Type.Union).expand()[this.tk_.num-1].clone(this,this.tk.lin,this.tk.col)
                }
            }
            this.arg.xinfTypes(x)
            this.type_ ?: inf!!.clone(this,this.tk.lin,this.tk.col)
        }
        is Expr.New   -> {
            All_assert_tk(this.tk, inf==null || inf is Type.Ptr) {
                "invalid inference : type mismatch"
            }
            this.arg.xinfTypes((inf as Type.Ptr?)?.pln)
            Type.Ptr(Tk.Chr(TK.CHAR, this.tk.lin, this.tk.col, '/'), this.scp!!, this.arg.type!!).setUp(this)
        }
        is Expr.Inp -> this.type_ ?: inf!!.clone(this,this.tk.lin,this.tk.col)
        is Expr.Out -> {
            this.arg.xinfTypes(null)  // no inf b/c output always depends on the argument
            Type.Unit(Tk.Sym(TK.UNIT, this.tk.lin, this.tk.col, "()")).setUp(this)
        }
        is Expr.Call -> {
            val nat = Type.Nat(Tk.Nat(TK.XNAT, this.tk.lin, this.tk.col, null,"")).setUp(this)
            this.f.xinfTypes(nat)    // no infer for functions, default _ for nat
            this.f.type!!.let {
                when (it) {
                    is Type.Nat -> {
                        this.arg.xinfTypes(it)
                        it
                    }
                    is Type.Func -> {
                        this.arg.xinfTypes(it.inp)
                        val MAP = it.scps.map { Pair(it.lbl,it.num) }.zip(this.sinps.map { Pair(it.lbl,it.num) }).toMap()
                        fun f (tk: Tk.Scope): Tk.Scope {
                            return MAP[Pair(tk.lbl,tk.num)].let { if (it == null) tk else
                                Tk.Scope(TK.XSCOPE, tk.lin, tk.col, it.first, it.second)
                            }
                        }
                        fun map (tp: Type): Type {
                            return when (tp) {
                                is Type.Ptr   -> Type.Ptr(tp.tk_, f(tp.scope), map(tp.pln))
                                is Type.Tuple -> Type.Tuple(tp.tk_, tp.vec.map { map(it) }.toTypedArray())
                                is Type.Union -> Type.Union(tp.tk_, tp.isrec, tp.vec.map { map(it) }.toTypedArray())
                                is Type.Func  -> Type.Func(tp.tk_, if (tp.clo==null) null else f(tp.clo), tp.scps.map { f(it) }.toTypedArray(), map(tp.inp), map(tp.out))
                                else -> tp
                            }
                        }
                        map(it.out)
                    }
                    else -> {
                        All_assert_tk(this.f.tk, false) {
                            "invalid call : not a function"
                        }
                        error("impossible case")
                    }
                }
            }.clone(this.f,this.tk.lin,this.tk.col)
        }
        is Expr.Func -> {
            this.block.xinfTypes(null)
            this.type
        }
        is Expr.TDisc -> {
            this.tup.xinfTypes(null)  // not possible to infer big (tuple) from small (disc)
            this.tup.type!!.let {
                All_assert_tk(this.tk, it is Type.Tuple) {
                    "invalid discriminator : type mismatch"
                }
                val (MIN, MAX) = Pair(1, (it as Type.Tuple).vec.size)
                All_assert_tk(this.tk, MIN <= this.tk_.num && this.tk_.num <= MAX) {
                    "invalid discriminator : out of bounds"
                }
                it.vec[this.tk_.num - 1]
            }
        }
        is Expr.UDisc, is Expr.UPred -> {
            // not possible to infer big (union) from small (disc/pred)
            val (tk_,uni) = when (this) {
                is Expr.UPred -> { this.uni.xinfTypes(null) ; Pair(this.tk_,this.uni) }
                is Expr.UDisc -> { this.uni.xinfTypes(null) ; Pair(this.tk_,this.uni) }
                else -> error("impossible case")
            }
            val tp = uni.type!!

            All_assert_tk(this.tk, tp is Type.Union) {
                "invalid discriminator : not an union"
            }
            assert(tk_.num!=0 || tp.isrec()) { "bug found" }

            val (MIN, MAX) = Pair(if (tp.isrec()) 0 else 1, (tp as Type.Union).vec.size)
            All_assert_tk(this.tk, MIN <= tk_.num && tk_.num <= MAX) {
                "invalid discriminator : out of bounds"
            }

            when (this) {
                is Expr.UDisc -> if (this.tk_.num == 0) {
                    Type.Unit(Tk.Sym(TK.UNIT, this.tk.lin, this.tk.col, "()")).setUp(this)
                } else {
                    tp.expand()[this.tk_.num - 1]
                }
                is Expr.UPred -> Type.Nat(Tk.Nat(TK.XNAT, this.tk.lin, this.tk.col, null,"int")).setUp(this)
                else -> error("bug found")
            }
        }
        is Expr.Var -> this.env().let { it.second ?: it.first!!.type }!!
    }
}

fun Stmt.xinfTypes (inf: Type? = null) {
    when (this) {
        is Stmt.Nop, is Stmt.Break, is Stmt.Ret -> {}
        is Stmt.Var -> this.type = this.type ?: inf!!.clone(this,this.tk.lin,this.tk.col)
        is Stmt.Set -> {
            this.dst.xinfTypes(null)
            this.src.xinfTypes(this.dst.type!!)
        }
        is Stmt.SExpr -> this.e.xinfTypes(null)
        is Stmt.If -> {
            this.tst.xinfTypes(Type.Nat(Tk.Nat(TK.XNAT, this.tk.lin, this.tk.col, null,"int")).setUp(this))
            this.true_.xinfTypes(null)
            this.false_.xinfTypes(null)
        }
        is Stmt.Loop -> this.block.xinfTypes(null)
        is Stmt.Block -> this.body.xinfTypes(null)
        is Stmt.Seq -> {
            // var x: ...
            // set x = ...
            if (this.s1 is Stmt.Var && this.s2 is Stmt.Set && this.s2.dst is Expr.Var && this.s1.tk_.str==this.s2.dst.tk_.str) {
                if (this.s1.type == null) {
                    // infer var (s1) from expr (s2)
                    // var x: NO
                    // set x = OK
                    this.s2.src.xinfTypes(null)
                    this.s2.src.type!!.let {
                        this.s1.xinfTypes(it)
                        this.s2.dst.xinfTypes(null) //it
                    }
                } else {
                    // infer expr (s2) from var (s1)
                    // var x: OK
                    // set x = NO
                    this.s1.xinfTypes(null)
                    this.s2.xinfTypes(null) //this.s1.type
                }
            } else {
                this.s1.xinfTypes(null)
                this.s2.xinfTypes(null)
            }
        }
        else -> TODO(this.toString())
    }
}
