// Need to infer:
//  var x: ? = ...
//  var x: _int = input std: ?
//  var x: <(),()> = <.1>: ?
//  var x: _int = _v: ?

fun Type.mapScp1 (up: Any, to: Tk.Id): Type {
    fun Type.aux (): Type {
        return when (this) {
            is Type.Unit, is Type.Nat, is Type.Spawn, is Type.Spawns -> this
            is Type.Tuple   -> Type.Tuple(this.tk_, this.vec.map { it.aux() }.toTypedArray())
            is Type.Union   -> Type.Union(this.tk_, this.vec.map { it.aux() }.toTypedArray())
            is Type.Func    -> this
            is Type.Pointer -> Type.Pointer(this.tk_, to, to.toScp2(this), this.pln.aux())
            is Type.Alias   -> Type.Alias(this.tk_, this.xisrec,
                /*arrayOf(to),*/                this.xscp1s!!.map{to}.toTypedArray(),   // TODO: wrong
                /*arrayOf(to.toScp2(up)))*/     this.xscp1s!!.map{to.toScp2(up)}.toTypedArray())
        }
    }
    return this.aux().clone(up, this.tk.lin, this.tk.col)
}

fun Expr.xinfTypes (inf: Type?) {
    this.wtype = when (this) {
        is Expr.Unit  -> this.wtype!!
        is Expr.Nat   -> this.xtype ?: inf!!.clone(this,this.tk.lin,this.tk.col)
        is Expr.Upref -> {
            All_assert_tk(this.tk, inf==null || inf is Type.Pointer) { "invalid inference : type mismatch"}
            this.pln.xinfTypes((inf as Type.Pointer?)?.pln)
            this.pln.wtype!!.let {
                val lbl = this.toBaseVar()?.let {
                    val blk = (it.env(it.tk_.id) as Stmt.Var).ups_first { it is Stmt.Block } as Stmt.Block?
                    when {
                        (blk == null) -> "GLOBAL"
                        (blk.xscp1 == null) -> {
                            val lbl = "SS" + it.tk_.id.toUpperCase()
                            blk.xscp1 = Tk.Id(TK.XID,this.tk.lin,this.tk.col,lbl)
                            lbl
                        }
                        else -> blk.xscp1!!.id
                    }
                } ?: "GLOBAL"
                val scp1 = Tk.Id(TK.XID,this.tk.lin,this.tk.col,lbl)
                Type.Pointer(this.tk_, scp1, scp1.toScp2(this), it).clone(this, this.tk.lin, this.tk.col)
            }
        }
        is Expr.Dnref -> {
            this.ptr.xinfTypes(inf?.let {
                val scp1 = Tk.Id(TK.XID,this.tk.lin,this.tk.col,"LOCAL")
                Type.Pointer (
                    Tk.Chr(TK.CHAR,this.tk.lin,this.tk.col,'/'),
                    scp1,
                    scp1.toScp2(this),
                    inf
                ).clone(this, this.tk.lin, this.tk.col)
            })
            this.ptr.wtype!!.let {
                if (it is Type.Nat) it else {
                    All_assert_tk(this.tk, it is Type.Pointer) {
                        "invalid operand to `\\´ : not a pointer"
                    }
                    (it as Type.Pointer).pln
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
            All_assert_tk(this.tk, this.xtype!=null || inf!=null) {
                "invalid inference : undetermined type"
            }
            if (this.xtype != null) {
                val x = (this.xtype!!.noalias() as Type.Union).vec[this.tk_.num - 1]
                this.arg.xinfTypes(x)
                this.xtype
            } else {
                assert(inf != null)
                val xinf = inf!!.noalias()
                    .mapScp1(this, Tk.Id(TK.XID, this.tk.lin, this.tk.col,"LOCAL")) // TODO: not always LOCAL
                All_assert_tk(this.tk, xinf is Type.Union) { "invalid inference : type mismatch : expected union : have ${inf!!.tostr()}"}
                val x = (xinf as Type.Union)
                    .vec[this.tk_.num-1]
                    .clone(this,this.tk.lin,this.tk.col)
                this.arg.xinfTypes(x)
                xinf
            }
        }
        is Expr.UNull -> {
            All_assert_tk(this.tk, this.xtype!=null || inf!=null) {
                "invalid inference : undetermined type"
            }
            this.xtype ?: inf!!
                .clone(this,this.tk.lin,this.tk.col)
                .mapScp1(this, Tk.Id(TK.XID, this.tk.lin, this.tk.col,"LOCAL")) // TODO: not always LOCAL
        }
        is Expr.New   -> {
            All_assert_tk(this.tk, inf==null || inf is Type.Pointer) {
                "invalid inference : type mismatch"
            }
            this.arg.xinfTypes((inf as Type.Pointer?)?.pln)
            if (this.xscp1 == null) {
                if (inf is Type.Pointer) {
                    this.xscp1 = inf.xscp1
                } else {
                    this.xscp1 = Tk.Id(TK.XID, this.tk.lin, this.tk.col, "LOCAL")
                }
                println(inf)
                println(this.xscp1)
                this.xscp2 = this.xscp1!!.toScp2(this)
            }
            Type.Pointer (
                Tk.Chr(TK.CHAR, this.tk.lin, this.tk.col, '/'),
                this.xscp1!!,
                this.xscp2!!,
                this.arg.wtype!!
            ).clone(this, this.tk.lin, this.tk.col)
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
        is Expr.Pub -> {
            this.tsk.xinfTypes(null)  // not possible to infer big (tuple) from small (disc)
            this.tsk.wtype.let {
                All_assert_tk(this.tk, it is Type.Spawn) {
                    "invalid \"pub\" : type mismatch : expected active task"
                }
                (it as Type.Spawn).tsk.pub!!
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
            val xtp = tp.noalias()

            All_assert_tk(this.tk, xtp is Type.Union) {
                "invalid discriminator : not an union"
            }
            assert(tk_.num!=0 || tp.isrec()) { "bug found" }

            val (MIN, MAX) = Pair(if (tp.isrec()) 0 else 1, (xtp as Type.Union).vec.size)
            All_assert_tk(this.tk, MIN <= tk_.num && tk_.num <= MAX) {
                "invalid discriminator : out of bounds"
            }

            when (this) {
                is Expr.UDisc -> if (this.tk_.num == 0) {
                    Type.Unit(Tk.Sym(TK.UNIT, this.tk.lin, this.tk.col, "()")).clone(this, this.tk.lin, this.tk.col)
                } else {
                    xtp.vec[this.tk_.num - 1]
                }
                is Expr.UPred -> Type.Nat(Tk.Nat(TK.XNAT, this.tk.lin, this.tk.col, null,"int")).clone(this, this.tk.lin, this.tk.col)
                else -> error("bug found")
            }
        }
        is Expr.Var -> {
            val s = this.env(this.tk_.id)!!
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
                        this.xscp1s = Pair(this.xscp1s.first ?: emptyArray(), this.xscp1s.second)
                        ft
                    }
                    is Type.Func -> {
                        val e = this

                        // TODO: remove after change increasing?
                        //println(ft.inp.tostr())
                        //println(ft.inp.mapScp1(e, Tk.Id(TK.XID, this.tk.lin, this.tk.col,"LOCAL")).tostr())
                        this.arg.xinfTypes(ft.inp.mapScp1(e, Tk.Id(TK.XID, this.tk.lin, this.tk.col,"LOCAL")))

                        // Calculates type scopes {...}:
                        //  call f {...} arg

                        this.xscp1s = let {
                            // scope of expected closure environment
                            //      var f: func {@LOCAL} -> ...     // f will hold env in @LOCAL
                            //      set f = call g {@LOCAL} ()      // pass it for the builder function
                            val clo: List<Pair<Tk.Id,Tk.Id>> = if (inf is Type.Func && inf.xscp1s.first!=null) {
                                listOf(Pair((ft.out as Type.Func).xscp1s.first!!,inf.xscp1s.first!!))
                            } else {
                                emptyList()
                            }

                            /////////

                            val ret1s = if (inf == null) {
                                // no attribution expected, save to @LOCAL as shortest scope possible
                                ft.out.flattenLeft()
                                    .filter { it is Type.Pointer }
                                    .let { it as List<Type.Pointer> }
                                    .map { Tk.Id(TK.XID, it.tk.lin, it.tk.col, "LOCAL") }
                            } else {
                                inf.flattenLeft()
                                    .filter { it is Type.Pointer }
                                    .let { it as List<Type.Pointer> }
                                    .map { it.xscp1!! }
                            }
                            assert(ret1s.distinctBy { it.id }.size <= 1) { "TODO: multiple pointer returns" }
                            val arg1s = this.arg.wtype!!.flattenLeft()
                                .filter { it is Type.Pointer }
                                .let { it as List<Type.Pointer> }
                                .map { it.xscp1!! }

                            // var ret = call f arg  ==>  { arg, ret }
                            val arg_ret: List<Tk.Id> = arg1s + ret1s

                            /////////

                            // func inp -> out  ==>  { inp, out }
                            val inp_out: List<Tk.Id> = (ft.inp.flattenLeft() + ft.out.flattenLeft())
                                .filter { it is Type.Pointer }
                                .let { it as List<Type.Pointer> }
                                .map { it.xscp1!! }

                            val xxx: List<Pair<Tk.Id, Tk.Id>> = inp_out.zip(arg_ret)

                            // [ (inp,arg), (out,ret) ] ==> remove all repeated inp/out
                            // TODO: what if out/ret are not the same for the removed reps?
                            val scp1s: List<Tk.Id> = (clo + xxx)
                                .filter { it.first.enu == TK.XID }  // ignore constant labels (they not args)
                                .distinctBy { it.first.id }
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
                            val MAP: List<Pair<Tk.Id, Pair<Tk.Id, Scp2>>> =
                                ft.xscp1s.second!!.zip(this.xscp1s.first!!.zip(this.xscp2s!!.first))

                            fun Tk.Id.get(scp2: Scp2): Pair<Tk.Id, Scp2> {
                                return MAP.find { it.first.let { it.id == this.id } }?.second
                                    ?: Pair(this, scp2)
                            }

                            val outer = this
                            fun Type.map(): Type {
                                return when (this) {
                                    is Type.Pointer -> this.xscp1!!.get(this.xscp2!!).let {
                                        Type.Pointer(this.tk_, it.first, it.second, this.pln.map())
                                            .clone(outer, outer.tk.lin, outer.tk.col)
                                    }
                                    is Type.Tuple -> Type.Tuple(this.tk_, this.vec.map { it.map() }.toTypedArray())
                                        .clone(outer, outer.tk.lin, outer.tk.col)
                                    is Type.Union -> Type.Union(this.tk_, this.vec.map { it.map() }.toTypedArray())
                                        .clone(outer, outer.tk.lin, outer.tk.col)
                                    is Type.Func -> {
                                        val clo = this.xscp1s.first?.get(this.xscp2s!!.first!!)
                                        val (x1, x2) = this.xscp1s.second!!.zip(this.xscp2s!!.second)
                                            .map { it.first.get(it.second) }
                                            .unzip()
                                        Type.Func(
                                            this.tk_,
                                            Triple(clo?.first, x1.toTypedArray(), this.xscp1s.third),
                                            Pair(clo?.second, x2.toTypedArray()),
                                            this.inp.map(),
                                            this.pub?.map(),
                                            this.out.map()
                                        ).clone(outer, outer.tk.lin, outer.tk.col)
                                    }
                                    else -> this
                                }
                            }
                            ft.out.map()
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
        is Stmt.Nop, is Stmt.Break, is Stmt.Return, is Stmt.Native, is Stmt.Throw, is Stmt.Typedef -> {}
        is Stmt.Var -> this.xtype = this.xtype ?: inf!!.clone(this,this.tk.lin,this.tk.col)
        is Stmt.Set -> {
            this.dst.xinfTypes(null)
            this.src.xinfTypes(this.dst.wtype!!)
        }
        is Stmt.SCall -> this.e.xinfTypes(unit())
        is Stmt.SSpawn -> {
            this.dst.xinfTypes(null)
            this.call.xinfTypes(null)
        }
        is Stmt.DSpawn -> {
            this.dst.xinfTypes(null)
            this.call.xinfTypes(null)
        }
        is Stmt.Await -> this.e.xinfTypes(Type.Nat(Tk.Nat(TK.XNAT, this.tk.lin, this.tk.col, null,"int")).clone(this, this.tk.lin, this.tk.col))
        is Stmt.Bcast -> this.e.xinfTypes(Type.Nat(Tk.Nat(TK.XNAT, this.tk.lin, this.tk.col, null,"int")).clone(this, this.tk.lin, this.tk.col))
        is Stmt.Input -> {
            //All_assert_tk(this.tk, this.xtype!=null || inf!=null) {
            //    "invalid inference : undetermined type"
            //}
            // inf is at least Unit
            this.arg.xinfTypes(null)
            this.xtype = this.xtype ?: inf?.clone(this,this.tk.lin,this.tk.col) ?: unit()
        }
        is Stmt.Output -> this.arg.xinfTypes(null)  // no inf b/c output always depends on the argument
        is Stmt.If -> {
            this.tst.xinfTypes(Type.Nat(Tk.Nat(TK.XNAT, this.tk.lin, this.tk.col, null,"int")).clone(this, this.tk.lin, this.tk.col))
            this.true_.xinfTypes(null)
            this.false_.xinfTypes(null)
        }
        is Stmt.Loop -> this.block.xinfTypes(null)
        is Stmt.DLoop -> {
            this.tsks.xinfTypes(null)
            this.i.xinfTypes(null)
            this.block.xinfTypes(null)
        }
        is Stmt.Block -> this.body.xinfTypes(null)
        is Stmt.Seq -> {
            // var x: ...
            // set x = ...
            if (this.s1 is Stmt.Var && (this.s2 is Stmt.Input && this.s2.dst!!.let { it is Expr.Var && this.s1.tk_.id==it.tk_.id } || this.s2 is Stmt.SSpawn && this.s2.dst is Expr.Var && this.s1.tk_.id==this.s2.dst.tk_.id || this.s2 is Stmt.Set && this.s2.dst is Expr.Var && this.s1.tk_.id==this.s2.dst.tk_.id)) {
                if (this.s1.xtype == null) {
                    // infer var (s1) from expr (s2)
                    // var x: NO
                    // set x = OK
                    when (this.s2) {
                        is Stmt.Input -> {
                            this.s2.xinfTypes(null)
                            this.s2.xtype!!.let {
                                this.s1.xinfTypes(it)
                                this.s2.dst!!.xinfTypes(null) //it
                            }
                        }
                        is Stmt.SSpawn -> {
                            this.s2.call.xinfTypes(null)
                            this.s2.call.f.wtype!!.let {
                                this.s1.xinfTypes (
                                    Type.Spawn (
                                        Tk.Key(TK.ACTIVE,this.s2.tk.lin,this.s2.tk.col,"active"),
                                        it.clone(this.s2,this.s2.tk.lin,this.s2.tk.col) as Type.Func
                                    )
                                )
                                this.s2.dst.xinfTypes(null) //it
                            }
                        }
                        is Stmt.Set -> {
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
                    this.s2.xinfTypes(this.s1.xtype)
                }
            } else {
                this.s1.xinfTypes(null)
                this.s2.xinfTypes(null)
            }
        }
        else -> TODO(this.toString())
    }
}
