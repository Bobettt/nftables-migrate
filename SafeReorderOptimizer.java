import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SafeReorderOptimizer {

    public static final class Crossing {
        private final int crossedRuleIndex;
        private final Relation relation;

        private Crossing(int crossedRuleIndex, Relation relation) {
            this.crossedRuleIndex = crossedRuleIndex;
            this.relation = relation;
        }

        public int getCrossedRuleIndex() {
            return crossedRuleIndex;
        }

        public Relation getRelation() {
            return relation;
        }
    }

    public static final class Move {
        private final int movingRuleIndex;
        private final int anchorRuleIndex;
        private final int fromPosition;
        private final int toPosition;
        private final List<Crossing> crossings;

        private Move(
                int movingRuleIndex,
                int anchorRuleIndex,
                int fromPosition,
                int toPosition,
                List<Crossing> crossings) {
            this.movingRuleIndex = movingRuleIndex;
            this.anchorRuleIndex = anchorRuleIndex;
            this.fromPosition = fromPosition;
            this.toPosition = toPosition;
            this.crossings = Collections.unmodifiableList(new ArrayList<>(crossings));
        }

        public int getMovingRuleIndex() { return movingRuleIndex; }
        public int getAnchorRuleIndex() { return anchorRuleIndex; }
        public int getFromPosition() { return fromPosition; }
        public int getToPosition() { return toPosition; }
        public List<Crossing> getCrossings() { return crossings; }
    }

    public static final class OptimizationResult {
        private final List<Rule> optimizedRules;
        private final int adjacentSwaps;
        private final int movedRules;
        private final int mergeCandidatesConsidered;
        private final int blockedMoves;
        private final List<Move> moves;

        private OptimizationResult(
                List<Rule> optimizedRules,
                int adjacentSwaps,
                int movedRules,
                int mergeCandidatesConsidered,
                int blockedMoves,
                List<Move> moves) {
            this.optimizedRules = Collections.unmodifiableList(new ArrayList<>(optimizedRules));
            this.adjacentSwaps = adjacentSwaps;
            this.movedRules = movedRules;
            this.mergeCandidatesConsidered = mergeCandidatesConsidered;
            this.blockedMoves = blockedMoves;
            this.moves = Collections.unmodifiableList(new ArrayList<>(moves));
        }

        public List<Rule> getOptimizedRules() { return optimizedRules; }
        public int getAdjacentSwaps() { return adjacentSwaps; }
        public int getMovedRules() { return movedRules; }
        public int getMergeCandidatesConsidered() { return mergeCandidatesConsidered; }
        public int getBlockedMoves() { return blockedMoves; }
        public List<Move> getMoves() { return moves; }
    }

    public OptimizationResult optimize(List<Rule> input) {
        if (input == null) {
            throw new IllegalArgumentException("Input rule list must not be null");
        }

        List<Rule> rules = new ArrayList<>(input);
        List<Move> moves = new ArrayList<>();
        int swaps = 0;
        int moved = 0;
        int candidates = 0;
        int blocked = 0;

        for (int i = 0; i < rules.size(); i++) {
            Rule anchor = rules.get(i);

            for (int j = i + 2; j < rules.size(); j++) {
                Rule candidate = rules.get(j);
                if (!MergeCandidateAnalyzer.isMergeCandidate(anchor, candidate)) {
                    continue;
                }

                candidates++;
                int originalPosition = j;
                int k = j;
                List<Crossing> crossings = new ArrayList<>();
                boolean wasBlocked = false;

                while (k > i + 1) {
                    Rule left = rules.get(k - 1);
                    Rule moving = rules.get(k);
                    SwapAnalyzer.Decision decision = SwapAnalyzer.canSwap(left, moving);

                    if (!decision.canSwap()) {
                        blocked++;
                        wasBlocked = true;
                        break;
                    }

                    crossings.add(new Crossing(left.getIndex(), decision.getRelation()));
                    Collections.swap(rules, k - 1, k);
                    swaps++;
                    k--;
                }

                if (k != originalPosition) {
                    moved++;
                    moves.add(new Move(
                            candidate.getIndex(), anchor.getIndex(),
                            originalPosition + 1, k + 1, crossings));
                }

                if (!wasBlocked && k == i + 1) {
                    break;
                }

                // Vec odobrene swapove ne vracam ako kandidat stane na pola puta.
                j = Math.max(j, k);
            }
        }

        return new OptimizationResult(rules, swaps, moved, candidates, blocked, moves);
    }
}
