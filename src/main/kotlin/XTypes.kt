// Need to infer:
//  var x: ? = ...
//  var x: _int = input std: ?
//  var x: <(),()> = <.1>: ?
//  var x: _int = _v: ?

fun Expr.xinfTypes (inf: Type?) {
    this.wtype = when (this) {
        is Expr.Unit  -> this.wtype!!
        is Expr.Nat   -> this.xtype ?: inf!!.clone(this,this.tk.lin,this.tk.col)
        is Expr.Upref -> {
            All_assert_tk(this.tk, inf==null || inf is Type.Ptr) { "invalid inference : type mismatch"}
            this.pln.xinfTypes((inf as Type.Ptr?)?.pln)
            this.pln.wtype!!.let {
                val lbl = this.toBaseVar()?.let {
                    val blk = (it.env() as Stmt.Var).ups_first { it is Stmt.Block } as Stmt.Block?
                    when {
                        (blk == null) -> "global"
                        (blk.xscp1 == null) -> {
                            val lbl = "ss" + it.tk_.str
                            blk.xscp1 = Tk.Scp1(TK.XSCOPE,this.tk.lin,this.tk.col,lbl,null)
                            lbl
                        }
                        else -> blk.xscp1!!.lbl
                    }
                } ?: "global"
                val scp1 = Tk.Scp1(TK.XSCOPE,this.tk.lin,this.tk.col,lbl,null)
                Type.Ptr(this.tk_, scp1, scp1.toScp2(this), it).clone(this, this.tk.lin, this.tk.col)
            }
        }
        is Expr.Dnref -> {
            this.ptr.xinfTypes(inf?.let {
                val scp1 = Tk.Scp1(TK.XSCOPE,this.tk.lin,this.tk.col,"local",null)
                Type.Ptr (
                    Tk.Chr(TK.CHAR,this.tk.lin,this.tk.col,'/'),
                    scp1,
                    scp1.toScp2(this),
                    inf
                ).clone(this, this.tk.lin, this.tk.col)
            })
            this.ptr.wtype!!.let {
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
            Type.Tuple(this.tk_, this.arg.map { it.wtype!! }.toTypedArray()).clone(this, this.tk.lin, this.tk.col)
        }
        is Expr.UCons -> {
            val x = when {
                (this.tk_.num == 0) -> Type.Unit(Tk.Sym(TK.UNIT, this.tk.lin, this.tk.col, "()")).clone(this, this.tk.lin, this.tk.col)
                (this.xtype != null) -> (this.xtype as Type.Union).expand()[this.tk_.num-1]
                else -> {
                    All_assert_tk(this.tk, inf is Type.Union) { "invalid inference : type mismatch"}
                    (inf as Type.Union).expand()[this.tk_.num-1].clone(this,this.tk.lin,this.tk.col)
                }
            }
            this.arg.xinfTypes(x)
            this.xtype ?: inf!!.clone(this,this.tk.lin,this.tk.col)
        }
        is Expr.New   -> {
            All_assert_tk(this.tk, inf==null || inf is Type.Ptr) {
                "invalid inference : type mismatch"
            }
            this.arg.xinfTypes((inf as Type.Ptr?)?.pln)
            if (this.xscp1 == null) {
                if (inf is Type.Ptr) {
                    this.xscp1 = inf.xscp1
                } else {
                    this.xscp1 = Tk.Scp1(TK.XSCOPE, this.tk.lin, this.tk.col, "local", null)
                }
                this.xscp2 = this.xscp1!!.toScp2(this)
            }
            Type.Ptr(Tk.Chr(TK.CHAR, this.tk.lin, this.tk.col, '/'), this.xscp1!!, this.xscp2!!, this.arg.wtype!!).clone(this, this.tk.lin, this.tk.col)
        }
        is Expr.Inp -> {
            this.xtype ?: inf!!.clone(this,this.tk.lin,this.tk.col)
        }
        is Expr.Out -> {
            this.arg.xinfTypes(null)  // no inf b/c output always depends on the argument
            Type.Unit(Tk.Sym(TK.UNIT, this.tk.lin, this.tk.col, "()")).clone(this, this.tk.lin, this.tk.col)
        }
        is Expr.Call -> {
            val nat = Type.Nat(Tk.Nat(TK.XNAT, this.tk.lin, this.tk.col, null,"")).clone(this, this.tk.lin, this.tk.col)
            this.f.xinfTypes(nat)    // no infer for functions, default _ for nat
            this.f.wtype!!.let {
                when (it) {
                    is Type.Nat -> {
                        this.arg.xinfTypes(nat)
                        it
                    }
                    is Type.Func -> {
                        this.arg.xinfTypes(it.inp)

                        // calculates return of "e" call based on "e.f" function type
                        // "e" passes "e.arg" with "e.scp1s.first" scopes which may affect "e.f" return scopes
                        // we want to map these input scopes into "e.f" return scopes
                        //  var f: func /@a_1 -> /@b_1
                        //              /     /---/
                        //  call f {@scp1,@scp2}  -->  /@scp2
                        //  f passes two scopes, first goes to @a_1, second goes to @b_1 which is the return
                        //  so @scp2 maps to @b_1
                        // zip [[{@scp1a,@scp1b},{@scp2a,@scp2b}],{@a_1,@b_1}]
                        if (it.xscp1s.second!!.size != this.xscp1s.first!!.size) {
                            // TODO: may fail before check2, return anything
                            Type.Unit(Tk.Sym(TK.UNIT, this.tk.lin, this.tk.col, "()")).clone(this,this.tk.lin,this.tk.col)
                        } else {
                            val MAP: List<Pair<Tk.Scp1, Pair<Tk.Scp1, Scp2>>> =
                                it.xscp1s.second!!.zip(this.xscp1s.first!!.zip(this.xscp2s!!.first))

                            fun Tk.Scp1.get(scp2: Scp2): Pair<Tk.Scp1, Scp2> {
                                return MAP.find { it.first.let { it.lbl == this.lbl && it.num == this.num } }?.second
                                    ?: Pair(this, scp2)
                            }

                            fun map(tp: Type): Type {
                                return when (tp) {
                                    is Type.Ptr -> tp.xscp1!!.get(tp.xscp2!!).let {
                                        Type.Ptr(tp.tk_, it.first, it.second, map(tp.pln))
                                            .clone(this, this.tk.lin, this.tk.col)
                                    }
                                    is Type.Tuple -> Type.Tuple(tp.tk_, tp.vec.map { map(it) }.toTypedArray())
                                        .clone(this, this.tk.lin, this.tk.col)
                                    is Type.Union -> Type.Union(tp.tk_, tp.isrec, tp.vec.map { map(it) }.toTypedArray())
                                        .clone(this, this.tk.lin, this.tk.col)
                                    is Type.Func -> {
                                        val clo = tp.xscp1s.first?.get(tp.xscp2s!!.first!!)
                                        val (x1, x2) = tp.xscp1s.second!!.zip(tp.xscp2s!!.second)
                                            .map { it.first.get(it.second) }
                                            .unzip()
                                        Type.Func(
                                            tp.tk_,
                                            Pair(clo?.first, x1.toTypedArray()),
                                            Pair(clo?.second, x2.toTypedArray()),
                                            map(tp.inp),
                                            map(tp.out)
                                        ).clone(this, this.tk.lin, this.tk.col)
                                    }
                                    else -> tp
                                }
                            }
                            map(it.out)
                        }
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
            this.tup.wtype!!.let {
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
            val tp = uni.wtype!!

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
                    Type.Unit(Tk.Sym(TK.UNIT, this.tk.lin, this.tk.col, "()")).clone(this, this.tk.lin, this.tk.col)
                } else {
                    tp.expand()[this.tk_.num - 1]
                }
                is Expr.UPred -> Type.Nat(Tk.Nat(TK.XNAT, this.tk.lin, this.tk.col, null,"int")).clone(this, this.tk.lin, this.tk.col)
                else -> error("bug found")
            }
        }
        is Expr.Var -> {
            val s = this.env()!!
            All_assert_tk(this.tk, s !is Stmt.Var || s.xtype!=null) {
                "invalid inference : undetermined type"
            }
            s.toType()
        }
    }
}

fun Stmt.xinfTypes (inf: Type? = null) {
    when (this) {
        is Stmt.Nop, is Stmt.Break, is Stmt.Ret -> {}
        is Stmt.Var -> this.xtype = this.xtype ?: inf!!.clone(this,this.tk.lin,this.tk.col)
        is Stmt.Set -> {
            this.dst.xinfTypes(null)
            this.src.xinfTypes(this.dst.wtype!!)
        }
        is Stmt.SExpr -> this.e.xinfTypes(null)
        is Stmt.If -> {
            this.tst.xinfTypes(Type.Nat(Tk.Nat(TK.XNAT, this.tk.lin, this.tk.col, null,"int")).clone(this, this.tk.lin, this.tk.col))
            this.true_.xinfTypes(null)
            this.false_.xinfTypes(null)
        }
        is Stmt.Loop -> this.block.xinfTypes(null)
        is Stmt.Block -> this.body.xinfTypes(null)
        is Stmt.Seq -> {
            // var x: ...
            // set x = ...
            if (this.s1 is Stmt.Var && this.s2 is Stmt.Set && this.s2.dst is Expr.Var && this.s1.tk_.str==this.s2.dst.tk_.str) {
                if (this.s1.xtype == null) {
                    // infer var (s1) from expr (s2)
                    // var x: NO
                    // set x = OK
                    this.s2.src.xinfTypes(null)
                    this.s2.src.wtype!!.let {
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
