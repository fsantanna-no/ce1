fun xparser_type (all: All, tasks: Boolean=false): Type {
    return when {
        all.accept(TK.UNIT) -> Type.Unit(all.tk0 as Tk.Sym)
        all.accept(TK.XNAT) -> Type.Nat(all.tk0 as Tk.Nat)
        all.accept(TK.XUP)  -> Type.Rec(all.tk0 as Tk.Up)
        all.accept(TK.CHAR, '/') -> {
            val tk0 = all.tk0 as Tk.Chr
            val pln = xparser_type(all)
            val scp = if (all.accept(TK.XSCPCST) || all.accept(TK.XSCPVAR)) {
                all.tk0 as Tk.Scp1
            } else {
                null
            }
            Type.Ptr(tk0, scp, null, pln)
        }
        all.accept(TK.CHAR, '(') -> {
            val tp = xparser_type(all)
            all.accept_err(TK.CHAR, ')')
            tp
        }
        all.accept(TK.CHAR, '[') || all.accept(TK.CHAR, '<') -> {
            val tk0 = all.tk0 as Tk.Chr
            val tp = xparser_type(all)
            val tps = arrayListOf(tp)
            while (true) {
                if (!all.accept(TK.CHAR, ',')) {
                    break
                }
                val tp2 = xparser_type(all)
                tps.add(tp2)
            }
            if (tk0.chr == '[') {
                all.accept_err(TK.CHAR, ']')
                Type.Tuple(tk0, tps.toTypedArray())
            } else {
                all.accept_err(TK.CHAR, '>')
                fun f(tp: Type, n: Int): Boolean {
                    return when (tp) {
                        is Type.Ptr -> tp.pln.let {
                            f(it, n) || (it is Type.Rec && n == it.tk_.up)
                        }
                        //is Type.Rec   -> return n <= tp.tk_.up
                        is Type.Tuple -> tp.vec.any { f(it, n) }
                        is Type.Union -> tp.vec.any { f(it, n + 1) }
                        else -> false
                    }
                }

                val vec = tps.toTypedArray()
                val isrec = vec.any { f(it, 1) }
                Type.Union(tk0, isrec, vec)
            }
        }
        all.accept(TK.FUNC) || all.accept(TK.TASK) || (tasks && all.accept(TK.TASKS)) -> {
            val tk0 = all.tk0 as Tk.Key

            // closure
            val clo = if (all.accept(TK.XSCPCST) || all.accept(TK.XSCPVAR)) {
                val tk = all.tk0 as Tk.Scp1
                all.accept_err(TK.ARROW)
                tk
            } else {
                null
            }

            // scopes
            val scp1s = if (!all.accept(TK.ATBRACK)) null else {
                val ret = mutableListOf<Tk.Scp1>()
                while (all.accept(TK.XSCPVAR)) {
                    ret.add(all.tk0 as Tk.Scp1)
                    if (!all.accept(TK.CHAR, ',')) {
                        break
                    }
                }
                all.accept_err(TK.CHAR, ']')
                all.accept_err(TK.ARROW)
                ret.toTypedArray()
            }

            // input & pub & output
            val inp = xparser_type(all)
            val pub = if (tk0.enu != TK.FUNC) {
                all.accept_err(TK.ARROW)
                xparser_type(all)
            } else null
            all.accept_err(TK.ARROW)
            val out = xparser_type(all) // right associative

            Type.Func(tk0, Pair(clo,scp1s), null, inp, pub, out)
        }
        all.accept(TK.ACTIVE) -> {
            val tk0 = all.tk0 as Tk.Key
            all.check(TK.TASKS) || all.check_err(TK.TASK)
            val task = xparser_type(all, true)
            assert(task is Type.Func && task.tk.enu!=TK.FUNC)
            if (task.tk.enu == TK.TASKS) {
                Type.Spawns(tk0, task as Type.Func)
            } else {
                Type.Spawn(tk0, task as Type.Func)
            }
        }
        else -> {
            all.err_expected("type")
            error("unreachable")
        }
    }
}

