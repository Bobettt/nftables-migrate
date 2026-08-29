public final class MergeCandidateAnalyzer {

    public static final class Decision {
        private final boolean candidate;
        private final String differingField;

        private Decision(boolean candidate, String differingField) {
            this.candidate = candidate;
            this.differingField = differingField;
        }

        public boolean isCandidate() {
            return candidate;
        }

        public String getDifferingField() {
            return differingField;
        }
    }

    private MergeCandidateAnalyzer() {
    }

    public static Decision analyze(Rule a, Rule b) {
        if (a == null || b == null || !a.isSupported() || !b.isSupported()) {
            return new Decision(false, null);
        }

        if (a.getEffect() != b.getEffect()
                || (a.getEffect() != Rule.Effect.ACCEPT
                    && a.getEffect() != Rule.Effect.DROP)
                || a.getMarkWrite() != null
                || b.getMarkWrite() != null) {
            return new Decision(false, null);
        }

        if (RelationEngine.compareProtocol(a.getProtocol(), b.getProtocol()) != Relation.EQUAL
                || RelationEngine.compareMark(a.getMarkRead(), b.getMarkRead()) != Relation.EQUAL) {
            return new Decision(false, null);
        }

        String[] names = {"source IP", "destination IP", "source port", "destination port"};
        Relation[] relations = {
                RelationEngine.compareIp(a.getSourceIp(), b.getSourceIp()),
                RelationEngine.compareIp(a.getDestinationIp(), b.getDestinationIp()),
                RelationEngine.comparePort(a.getSourcePort(), b.getSourcePort()),
                RelationEngine.comparePort(a.getDestinationPort(), b.getDestinationPort())
        };

        String differingField = null;
        int different = 0;
        for (int i = 0; i < relations.length; i++) {
            if (relations[i] == Relation.EQUAL) {
                continue;
            }
            if (relations[i] != Relation.DISJOINT) {
                return new Decision(false, null);
            }
            differingField = names[i];
            different++;
        }

        return new Decision(different == 1, different == 1 ? differingField : null);
    }

    public static boolean isMergeCandidate(Rule a, Rule b) {
        return analyze(a, b).isCandidate();
    }
}
