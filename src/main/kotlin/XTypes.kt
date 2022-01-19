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
                        (blk == null) -> "GLOBAL"
                        (blk.xscp1 == null) -> {
                            val lbl = "SS" + it.tk_.str.toUpperCase()
                            blk.xscp1 = Tk.Scp1(TK.XSCPCST,this.tk.lin,this.tk.col,lbl,null)
                            lbl
                        }
                        else -> blk.xscp1!!.lbl
                    }
                } ?: "GLOBAL"
                val scp1 = Tk.Scp1(TK.XSCPCST,this.tk.lin,this.tk.col,lbl,null)
                Type.Ptr(this.tk_, scp1, scp1.toScp2(this), it).clone(this, this.tk.lin, this.tk.col)
            }
        }
        is Expr.Dnref -> {
            this.ptr.xinfTypes(inf?.let {
                val scp1 = Tk.Scp1(TK.XSCPCST,this.tk.lin,this.tk.col,"LOCAL",null)
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
            val x = if (this.xtype != null) {
                (this.xtype as Type.Union).expand()[this.tk_.num - 1]
            } else {
                All_assert_tk(this.tk, inf is Type.Union) { "invalid inference : type mismatch"}
                (inf as Type.Union).expand()[this.tk_.num-1].clone(this,this.tk.lin,this.tk.col)
            }
            this.arg.xinfTypes(x)
            All_assert_tk(this.tk, this.xtype!=null || inf!=null) {
                "invalid inference : undetermined type"
            }
            this.xtype ?: inf!!.clone(this,this.tk.lin,this.tk.col)
        }
        is Expr.UNull -> {
            All_assert_tk(this.tk, this.xtype!=null || inf!=null) {
                "invalid inference : undetermined type"
            }
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
                    this.xscp1 = Tk.Scp1(TK.XSCPCST, this.tk.lin, this.tk.col, "LOCAL", null)
                }
                this.xscp2 = this.xscp1!!.toScp2(this)
            }
            Type.Ptr(Tk.Chr(TK.CHAR, this.tk.lin, this.tk.col, '/'), this.xscp1!!, this.xscp2!!, this.arg.wtype!!).clone(this, this.tk.lin, this.tk.col)
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
        is Expr.Call -> {
            val nat = Type.Nat(Tk.Nat(TK.XNAT, this.tk.lin, this.tk.col, null,"")).clone(this, this.tk.lin, this.tk.col)
            this.f.xinfTypes(nat)    // no infer for functions, default _ for nat

            this.f.wtype!!.let { ft ->
                when (ft) {
                    is Type.Nat -> {
                        this.arg.xinfTypes(nat)
                        ft
                    }
                    is Type.Func -> {
                        val e = this
                        this.arg.xinfTypes(ft.inp.let {
                            // map @i1,@j1 -> @LOCAL,@LOCAL
                            fun Type.map (up: Any): Type {
                                fun Type.aux (): Type {
                                    return when (this) {
                                        is Type.Unit, is Type.Nat, is Type.Rec -> this
                                        is Type.Tuple -> Type.Tuple(this.tk_, this.vec.map { it.aux() }.toTypedArray())
                                        is Type.Union -> Type.Union(this.tk_, this.isrec, this.vec.map { it.aux() }.toTypedArray())
                                        is Type.Func  -> this
                                        is Type.Ptr   -> {
                                            val scp1 = Tk.Scp1(TK.XSCPCST, this.tk.lin, this.tk.col,"LOCAL",null)
                                            Type.Ptr(this.tk_, scp1, scp1.toScp2(e), this.pln.aux())
                                        }
                                    }
                                }
                                return this.aux().clone(up, this.tk.lin, this.tk.col)
                            }
                            it.map(this)
                        })

                        // Calculates type scopes {...}:
                        //  call f {...} arg

                        this.xscp1s = let {
                            // scope of expected closure environment
                            //      var f: func {@LOCAL} -> ...     // f will hold env in @LOCAL
                            //      set f = call g {@LOCAL} ()      // pass it for the builder function
                            val clo: List<Pair<Tk.Scp1,Tk.Scp1>> = if (inf is Type.Func && inf.xscp1s.first!=null) {
                                listOf(Pair((ft.out as Type.Func).xscp1s.first!!,inf.xscp1s.first!!))
                            } else {
                                emptyList()
                            }

                            /////////

                            val ret1s = if (inf == null) {
                                // no attribution expected, save to @LOCAL as shortest scope possible
                                ft.out.flattenLeft()
                                    .filter { it is Type.Ptr }
                                    .let { it as List<Type.Ptr> }
                                    .map { Tk.Scp1(TK.XSCPCST, it.tk.lin, it.tk.col, "LOCAL", null) }
                            } else {
                                inf.flattenLeft()
                                    .filter { it is Type.Ptr }
                                    .let { it as List<Type.Ptr> }
                                    .map { it.xscp1!! }
                            }
                            assert(ret1s.distinctBy { Pair(it.lbl,it.num) }.size <= 1) { "TODO: multiple pointer returns" }
                            val arg1s = this.arg.wtype!!.flattenLeft()
                                .filter { it is Type.Ptr }
                                .let { it as List<Type.Ptr> }
                                .map { it.xscp1!! }

                            // var ret = call f arg  ==>  { arg, ret }
                            val arg_ret: List<Tk.Scp1> = arg1s + ret1s

                            /////////

                            // func inp -> out  ==>  { inp, out }
                            val inp_out: List<Tk.Scp1> = (ft.inp.flattenLeft() + ft.out.flattenLeft())
                                .filter { it is Type.Ptr }
                                .let { it as List<Type.Ptr> }
                                .map { it.xscp1!! }

                            val xxx: List<Pair<Tk.Scp1, Tk.Scp1>> = inp_out.zip(arg_ret)

                            // [ (inp,arg), (out,ret) ] ==> remove all repeated inp/out
                            // TODO: what if out/ret are not the same for the removed reps?
                            val scp1s: List<Tk.Scp1> = (clo + xxx)
                                .filter { it.first.enu == TK.XSCPVAR }  // ignore constant labels (they not args)
                                .distinctBy { Pair(it.first.lbl,it.first.num) }
                                .map { it.second }

                            Pair(scp1s.toTypedArray(), if (ret1s.size==0) null else ret1s[0])
                        }
                        this.xscp2s = Pair (
                            this.xscp1s.first!!.map { it.toScp2(this) }.toTypedArray(),
                            this.xscp1s.second?.toScp2(this)
                        )

                        // calculates return of "e" call based on "e.f" function type
                        // "e" passes "e.arg" with "e.scp1s.first" scopes which may affect "e.f" return scopes
                        // we want to map these input scopes into "e.f" return scopes
                        //  var f: func /@a1 -> /@b_1
                        //              /     /---/
                        //  call f {@scp1,@scp2}  -->  /@scp2
                        //  f passes two scopes, first goes to @a1, second goes to @b_1 which is the return
                        //  so @scp2 maps to @b_1
                        // zip [[{@scp1a,@scp1b},{@scp2a,@scp2b}],{@a1,@b_1}]

                        if (ft.xscp1s.second!!.size != this.xscp1s.first!!.size) {
                            // TODO: may fail before check2, return anything
                            Type.Nat(Tk.Nat(TK.NATIVE, this.tk.lin, this.tk.col, null,"ERR")).clone(this,this.tk.lin,this.tk.col)
                        } else {
                            val MAP: List<Pair<Tk.Scp1, Pair<Tk.Scp1, Scp2>>> =
                                ft.xscp1s.second!!.zip(this.xscp1s.first!!.zip(this.xscp2s!!.first))

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
                            map(ft.out)
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
    }
}

fun Stmt.xinfTypes (inf: Type? = null) {
    fun unit (): Type {
        return Type.Unit(Tk.Sym(TK.UNIT, this.tk.lin, this.tk.col, "()")).clone(this, this.tk.lin, this.tk.col)
    }
    when (this) {
        is Stmt.Nop, is Stmt.Break, is Stmt.Ret, is Stmt.Nat -> {}
        is Stmt.Var -> this.xtype = this.xtype ?: inf!!.clone(this,this.tk.lin,this.tk.col)
        is Stmt.SSet -> {
            this.dst.xinfTypes(null)
            this.src.xinfTypes(this.dst.wtype!!)
        }
        is Stmt.ESet -> {
            this.dst.xinfTypes(null)
            this.src.xinfTypes(this.dst.wtype!!)
        }
        is Stmt.SCall -> this.e.xinfTypes(unit())
        is Stmt.Inp -> {
            //All_assert_tk(this.tk, this.xtype!=null || inf!=null) {
            //    "invalid inference : undetermined type"
            //}
            // inf is at least Unit
            this.arg.xinfTypes(null)
            this.xtype = this.xtype ?: inf?.clone(this,this.tk.lin,this.tk.col) ?: unit()
        }
        is Stmt.Out -> this.arg.xinfTypes(null)  // no inf b/c output always depends on the argument
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
            if (this.s1 is Stmt.Var && (this.s2 is Stmt.SSet && this.s2.dst is Expr.Var && this.s1.tk_.str==this.s2.dst.tk_.str || this.s2 is Stmt.ESet && this.s2.dst is Expr.Var && this.s1.tk_.str==this.s2.dst.tk_.str)) {
                if (this.s1.xtype == null) {
                    // infer var (s1) from expr (s2)
                    // var x: NO
                    // set x = OK
                    when (this.s2) {
                        is Stmt.SSet -> {
                            this.s2.src.xinfTypes(null)
                            this.s2.src.xtype!!.let {
                                this.s1.xinfTypes(it)
                                this.s2.dst.xinfTypes(null) //it
                            }
                        }
                        is Stmt.ESet -> {
                            this.s2.src.xinfTypes(null)
                            this.s2.src.wtype!!.let {
                                this.s1.xinfTypes(it)
                                this.s2.dst.xinfTypes(null) //it
                            }
                        }
                        else -> error("bug found")
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
