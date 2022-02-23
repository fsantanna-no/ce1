class XParser: Parser()
{
    override fun type(tasks: Boolean): Type {
        return when {
            all.accept(TK.XID) -> {
                val tk0 = all.tk0.astype()
                val scps = if (all.accept(TK.ATBRACK)) {
                    val ret = this.scp1s { it.asscope() }
                    all.accept_err(TK.CHAR, ']')
                    ret
                } else {
                    null
                }
                Type.Alias(tk0, false, scps?.map { Scope(it,null) })
            }
            all.accept(TK.CHAR, '/') -> {
                val tk0 = all.tk0 as Tk.Chr
                val pln = this.type(false)
                val scp = if (all.accept(TK.CHAR, '@')) {
                    all.accept_err(TK.XID)
                    all.tk0.asscope()
                } else {
                    null
                }
                Type.Pointer(tk0, if (scp==null) null else Scope(scp,null), pln)
            }
            all.accept(TK.FUNC) || all.accept(TK.TASK) || (tasks && all.accept(TK.TASKS)) -> {
                val tk0 = all.tk0 as Tk.Key
                val (scps, ctrs) = if (all.check(TK.ATBRACK)) {
                    val (x, y) = this.scopepars()
                    all.accept_err(TK.ARROW)
                    Pair(x, y)
                } else {
                    Pair(null, null)
                }

                // input & pub & output
                val inp = this.type(false)
                val pub = if (tk0.enu != TK.FUNC) {
                    all.accept_err(TK.ARROW)
                    this.type(false)
                } else null
                all.accept_err(TK.ARROW)
                val out = this.type(false) // right associative

                Type.Func(tk0,
                    Triple(
                        Scope(Tk.Id(TK.XID, tk0.lin, tk0.col, "LOCAL"),null),
                        if (scps==null) null else scps.map { Scope(it,null) },
                        ctrs),
                    inp, pub, out)
            }
            else -> super.type(tasks)
        }
    }

    override fun expr_one (): Expr {
        return when {
            all.accept(TK.XNAT) -> {
                val tk0 = all.tk0 as Tk.Nat
                val tp = if (!all.accept(TK.CHAR, ':')) null else {
                    this.type(false)
                }
                Expr.Nat(tk0, tp)
            }
            all.accept(TK.CHAR, '<') || (all.tk1.istype() && all.accept(TK.XID)) -> {
                val tkx = all.tk0
                all.accept_err(TK.CHAR, '.')
                all.accept_err(TK.XNUM)
                val tk0 = all.tk0 as Tk.Num
                val cons = when {
                    (tk0.num == 0) -> null
                    all.checkExpr() -> this.expr()
                    else -> Expr.Unit(Tk.Sym(TK.UNIT, all.tk1.lin, all.tk1.col, "()"))
                }
                val tp = if (tkx.enu == TK.CHAR) {
                    all.accept_err(TK.CHAR, '>')
                    if (!all.accept(TK.CHAR, ':')) null else this.type(false)
                } else {
                    Type.Alias(tkx as Tk.Id, false, null)
                }
                if (tk0.num == 0) {
                    Expr.UNull(tk0, tp)
                } else {
                    Expr.UCons(tk0, tp, cons!!)
                }
            }
            all.accept(TK.NEW) -> {
                val tk0 = all.tk0
                val e = this.expr()
                all.assert_tk(tk0, e is Expr.UCons && e.tk_.num != 0) {
                    "invalid `new` : expected constructor"
                }

                val scp = if (all.accept(TK.CHAR, ':')) {
                    all.accept_err(TK.CHAR, '@')
                    all.accept_err(TK.XID)
                    all.tk0.asscope()
                } else {
                    null
                }
                Expr.New(tk0 as Tk.Key, if (scp==null) null else Scope(scp,null), e as Expr.UCons)
            }
            else -> super.expr_one()
        }
    }

    override fun expr (): Expr {
        var e = this.expr_dots()

        // call
        if (all.checkExpr() || all.check(TK.ATBRACK)) {
            val iscps = if (!all.accept(TK.ATBRACK)) null else {
                val ret = this.scp1s { it.asscope() }
                all.accept_err(TK.CHAR, ']')
                ret
            }
            val arg = this.expr()
            val oscp = if (!all.accept(TK.CHAR, ':')) null else {
                all.accept_err(TK.CHAR, '@')
                all.accept_err(TK.XID)
                all.tk0.asscope()
            }
            e = Expr.Call(e.tk, e, arg,
                Pair(
                    if (iscps==null) null else iscps.map { Scope(it,null) },
                    if (oscp==null) null else Scope(oscp,null)
                ))
        }
        return e
    }

    override fun stmt(): Stmt {
        return when {
            all.accept(TK.VAR) -> {
                all.accept_err(TK.XID)
                val tk_id = all.tk0 as Tk.Id
                val tp = if (!all.accept(TK.CHAR, ':')) null else {
                    this.type(false)
                }
                if (all.accept(TK.CHAR, '=')) {
                    val tk0 = all.tk0 as Tk.Chr
                    val dst = Expr.Var(tk_id)
                    val src = when {
                        all.check(TK.INPUT) -> {
                            all.accept(TK.INPUT)
                            val tk = all.tk0 as Tk.Key
                            all.accept_err(TK.XID)
                            val lib = (all.tk0 as Tk.Id)
                            val arg = this.expr()
                            val inp = if (!all.accept(TK.CHAR, ':')) null else this.type(false)
                            Stmt.Input(tk, inp, dst, lib, arg)
                        }
                        all.check(TK.SPAWN) -> {
                            val s = this.stmt()
                            All_assert_tk(s.tk, s is Stmt.SSpawn) { "unexpected dynamic `spawn`" }
                            val ss = s as Stmt.SSpawn
                            Stmt.SSpawn(ss.tk_, dst, ss.call)
                        }
                        else -> {
                            val src = this.expr()
                            Stmt.Set(tk0, dst, src)
                        }
                    }
                    Stmt.Seq(tk_id, Stmt.Var(tk_id, tp), src)
                } else {
                    if (tp == null) {
                        all.err_expected("type declaration")
                    }
                    Stmt.Var(tk_id, tp)
                }
            }
            all.accept(TK.INPUT) -> {
                val tk = all.tk0 as Tk.Key
                all.accept_err(TK.XID)
                val lib = (all.tk0 as Tk.Id)
                val arg = this.expr()
                val tp = if (!all.accept(TK.CHAR, ':')) null else this.type(false)
                Stmt.Input(tk, tp, null, lib, arg)
            }
            all.accept(TK.IF) -> {
                val tk0 = all.tk0 as Tk.Key
                val tst = this.expr()
                val true_ = this.block()
                val false_ = if (all.accept(TK.ELSE)) {
                    this.block()
                } else {
                    Stmt.Block(Tk.Chr(TK.CHAR, all.tk1.lin, all.tk1.col, '{'), false, null, Stmt.Nop(all.tk0))
                }
                Stmt.If(tk0, tst, true_, false_)
            }
            all.accept(TK.RETURN) -> {
                if (!all.checkExpr()) {
                    Stmt.Return(all.tk0 as Tk.Key)
                } else {
                    val e = this.expr()
                    val old = All_nest("""
                        set ret = ${e.xtostr()}
                        return
                        
                    """.trimIndent()
                    )
                    val ret = this.stmts()
                    all = old
                    ret
                }
            }
            all.accept(TK.TYPE) -> {
                all.accept_err(TK.XID)
                val id = all.tk0.astype()
                val scps = if (all.check(TK.ATBRACK)) this.scopepars() else Pair(null, null)
                all.accept_err(TK.CHAR, '=')
                val tp = this.type(false)
                Stmt.Typedef(id, scps, tp)
            }
            all.accept(TK.SPAWN) -> {
                val tk0 = all.tk0 as Tk.Key
                if (all.check(TK.CHAR,'{')) {
                    val block = this.block()
                    val old = All_nest("""
                        spawn task () -> () -> () {
                            ${block.body.xtostr()}
                        } ()
                        
                    """.trimIndent())
                    val ret = this.stmt()
                    all = old
                    ret
                } else {
                    val e = this.expr()
                    All_assert_tk(tk0, e is Expr.Call) { "expected call expression" }
                    if (all.accept(TK.IN)) {
                        val tsks = this.expr()
                        Stmt.DSpawn(tk0, tsks, e as Expr.Call)
                    } else {
                        Stmt.SSpawn(tk0, null, e as Expr.Call)
                    }
                }
            }
            all.accept(TK.PAR) -> {
                var pars = mutableListOf<Stmt.Block>()
                pars.add(this.block())
                while (all.accept(TK.WITH)) {
                    pars.add(this.block())
                }
                val srcs = pars.map { "spawn { ${it.body.xtostr()} }" }.joinToString("\n")
                val old = All_nest(srcs + "await _0\n")
                val ret = this.stmts()
                all = old
                ret
            }
            all.accept(TK.WATCHING) -> {
                val cnd = this.expr()
                val blk = this.block()
                val old = All_nest("""
                    paror {
                        await ${cnd.xtostr()}
                    } with
                        ${blk.xtostr()}
                    
                """.trimIndent())
                val ret = this.stmt()
                all = old
                ret
            }
            all.accept(TK.PARAND) || all.accept(TK.PAROR) -> {
                val op = if (all.tk0.enu==TK.PARAND) "&&" else "||"
                var pars = mutableListOf<Stmt.Block>()
                pars.add(this.block())
                while (all.accept(TK.WITH)) {
                    pars.add(this.block())
                }
                val spws = pars.mapIndexed { i,x -> "var tk_${N}_$i = spawn { ${x.body.xtostr()} }" }.joinToString("\n")
                val oks  = pars.mapIndexed { i,_ -> "var ok_${N}_$i: _int = _((${D}tk_${N}_$i->task0.state == TASK_DEAD))" }.joinToString("\n")
                val sets = pars.mapIndexed { i,_ -> "set ok_${N}_$i = _(${D}ok_${N}_$i || (((uint64_t)${D}tk_${N}_$i)==${D}tk_x))" }.joinToString("\n")
                val chks = pars.mapIndexed { i,_ -> "${D}ok_${N}_$i" }.joinToString(" $op ")

                val old = All_nest("""
                    {
                        $spws
                        $oks
                        loop {
                            if _($chks) {
                                break
                            }
                            await evt?2
                            var tk_x = evt!2
                            $sets
                        }
                    }

                """.trimIndent()) //.let{println(it);it})
                val ret = this.stmt()
                all = old
                ret
            }
            else -> super.stmt()
        }.let { it1 ->
            val it2 = if (!all.check(TK.WHERE)) it1 else this.where(it1)
            val it3 = if (!all.accept(TK.UNTIL)) it2 else {
                //val tk0 = all.tk0
                //All_assert_tk(tk0, stmt !is Stmt.Var) { "unexpected `until`" }

                val cnd = this.expr()
                val old1 = All_nest("""
                    if ${cnd.xtostr()} {
                        break
                    }
                    
                """.trimIndent())
                val if1 = this.stmt()
                all = old1
                val if2 = if (!all.check(TK.WHERE)) if1 else this.where(if1)

                val old2 = All_nest("""
                    loop {
                        ${it2.xtostr()}
                        ${if2.xtostr()}
                    }
                    
                """.trimIndent())
                val ret = this.stmt()
                all = old2
                ret
            }
            //println(it3.xtostr())
            it3
        }
    }

    fun where (s: Stmt): Stmt {
        all.accept_err(TK.WHERE)
        val tk0 = all.tk0
        val blk = this.block()
        assert(!blk.iscatch && blk.scp1.isanon()) { "TODO" }
        return when {
            (s !is Stmt.Seq) -> {
                val old = All_nest("""
                    {
                        ${blk.body.xtostr()}
                        ${s.xtostr()}
                    }
                    
                """.trimIndent())
                val ret = this.stmt()
                all = old
                ret
            }
            (s.s1 is Stmt.Var) -> {
                /*
                    val old = All_nest("""
                        ${until.s1.xtostr()}        // this wouldn't work b/c var has no type yet
                        {
                            ${blk.body.xtostr()}
                            ${until.s2.xtostr()}
                        }
                    """.trimIndent())
                     */
                Stmt.Seq(
                    tk0, s.s1,
                    Stmt.Block(
                        blk.tk_, blk.iscatch, blk.scp1,
                        Stmt.Seq(tk0, blk.body, s.s2)
                    )
                )
            }
            (s.s2 is Stmt.Return) -> {
                val old = All_nest("""
                    {
                        ${blk.body.xtostr()}
                        ${s.s1.xtostr()}
                    }
                    ${s.s2.xtostr()}
                    
                """.trimIndent())
                val ret = this.stmt()
                all = old
                ret
            }
            else -> error("bug found")
        }
    }
}
