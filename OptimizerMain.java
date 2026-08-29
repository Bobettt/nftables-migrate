import java.nio.file.Path;

public final class OptimizerMain {
    private static final int DEBUG_MOVE_LIMIT = 5;

    private OptimizerMain() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java OptimizerMain input.nft optimized.nft");
            System.exit(1);
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);

        try {
            SafeReorderOptimizer.OptimizationResult result =
                    NftRulesetRewriter.rewrite(input, output);

            System.out.printf("Parsed rules:          %d%n", result.getOptimizedRules().size());
            System.out.printf("Merge candidates:      %d%n", result.getMergeCandidatesConsidered());
            System.out.printf("Adjacent swaps:        %d%n", result.getAdjacentSwaps());
            System.out.printf("Rules moved:           %d%n", result.getMovedRules());
            System.out.printf("Blocked moves:         %d%n", result.getBlockedMoves());
            System.out.printf("Output:                %s%n", output);

            int shown = 0;
            for (SafeReorderOptimizer.Move move : result.getMoves()) {
                if (shown >= DEBUG_MOVE_LIMIT) {
                    break;
                }
                System.out.printf(
                        "moved original Rule #%d from position %d to position %d%n",
                        move.getMovingRuleIndex(), move.getFromPosition(), move.getToPosition());
                System.out.printf(
                        "because it is a merge candidate with Rule #%d%n",
                        move.getAnchorRuleIndex());
                for (SafeReorderOptimizer.Crossing crossing : move.getCrossings()) {
                    System.out.printf(
                            "crossed Rule #%d: %s -> safe%n",
                            crossing.getCrossedRuleIndex(), crossing.getRelation());
                }
                shown++;
            }
        } catch (Exception e) {
            System.err.println("Optimization failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }
}