fun xparser_expr (all: All): Expr {
    var e = when {
        all.accept(TK.UNIT) -> Expr.Unit(all.tk0 as Tk.Sym)
        all.accept(TK.XVAR) -> Expr.Var(all.tk0 as Tk.Str)
        all.accept(TK.XNAT) -> {
            val tk0 = all.tk0 as Tk.Nat
            val tp = if (!all.accept(TK.CHAR, ':')) null else {
                xparser_type(all)
            }
            Expr.Nat(tk0, tp)
        }
        all.accept(TK.CHAR, '/') -> {
            val tk0 = all.tk0 as Tk.Chr
            val e = xparser_expr(all)
            all.assert_tk(
                all.tk0,
                e is Expr.Nat || e is Expr.Var || e is Expr.TDisc || e is Expr.Dnref || e is Expr.Upref
            ) {
                "unexpected operand to `/´"
            }
            Expr.Upref(tk0, e)
        }
        all.accept(TK.CHAR, '(') -> {
            val e = xparser_expr(all)
            all.accept_err(TK.CHAR, ')')
            e
        }
        all.accept(TK.CHAR, '[') -> {
            val tk0 = all.tk0 as Tk.Chr
            val e = xparser_expr(all)
            val es = arrayListOf(e)
            while (true) {
                if (!all.accept(TK.CHAR, ',')) {
                    break
                }
                val e2 = xparser_expr(all)
                es.add(e2)
            }
            all.accept_err(TK.CHAR, ']')
            Expr.TCons(tk0, es.toTypedArray())
        }
        all.accept(TK.CHAR, '<') -> {
            all.accept_err(TK.CHAR, '.')
            all.accept_err(TK.XNUM)
            val tk0 = all.tk0 as Tk.Num
            val cons = when {
                (tk0.num == 0) -> null
                all.check(TK.CHAR,'>') -> Expr.Unit(Tk.Sym(TK.UNIT, all.tk1.lin, all.tk1.col, "()"))
                else -> xparser_expr(all)
            }
            all.accept_err(TK.CHAR, '>')
            val tp = if (!all.accept(TK.CHAR, ':')) null else xparser_type(all)
            if (tk0.num == 0) {
                if (tp != null) {
                    All_assert_tk(tp.tk, (tp is Type.Ptr && tp.pln is Type.Union)) { "invalid type : expected pointer to union"}
                }
                Expr.UNull(tk0, tp)
            } else {
                if (tp != null) {
                    All_assert_tk(tp.tk, tp is Type.Union) { "invalid type : expected union" }
                }
                Expr.UCons(tk0, tp, cons!!)
            }
        }
        all.accept(TK.NEW) -> {
            val tk0 = all.tk0
            val e = xparser_expr(all)
            all.assert_tk(tk0, e is Expr.UCons && e.tk_.num!=0) {
                //"invalid `new` : unexpected <.0>"
                "invalid `new` : expected constructor"
            }
            val scp = if (all.accept(TK.CHAR, ':')) {
                all.accept(TK.XSCPCST) || all.accept_err(TK.XSCPVAR)
                all.tk0 as Tk.Scp1
            } else {
                null
            }
            Expr.New(tk0 as Tk.Key, scp, null, e as Expr.UCons)
        }
        all.check(TK.TASK) || all.check(TK.FUNC) -> {
            val tk = all.tk1 as Tk.Key
            val tp = xparser_type(all) as Type.Func

            val ups: Array<Tk.Str> = if (!all.accept(TK.CHAR,'[')) emptyArray() else {
                val ret = mutableListOf<Tk.Str>()
                while (all.accept(TK.XVAR)) {
                    ret.add(all.tk0 as Tk.Str)
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                }
                all.accept_err(TK.CHAR,']')
                ret.toTypedArray()
            }

            val block = xparser_block(all)
            Expr.Func(tk, tp, ups, block)
        }
        else -> {
            all.err_expected("expression")
            error("unreachable")
        }
    }

    // one!1\.2?1
    while (all.accept(TK.CHAR,'\\') || all.accept(TK.CHAR, '.') || all.accept(TK.CHAR, '!') || all.accept(TK.CHAR, '?')) {
        val chr = all.tk0 as Tk.Chr
        e = if (chr.chr == '\\') {
            all.assert_tk(all.tk0, e is Expr.Nat || e is Expr.Var || e is Expr.TDisc || e is Expr.UDisc || e is Expr.Dnref || e is Expr.Upref || e is Expr.Call) {
                "unexpected operand to `\\´"
            }
            Expr.Dnref(chr, e)
        } else {
            val ok = when {
                (chr.chr != '.') -> false
                all.accept(TK.XVAR) -> {
                    val tk = all.tk0 as Tk.Str
                    all.assert_tk(tk, tk.str=="pub") {
                        "unexpected \"${tk.str}\""
                    }
                    true
                }
                else -> false
            }
            if (!ok) {
                all.accept_err(TK.XNUM)
            }
            val num = if (ok) null else (all.tk0 as Tk.Num)
            all.assert_tk(all.tk0, e !is Expr.TCons && e !is Expr.UCons && e !is Expr.UNull) {
                "invalid discriminator : unexpected constructor"
            }
            if (chr.chr=='?' || chr.chr=='!') {
                All_assert_tk(all.tk0, num!!.num!=0 || e is Expr.Dnref) {
                    "invalid discriminator : union cannot be <.0>"
                }
            }
            when {
                (chr.chr == '?') -> Expr.UPred(num!!, e)
                (chr.chr == '!') -> Expr.UDisc(num!!, e)
                (chr.chr == '.') -> {
                    if (all.tk0.enu == TK.XVAR) {
                        Expr.Pub(all.tk0 as Tk.Str, e)
                    } else {
                        Expr.TDisc(num!!, e)
                    }
                }
                else -> error("impossible case")
            }
        }
    }

    // call

    if (all.checkExpr() || all.check(TK.ATBRACK)) {
        val iscps = mutableListOf<Tk.Scp1>()
        if (all.accept(TK.ATBRACK)) {
            while (all.accept(TK.XSCPCST) || all.accept(TK.XSCPVAR)) {
                val tk = all.tk0 as Tk.Scp1
                iscps.add(tk)
                if (!all.accept(TK.CHAR, ',')) {
                    break
                }
            }
            all.accept_err(TK.CHAR, ']')
        }
        val arg = xparser_expr(all)
        val oscp = if (!all.accept(TK.CHAR, ':')) null else {
            all.accept(TK.XSCPCST) || all.accept_err(TK.XSCPVAR)
            all.tk0 as Tk.Scp1
        }
        e = Expr.Call(e.tk, e, arg, Pair(iscps.toTypedArray(),oscp), null)
    }
    return e
}

