fun Expr.tostr (): String {
    return when (this) {
        is Expr.Unit  -> "()"
        is Expr.Var   -> this.tk_.str
        is Expr.Nat   -> "(" + this.tk_.toce() + ": " + this.wtype!!.tostr() + ")"
        is Expr.Upref -> "(/" + this.pln.tostr() + ")"
        is Expr.Dnref -> "(" + this.ptr.tostr() + "\\)"
        is Expr.TCons -> "[" + this.arg.map { it.tostr() }.joinToString(",") + "]"
        is Expr.UCons -> "<." + this.tk_.num + " " + this.arg.tostr() + ">: " + this.wtype!!.tostr()
        is Expr.UNull -> "<.0>: " + this.wtype!!.tostr()
        is Expr.TDisc -> "(" + this.tup.tostr() + "." + this.tk_.num + ")"
        is Expr.Pub   -> "(" + this.tsk.tostr() + ".pub)"
        is Expr.UDisc -> "(" + this.uni.tostr() + "!" + this.tk_.num + ")"
        is Expr.UPred -> "(" + this.uni.tostr() + "?" + this.tk_.num + ")"
        is Expr.New   -> "(new " + this.arg.tostr() + ": " + (this.xscp1?.tostr() ?: "") + ")"
        is Expr.Call  -> {
            val inps = " @[" + this.xscp1s.first!!.map { it.tostr() }.joinToString(",") + "]"
            val out  = this.xscp1s.second.let { if (it == null) "" else ": "+it.tostr() }
            "(" + this.f.tostr() + inps + " " + this.arg.tostr() + out + ")"
        }
        is Expr.Func  -> this.type.tostr() + " " + (if (this.ups.size==0) "" else "["+this.ups.map { it.str }.joinToString(",")+"] ") + this.block.tostr()
    }
}

fun Stmt.tostr (): String {
    return when (this) {
        is Stmt.Nop   -> "\n"
        is Stmt.Native   -> "native " + this.tk_.toce() + "\n"
        is Stmt.Var   -> "var " + this.tk_.str + ": " + this.xtype!!.tostr() + "\n"
        is Stmt.Set -> "set " + this.dst.tostr() + " = " + this.src.tostr() + "\n"
        is Stmt.Break -> "break\n"
        is Stmt.Return   -> "return\n"
        is Stmt.Seq   -> this.s1.tostr() + this.s2.tostr()
        is Stmt.SCall -> "call " + this.e.tostr() + "\n"
        is Stmt.Input   -> (if (this.dst==null) "" else "set "+this.dst.tostr()+" = ") + "input " + this.lib.str + " " + this.arg.tostr() + ": " + this.xtype!!.tostr() + "\n"
        is Stmt.Output   -> "output " + this.lib.str + " " + this.arg.tostr() + "\n"
        is Stmt.If    -> "if " + this.tst.tostr() + "{\n" + this.true_.tostr() + "} else {\n" + this.false_.tostr() + "}\n"
        is Stmt.Loop  -> "loop " + this.block.tostr()
        is Stmt.Block -> "{" + (this.xscp1?.tostr() ?: "").let { if (it == "") "" else " "+it } + "\n" + this.body.tostr() + "}\n"
        is Stmt.SSpawn -> "set " + this.dst.tostr() + " = spawn " + this.call.tostr() + "\n"
        is Stmt.DSpawn -> "spawn " + this.call + " in " + this.dst.tostr() + "\n"
        is Stmt.Await  -> "await " + this.e.tostr() + "\n"
        is Stmt.Bcast  -> "bcast " + this.e.tostr() + "\n"
        is Stmt.Throw  -> "throw\n"
        is Stmt.DLoop  -> "loop " + this.i.tostr() + " in " + this.tsks.tostr() + this.block.tostr()
    }
}