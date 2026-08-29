import java.nio.file.Path;
import java.util.List;

public class Milestone1Demo {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java Milestone1Demo <translated.nft> <ruleA-index> <ruleB-index>");
            System.exit(1);
        }

        try {
            Path input = Path.of(args[0]);
            int aIndex = Integer.parseInt(args[1]);
            int bIndex = Integer.parseInt(args[2]);

            NftParser parser = new NftParser();
            List<Rule> rules = parser.parseFile(input);

            Rule a = findRule(rules, aIndex);
            Rule b = findRule(rules, bIndex);

            System.out.println("A:");
            System.out.println(a.toDebugString());
            System.out.println();

            System.out.println("B:");
            System.out.println(b.toDebugString());
            System.out.println();

            printFieldRelations(a, b);

            Relation overall = RelationEngine.compareRules(a, b);
            System.out.println("overall relation: " + overall);

            System.out.println("A metadata: " + RuleMetadata.from(a));
            System.out.println("B metadata: " + RuleMetadata.from(b));
            System.out.println("state dependencies: "
                    + DependencyAnalyzer.findStateDependencies(a, b));

            SwapAnalyzer.Decision decision = SwapAnalyzer.canSwap(a, b);
            System.out.println(decision);

        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static Rule findRule(List<Rule> rules, int index) {
        for (Rule rule : rules) {
            if (rule.getIndex() == index) {
                return rule;
            }
        }
        throw new IllegalArgumentException("No rule with index " + index);
    }

    private static void printFieldRelations(Rule a, Rule b) {
        System.out.println("field relations:");
        System.out.println("  protocol: "
                + RelationEngine.compareProtocol(a.getProtocol(), b.getProtocol()));
        System.out.println("  srcIp:    "
                + RelationEngine.compareIp(a.getSourceIp(), b.getSourceIp()));
        System.out.println("  dstIp:    "
                + RelationEngine.compareIp(a.getDestinationIp(), b.getDestinationIp()));
        System.out.println("  srcPort:  "
                + RelationEngine.comparePort(a.getSourcePort(), b.getSourcePort()));
        System.out.println("  dstPort:  "
                + RelationEngine.comparePort(a.getDestinationPort(), b.getDestinationPort()));
        System.out.println("  markRead: "
                + RelationEngine.compareMark(a.getMarkRead(), b.getMarkRead()));
    }
}
