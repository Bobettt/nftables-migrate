import java.util.List;

public final class SwapAnalyzer {

    public static final class Decision {
        private final boolean canSwap;
        private final Relation relation;
        private final String reason;

        public Decision(boolean canSwap, Relation relation, String reason) {
            this.canSwap = canSwap;
            this.relation = relation;
            this.reason = reason;
        }

        public boolean canSwap() {
            return canSwap;
        }

        public Relation getRelation() {
            return relation;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return "canSwap=" + canSwap
                    + ", relation=" + relation
                    + ", reason=" + reason;
        }
    }

    private SwapAnalyzer() {
    }

    public static Decision canSwap(Rule a, Rule b) {
        if (a == null || b == null) {
            return new Decision(false, Relation.UNKNOWN,
                    "One of the rules is null.");
        }

        RuleMetadata aMeta = RuleMetadata.from(a);
        RuleMetadata bMeta = RuleMetadata.from(b);

        if (aMeta.isBarrier()) {
            return new Decision(false, Relation.UNKNOWN,
                    "Rule A is unsupported/UNKNOWN and is therefore a barrier.");
        }

        if (bMeta.isBarrier()) {
            return new Decision(false, Relation.UNKNOWN,
                    "Rule B is unsupported/UNKNOWN and is therefore a barrier.");
        }

        Relation relation = RelationEngine.compareRules(a, b);
        if (relation == Relation.UNKNOWN) {
            return new Decision(false, relation,
                    "Match-space relation is UNKNOWN, so safety is not proven.");
        }

        List<DependencyAnalyzer.Dependency> dependencies =
                DependencyAnalyzer.findStateDependencies(a, b);

        if (!dependencies.isEmpty()) {
            return new Decision(false, relation,
                    "MARK state dependency: " + dependencies + ".");
        }

        // Za sada dozvoljavam samo slucaj koji mogu lako da dokazem.
        if (relation == Relation.DISJOINT) {
            return new Decision(true, relation,
                    "Rules are DISJOINT and there is no MARK dependency or barrier.");
        }

        if (aMeta.hasLogSideEffect() || bMeta.hasLogSideEffect()) {
            return new Decision(false, relation,
                    "Rules overlap and LOG is an observable side effect; order/reachability may change.");
        }

        if (aMeta.isTerminal() && bMeta.isTerminal()
                && a.getEffect() != b.getEffect()) {
            return new Decision(false, relation,
                    "Rules overlap and have different terminal verdicts ("
                            + a.getEffect() + " vs " + b.getEffect() + ").");
        }

        if (aMeta.isTerminal() || bMeta.isTerminal()) {
            return new Decision(false, relation,
                    "Rules overlap and a terminal verdict can change reachability after reordering.");
        }

        // Ako se preklapaju, ostavljam originalni redosled.
        return new Decision(false, relation,
                "Rules overlap; this MVP has no proof that their effects commute.");
    }
}