fun xparser_attr (all: All): Attr {
    var e = when {
        all.accept(TK.XVAR) -> Attr.Var(all.tk0 as Tk.Str)
        all.accept(TK.XNAT) -> {
            all.accept_err(TK.CHAR, ':')
            val tp = xparser_type(all)
            Attr.Nat(all.tk0 as Tk.Nat, tp)
        }
        all.accept(TK.CHAR,'\\') -> {
            val tk0 = all.tk0 as Tk.Chr
            val e = xparser_attr(all)
            all.assert_tk(all.tk0, e is Attr.Nat || e is Attr.Var || e is Attr.TDisc || e is Attr.UDisc || e is Attr.Dnref) {
                "unexpected operand to `\\´"
            }
            Attr.Dnref(tk0,e)
        }
        all.accept(TK.CHAR, '(') -> {
            val e = xparser_attr(all)
            all.accept_err(TK.CHAR, ')')
            e
        }
        else -> {
            all.err_expected("expression")
            error("unreachable")
        }
    }

    // one.1!\.2.1?
    while (all.accept(TK.CHAR, '\\') || all.accept(TK.CHAR, '.') || all.accept(TK.CHAR, '!')) {
        val chr = all.tk0 as Tk.Chr
        e = if (chr.chr == '\\') {
            all.assert_tk(
                all.tk0,
                e is Attr.Nat || e is Attr.Var || e is Attr.TDisc || e is Attr.UDisc || e is Attr.Dnref
            ) {
                "unexpected operand to `\\´"
            }
            Attr.Dnref(chr, e)
        } else {
            val ok = when {
                (chr.chr != '.') -> false
                all.accept(TK.XVAR) -> {
                    val tk = all.tk0 as Tk.Str
                    all.assert_tk(tk, tk.str=="pub") {
                        "unexpected \"${tk.str}\""
                    }
                    true
                }
                else -> false
            }
            if (!ok) {
                all.accept_err(TK.XNUM)
            }
            val num = if (ok) null else (all.tk0 as Tk.Num)
            when {
                (chr.chr == '!') -> Attr.UDisc(num!!, e)
                (chr.chr == '.') -> {
                    if (all.tk0.enu == TK.XVAR) {
                        Attr.Pub(all.tk0 as Tk.Str, e)
                    } else {
                        Attr.TDisc(num!!, e)
                    }
                }
                else -> error("impossible case")
            }
        }
    }
    return e
}

