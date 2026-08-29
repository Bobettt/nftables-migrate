import java.util.ArrayList;
import java.util.List;

public final class SyntheticReorderingBenchmark {

    private SyntheticReorderingBenchmark() {
    }

    public static void main(String[] args) {
        System.out.println("scenario,rules,candidates,swaps,moved,blocked,time_ns");
        for (int size : new int[] {100, 1000, 10000}) {
            run("candidate-rich", candidateRich(size));
        }
        run("barrier-heavy", barrierHeavy(1000));
    }

    private static void run(String scenario, List<Rule> rules) {
        long start = System.nanoTime();
        SafeReorderOptimizer.OptimizationResult result =
                new SafeReorderOptimizer().optimize(rules);
        long elapsed = System.nanoTime() - start;

        System.out.printf("%s,%d,%d,%d,%d,%d,%d%n",
                scenario,
                rules.size(),
                result.getMergeCandidatesConsidered(),
                result.getAdjacentSwaps(),
                result.getMovedRules(),
                result.getBlockedMoves(),
                elapsed);
    }

    private static List<Rule> candidateRich(int size) {
        List<Rule> rules = new ArrayList<>(size);
        int index = 1;
        int host = 1;
        while (rules.size() < size) {
            rules.add(tcpAccept(index++, host++));
            if (rules.size() < size) {
                rules.add(udpDrop(index++));
            }
            if (rules.size() < size) {
                rules.add(tcpAccept(index++, host++));
            }
        }
        return rules;
    }

    private static List<Rule> barrierHeavy(int size) {
        List<Rule> rules = new ArrayList<>(size);
        int index = 1;
        int host = 1;
        while (rules.size() < size) {
            rules.add(tcpAccept(index++, host++));
            if (rules.size() < size) {
                Rule log = new Rule(index++, "synthetic LOG barrier");
                log.setProtocol(Rule.Protocol.TCP);
                log.setDestinationPort(Rule.PortRange.parse("1-65535"));
                log.setEffect(Rule.Effect.LOG);
                rules.add(log);
            }
            if (rules.size() < size) {
                rules.add(tcpAccept(index++, host++));
            }
        }
        return rules;
    }

    private static Rule tcpAccept(int index, int hostNumber) {
        int third = (hostNumber / 254) % 256;
        int fourth = (hostNumber % 254) + 1;
        Rule rule = new Rule(index, "synthetic TCP ACCEPT " + index);
        rule.setProtocol(Rule.Protocol.TCP);
        rule.setSourceIp(Rule.Ipv4Range.parse("10.0." + third + "." + fourth));
        rule.setDestinationPort(Rule.PortRange.parse("443"));
        rule.setEffect(Rule.Effect.ACCEPT);
        return rule;
    }

    private static Rule udpDrop(int index) {
        Rule rule = new Rule(index, "synthetic UDP DROP " + index);
        rule.setProtocol(Rule.Protocol.UDP);
        rule.setDestinationPort(Rule.PortRange.parse("53"));
        rule.setEffect(Rule.Effect.DROP);
        return rule;
    }
}
