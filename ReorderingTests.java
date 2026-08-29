import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ReorderingTests {
    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        testActualSafeOrderChange();
        testLogBlocksMovement();
        testMarkDependencyBlocksMovement();
        testUnsupportedBarrier();
        testNoCandidateKeepsOrder();
        testMultipleSafeAdjacentSwaps();
        testEndToEndRewrite();
        System.out.println("All reordering tests passed: " + passed);
    }

    private static void testActualSafeOrderChange() {
        Rule a = tcpAccept(1, "10.0.0.1");
        Rule x = rule(2, Rule.Protocol.UDP, Rule.Effect.DROP);
        x.setDestinationPort(Rule.PortRange.parse("53"));
        Rule b = tcpAccept(3, "10.0.0.2");

        SafeReorderOptimizer.OptimizationResult result = optimize(a, x, b);
        expectOrder(result, a, b, x);
        expect(result.getAdjacentSwaps() >= 1, true, "safe order change swaps");
    }

    private static void testLogBlocksMovement() {
        Rule a = tcpAccept(1, "10.0.0.1");
        Rule x = rule(2, Rule.Protocol.TCP, Rule.Effect.LOG);
        x.setDestinationPort(Rule.PortRange.parse("1-65535"));
        Rule b = tcpAccept(3, "10.0.0.2");

        SafeReorderOptimizer.OptimizationResult result = optimize(a, x, b);
        expectOrder(result, a, x, b);
        expect(result.getBlockedMoves(), 1, "LOG blocked move count");
    }

    private static void testMarkDependencyBlocksMovement() {
        Rule a = tcpAccept(1, "10.0.0.1");
        a.setMarkRead(5L);
        Rule x = rule(2, Rule.Protocol.UDP, Rule.Effect.MARK_WRITE);
        x.setMarkWrite(7L);
        Rule b = tcpAccept(3, "10.0.0.2");
        b.setMarkRead(5L);

        SafeReorderOptimizer.OptimizationResult result = optimize(a, x, b);
        expectOrder(result, a, x, b);
        expect(result.getBlockedMoves(), 1, "MARK blocked move count");
    }

    private static void testUnsupportedBarrier() {
        Rule a = tcpAccept(1, "10.0.0.1");
        Rule barrier = rule(2, Rule.Protocol.UDP, Rule.Effect.DROP);
        barrier.markUnsupported("test barrier");
        Rule b = tcpAccept(3, "10.0.0.2");

        SafeReorderOptimizer.OptimizationResult result = optimize(a, barrier, b);
        expectOrder(result, a, barrier, b);
        expect(result.getBlockedMoves(), 1, "UNKNOWN blocked move count");
    }

    private static void testNoCandidateKeepsOrder() {
        Rule a = tcpAccept(1, "10.0.0.1");
        Rule x = rule(2, Rule.Protocol.UDP, Rule.Effect.DROP);
        Rule b = rule(3, Rule.Protocol.TCP, Rule.Effect.DROP);
        b.setSourceIp(Rule.Ipv4Range.parse("10.0.0.2"));
        b.setDestinationPort(Rule.PortRange.parse("443"));

        SafeReorderOptimizer.OptimizationResult result = optimize(a, x, b);
        expectOrder(result, a, x, b);
        expect(result.getMergeCandidatesConsidered(), 0, "no candidates");
    }

    private static void testMultipleSafeAdjacentSwaps() {
        Rule a = tcpAccept(1, "10.0.0.1");
        Rule x = rule(2, Rule.Protocol.UDP, Rule.Effect.DROP);
        Rule y = rule(3, Rule.Protocol.ICMP, Rule.Effect.DROP);
        Rule b = tcpAccept(4, "10.0.0.2");

        expect(SwapAnalyzer.canSwap(y, b).canSwap(), true, "first crossing approved");
        expect(SwapAnalyzer.canSwap(x, b).canSwap(), true, "second crossing approved");

        SafeReorderOptimizer.OptimizationResult result = optimize(a, x, y, b);
        expectOrder(result, a, b, x, y);
        expect(result.getAdjacentSwaps(), 2, "multiple adjacent swaps");
    }

    private static void testEndToEndRewrite() throws Exception {
        Path input = Files.createTempFile("reorder-input-", ".nft");
        Path output = Files.createTempFile("reorder-output-", ".nft");
        try {
            List<String> lines = List.of(
                    "add table ip filter",
                    "add chain ip filter INPUT { type filter hook input priority 0; policy accept; }",
                    "add rule ip filter INPUT ip saddr 10.0.0.1 tcp dport 443 counter accept",
                    "add rule ip filter INPUT udp dport 53 counter drop",
                    "add rule ip filter INPUT ip saddr 10.0.0.2 tcp dport 443 counter accept",
                    "# preserved trailer");
            Files.write(input, lines);

            SafeReorderOptimizer.OptimizationResult result =
                    NftRulesetRewriter.rewrite(input, output);
            List<String> rewritten = Files.readAllLines(output);

            expect(rewritten.contains(lines.get(0)), true, "table line preserved");
            expect(rewritten.contains(lines.get(1)), true, "chain line preserved");
            expect(rewritten.contains(lines.get(5)), true, "trailer preserved");
            expect(countRuleLines(rewritten), 3, "rule line count preserved");
            expect(rewritten.get(2).contains("10.0.0.1"), true, "rewrite A first");
            expect(rewritten.get(3).contains("10.0.0.2"), true, "rewrite B second");
            expect(rewritten.get(4).contains("udp dport 53"), true, "rewrite X third");
            expect(result.getAdjacentSwaps(), 1, "rewrite performed swap");
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }

    private static SafeReorderOptimizer.OptimizationResult optimize(Rule... rules) {
        List<Rule> original = new ArrayList<>(List.of(rules));
        SafeReorderOptimizer.OptimizationResult result =
                new SafeReorderOptimizer().optimize(original);
        expect(original, List.of(rules), "caller list not mutated");
        return result;
    }

    private static Rule tcpAccept(int index, String sourceIp) {
        Rule rule = rule(index, Rule.Protocol.TCP, Rule.Effect.ACCEPT);
        rule.setSourceIp(Rule.Ipv4Range.parse(sourceIp));
        rule.setDestinationPort(Rule.PortRange.parse("443"));
        return rule;
    }

    private static Rule rule(int index, Rule.Protocol protocol, Rule.Effect effect) {
        Rule rule = new Rule(index, "test rule " + index);
        rule.setProtocol(protocol);
        rule.setEffect(effect);
        return rule;
    }

    private static int countRuleLines(List<String> lines) {
        int count = 0;
        for (String line : lines) {
            if (line.trim().startsWith("add rule ")) {
                count++;
            }
        }
        return count;
    }

    private static void expectOrder(
            SafeReorderOptimizer.OptimizationResult result, Rule... expected) {
        expect(result.getOptimizedRules(), List.of(expected), "optimized order");
    }

    private static void expect(Object actual, Object expected, String name) {
        if (actual == null ? expected != null : !actual.equals(expected)) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
        passed++;
    }
}