fun xparser_block (all: All): Stmt.Block {
    val iscatch = (all.tk0.enu == TK.CATCH)
    all.accept_err(TK.CHAR,'{')
    val tk0 = all.tk0 as Tk.Chr
    val scp = all.accept(TK.XSCPCST).let { if (it) all.tk0 as Tk.Scp1 else null }
    val ret = xparser_stmts(all, Pair(TK.CHAR,'}'))
    all.accept_err(TK.CHAR,'}')
    return Stmt.Block(tk0, iscatch, scp, ret)
}

fun xparser_stmt (all: All): Stmt {
    return when {
        all.accept(TK.VAR) -> {
            all.accept_err(TK.XVAR)
            val tk_id = all.tk0 as Tk.Str
            val tp = if (!all.accept(TK.CHAR,':')) null else {
                xparser_type(all)
            }
            if (all.accept(TK.CHAR,'=')) {
                val tk = all.tk0 as Tk.Chr
                val dst = Expr.Var(tk_id)
                val src = when {
                    all.check(TK.INPUT) -> {
                        all.accept(TK.INPUT)
                        val tk = all.tk0 as Tk.Key
                        all.accept_err(TK.XVAR)
                        val lib = (all.tk0 as Tk.Str)
                        val arg = xparser_expr(all)
                        val tp = if (!all.accept(TK.CHAR, ':')) null else xparser_type(all)
                        Stmt.Input(tk, tp, dst, lib, arg)
                    }
                    all.check(TK.SPAWN) -> {
                        all.accept(TK.SPAWN)
                        val tk = all.tk0 as Tk.Key
                        val e = xparser_expr(all)
                        All_assert_tk(tk, e is Expr.Call) { "expected call expression" }
                        Stmt.SSpawn(tk, dst, e as Expr.Call)
                    }
                    else -> {
                        val src = xparser_expr(all)
                        Stmt.Set(tk, dst, src)
                    }
                }
                Stmt.Seq(tk_id, Stmt.Var(tk_id,tp), src)
            } else {
                if (tp == null) {
                    all.err_expected("type declaration")
                }
                Stmt.Var(tk_id, tp)
            }
        }
        all.accept(TK.SET) -> {
            val dst = xparser_attr(all).toExpr()
            all.accept_err(TK.CHAR,'=')
            val tk0 = all.tk0 as Tk.Chr
            when {
                all.check(TK.INPUT) -> {
                    all.accept(TK.INPUT)
                    val tk = all.tk0 as Tk.Key
                    all.accept_err(TK.XVAR)
                    val lib = (all.tk0 as Tk.Str)
                    val arg = xparser_expr(all)
                    all.accept_err(TK.CHAR, ':')
                    val tp = xparser_type(all)
                    Stmt.Input(tk, tp, dst, lib, arg)
                }
                all.check(TK.SPAWN) -> {
                    all.accept(TK.SPAWN)
                    val tk = all.tk0 as Tk.Key
                    val e = xparser_expr(all)
                    All_assert_tk(tk, e is Expr.Call) { "expected call expression" }
                    Stmt.SSpawn(tk, dst, e as Expr.Call)
                }
                else -> {
                    val src = xparser_expr(all)
                    Stmt.Set(tk0, dst, src)
                }
            }
        }
        all.accept(TK.NATIVE) -> {
            all.accept_err(TK.XNAT)
            Stmt.Native(all.tk0 as Tk.Nat)
        }
        all.accept(TK.CALL) -> {
            val tk0 = all.tk0 as Tk.Key
            val e = xparser_expr(all)
            All_assert_tk(tk0, e is Expr.Call) { "expected call expression" }
            Stmt.SCall(tk0, e as Expr.Call)
        }
        all.accept(TK.SPAWN) -> {
            val tk0 = all.tk0 as Tk.Key
            val e = xparser_expr(all)
            All_assert_tk(tk0, e is Expr.Call) { "expected call expression" }
            all.accept_err(TK.IN)
            val tsks = xparser_expr(all)
            Stmt.DSpawn(tk0, tsks, e as Expr.Call)
        }
        all.accept(TK.AWAIT) -> {
            val tk0 = all.tk0 as Tk.Key
            val e = xparser_expr(all)
            Stmt.Await(tk0, e)
        }
        all.accept(TK.BCAST) -> {
            val tk0 = all.tk0 as Tk.Key
            val scp = if (all.accept(TK.XSCPCST) || all.accept(TK.XSCPVAR)) {
                all.tk0 as Tk.Scp1
            } else {
                Tk.Scp1(TK.XSCPCST, tk0.lin, tk0.col, "GLOBAL", null)
            }
            val e = xparser_expr(all)
            Stmt.Bcast(tk0, scp, e)
        }
        all.accept(TK.THROW) -> {
            Stmt.Throw(all.tk0 as Tk.Key)
        }
        all.accept(TK.INPUT) -> {
            val tk = all.tk0 as Tk.Key
            all.accept_err(TK.XVAR)
            val lib = (all.tk0 as Tk.Str)
            val arg = xparser_expr(all)
            val tp = if (!all.accept(TK.CHAR, ':')) null else xparser_type(all)
            Stmt.Input(tk, tp, null, lib, arg)
        }
        all.accept(TK.OUTPUT) -> {
            val tk = all.tk0 as Tk.Key
            all.accept_err(TK.XVAR)
            val lib = (all.tk0 as Tk.Str)
            val arg = xparser_expr(all)
            Stmt.Output(tk, lib, arg)
        }
        all.accept(TK.IF) -> {
            val tk0 = all.tk0 as Tk.Key
            val tst = xparser_expr(all)
            val true_ = xparser_block(all)
            val false_ = if (all.accept(TK.ELSE)) {
                xparser_block(all)
            } else {
                Stmt.Block(Tk.Chr(TK.CHAR,all.tk1.lin,all.tk1.col,'{'), false,null, Stmt.Nop(all.tk0))
            }
            Stmt.If(tk0, tst, true_, false_)
        }
        all.accept(TK.RETURN) -> {
            val tk0 = all.tk0 as Tk.Key
            val e = if (all.checkExpr()) {
                xparser_expr(all)
            } else {
                Expr.Unit(Tk.Sym(TK.UNIT,all.tk1.lin,all.tk1.col,"()"))
            }
            Stmt.Seq (tk0,
                Stmt.Set (
                    Tk.Chr(TK.CHAR,tk0.lin,tk0.col,'='),
                    Expr.Var(Tk.Str(TK.XVAR,tk0.lin,tk0.col,"ret")),
                    e
                ),
                Stmt.Return(tk0)
            )
        }
        all.accept(TK.LOOP) -> {
            val tk0 = all.tk0 as Tk.Key
            if (all.check(TK.CHAR, '{')) {
                val block = xparser_block(all)
                Stmt.Loop(tk0, block)
            } else {
                val i = xparser_expr(all)
                All_assert_tk(all.tk0, i is Expr.Var) { "expected variable expression" }
                all.accept_err(TK.IN)
                val tsks = xparser_expr(all)
                val block = xparser_block(all)
                Stmt.DLoop(tk0, i as Expr.Var, tsks, block)
            }
        }
        all.accept(TK.BREAK) -> Stmt.Break(all.tk0 as Tk.Key)
        all.accept(TK.CATCH) || all.check(TK.CHAR,'{') -> xparser_block(all)
        else -> {
            all.err_expected("statement")
            error("unreachable")
        }
    }
}

fun xparser_stmts (all: All, opt: Pair<TK,Char?>): Stmt {
    fun enseq (s1: Stmt, s2: Stmt): Stmt {
        return when {
            (s1 is Stmt.Nop) -> s2
            (s2 is Stmt.Nop) -> s1
            else -> Stmt.Seq(s1.tk, s1, s2)
        }
    }
    var ret: Stmt = Stmt.Nop(all.tk0)
    while (true) {
        all.accept(TK.CHAR, ';')
        val isend = all.check(TK.CHAR,'}') || all.check(TK.EOF)
        if (!isend) {
            val s = xparser_stmt(all)
            ret = enseq(ret,s)
        } else {
            break
        }
    }
    return ret
}
