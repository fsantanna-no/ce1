val INF = mutableMapOf<Stmt.Var,Type>()

fun Expr.aux_tps (inf: Type?) {
    AUX.tps[this] = when (this) {
        is Expr.Unit  -> Type.Unit(this.tk_).up(this)
        is Expr.Nat   -> this.type ?: inf!!
        is Expr.Upref -> {
            if (inf == null) {
                this.pln.aux_tps(null)
                AUX.tps[this.pln]!!.let {
                    Type.Ptr(this.tk_, Tk.Scope(TK.XSCOPE,this.tk.lin,this.tk.col,"var",null), it).up(it)
                }
            } else {
                val tp = inf as Type.Ptr
                this.pln.aux_tps(tp.pln)
                inf
            }
        }
        is Expr.Dnref -> {
            if (inf == null) {
                this.ptr.aux_tps(null)
                AUX.tps[this.ptr].let {
                    if (it is Type.Nat) it else {
                        All_assert_tk(this.tk, it is Type.Ptr) {
                            "invalid operand to `\\´ : not a pointer"
                        }
                        (it as Type.Ptr).pln
                    }
                }
            } else {
                val tp = Type.Ptr (
                    Tk.Chr(TK.CHAR,this.tk.lin,this.tk.col,'/'),
                    Tk.Scope(TK.XSCOPE,this.tk.lin,this.tk.col,"local",null),
                    inf
                ).up(this)
                this.ptr.aux_tps(tp)
                inf
            }
        }
        is Expr.TCons -> {
            if (inf == null) {
                Type.Tuple(this.tk_, this.arg.map { it.aux_tps(null) ; AUX.tps[it]!! }.toTypedArray()).up(this)
            } else {
                val tp = inf as Type.Tuple
                this.arg.forEachIndexed { i,e -> e.aux_tps(tp.vec[i]) }
                inf
            }
        }
        is Expr.UCons -> {
            if (inf == null) {
                this.arg.aux_tps(null)
                this.type!!
            } else {
                assert(this.tk_.num > 0) { "TODO: <.0>" }
                val tp = inf as Type.Union
                this.arg.aux_tps(tp.vec[this.tk_.num-1])
                inf
            }
        }
        is Expr.New   -> {
            //TODO()
            this.arg.aux_tps(null)
            Type.Ptr(Tk.Chr(TK.CHAR,this.tk.lin,this.tk.col,'/'), this.scp!!, AUX.tps[this.arg]!!).up(this)
        }
        is Expr.Inp -> this.type ?: inf!!
        is Expr.Out -> {
            this.arg.aux_tps(null)  // no inf b/c output always depends on the argument
            Type.Unit(Tk.Sym(TK.UNIT, this.tk.lin, this.tk.col, "()")).up(this)
        }
        is Expr.Call -> {
            val nat = Type.Nat(Tk.Nat(TK.XNAT, this.tk.lin, this.tk.col, null,"")).up(this)
            this.f.aux_tps(nat)    // no infer for functions, default _ for nat
            AUX.tps[this.f].let {
                when (it) {
                    is Type.Nat -> {
                        this.arg.aux_tps(it)
                        it
                    }
                    is Type.Func -> {
                        this.arg.aux_tps(it.inp)
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
                                is Type.Func  -> Type.Func(tp.tk_, if (tp.clo==null) tp.clo else f(tp.clo), tp.scps.map { f(it) }.toTypedArray(), map(tp.inp), map(tp.out))
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
            }.lincol(this.tk.lin,this.tk.col).let {
                it.aux_upsenvs(this)
                it
            }
        }
        is Expr.Func -> {
            this.block.aux_tps(null)
            this.type
        }
        is Expr.TDisc -> {
            this.tup.aux_tps(null)  // not possible to infer big (tuple) from small (disc)
            AUX.tps[this.tup].let {
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
                is Expr.UPred -> { this.uni.aux_tps(null) ; Pair(this.tk_,this.uni) }
                is Expr.UDisc -> { this.uni.aux_tps(null) ; Pair(this.tk_,this.uni) }
                else -> error("impossible case")
            }
            val tp = AUX.tps[uni]!!

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
                    Type.Unit(Tk.Sym(TK.UNIT, this.tk.lin, this.tk.col, "()")).up(this)
                } else {
                    tp.expand()[this.tk_.num - 1]
                }
                is Expr.UPred -> Type.Nat(Tk.Nat(TK.XNAT, this.tk.lin, this.tk.col, null,"int")).up(this)
                else -> error("bug found")
            }
        }
        is Expr.Var -> this.env().let { it.second ?: INF[it.first!!] }!!
    }
}

// Need to infer:
//  var x: ? = ...
//  var x: _int = input std: ?
//  var x: <(),()> = <.1>: ?

fun Stmt.aux_tps (inf: Type? = null) {
    when (this) {
        is Stmt.Nop, is Stmt.Break, is Stmt.Ret -> {}
        is Stmt.Var -> {
            if (inf != null) {
                assert(this.type == null)
                INF[this] = inf
            }
        }
        is Stmt.Set -> {
            this.dst.aux_tps(null)
            this.src.aux_tps(AUX.tps[this.dst]!!)
        }
        is Stmt.SExpr -> this.e.aux_tps(null)
        is Stmt.If -> {
            this.tst.aux_tps(Type.Nat(Tk.Nat(TK.XNAT, this.tk.lin, this.tk.col, null,"int")).up(this))
            this.true_.aux_tps(null)
            this.false_.aux_tps(null)
        }
        is Stmt.Loop -> this.block.aux_tps(null)
        is Stmt.Block -> this.body.aux_tps(null)
        is Stmt.Seq -> {
            // var x: ...
            // set x = ...
            if (this.s1 is Stmt.Var && this.s2 is Stmt.Set && this.s2.dst is Expr.Var && this.s1.tk_.str==this.s2.dst.tk_.str) {
                if (this.s1.type == null) {
                    // infer var (s1) from expr (s2)
                    // var x: NO
                    // set x = OK
                    this.s2.src.aux_tps(null)
                    AUX.tps[this.s2.src]!!.let {
                        this.s1.aux_tps(it)
                        this.s2.dst.aux_tps(it)
                    }
                } else {
                    // infer expr (s2) from var (s1)
                    // var x: OK
                    // set x = NO
                    this.s1.aux_tps(null)
                    this.s2.aux_tps(this.s1.type)
                }
            } else {
                this.s1.aux_tps(null)
                this.s2.aux_tps(null)
            }
        }
        else -> TODO(this.toString())
    }
}
