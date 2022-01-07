fun Stmt.infer () {
    fun fs (s: Stmt) {
        if (s is Stmt.Var && s.type == null) {
            val up = AUX.ups[s] as Stmt.Seq
            assert(up.s1 == s)
            val set = up.s2 as Stmt.Set
            //AUX.tps[s] = AUX.tps[set.src]!!
        }
    }
    this.visit(::fs , null, null)
}
