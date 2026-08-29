public class Milestone1Tests {
    private static int passed = 0;

    public static void main(String[] args) {
        testProtocolRelations();
        testIpRelations();
        testPortRelations();
        testMarkRelations();
        testRuleRelations();
        testSafeDisjointSwap();
        testUnsafeOverlappingVerdicts();
        testUnsafeLogOverlap();
        testMarkRaw();
        testMarkWar();
        testMarkWaw();
        testUnknownBarrier();

        System.out.println("All milestone-1 tests passed: " + passed);
    }

    private static void testProtocolRelations() {
        expect(RelationEngine.compareProtocol(Rule.Protocol.TCP, Rule.Protocol.TCP),
                Relation.EQUAL, "protocol equal");
        expect(RelationEngine.compareProtocol(Rule.Protocol.TCP, Rule.Protocol.ANY),
                Relation.SUBSET, "TCP subset ANY");
        expect(RelationEngine.compareProtocol(Rule.Protocol.ANY, Rule.Protocol.UDP),
                Relation.SUPERSET, "ANY superset UDP");
        expect(RelationEngine.compareProtocol(Rule.Protocol.TCP, Rule.Protocol.UDP),
                Relation.DISJOINT, "TCP disjoint UDP");
    }

    private static void testIpRelations() {
        Rule.Ipv4Range a = Rule.Ipv4Range.parse("10.0.0.0-10.0.0.100");
        Rule.Ipv4Range b = Rule.Ipv4Range.parse("10.0.0.20-10.0.0.30");
        Rule.Ipv4Range c = Rule.Ipv4Range.parse("10.0.0.90-10.0.0.120");
        Rule.Ipv4Range d = Rule.Ipv4Range.parse("10.0.1.1");

        expect(RelationEngine.compareIp(b, a), Relation.SUBSET, "IP subset");
        expect(RelationEngine.compareIp(a, b), Relation.SUPERSET, "IP superset");
        expect(RelationEngine.compareIp(a, c), Relation.PARTIAL_OVERLAP, "IP partial overlap");
        expect(RelationEngine.compareIp(a, d), Relation.DISJOINT, "IP disjoint");
        expect(RelationEngine.compareIp(null, a), Relation.SUPERSET, "ANY IP superset");
    }

    private static void testPortRelations() {
        Rule.PortRange a = Rule.PortRange.parse("80-100");
        Rule.PortRange b = Rule.PortRange.parse("90-120");
        Rule.PortRange c = Rule.PortRange.parse("90-95");
        Rule.PortRange d = Rule.PortRange.parse("443");

        expect(RelationEngine.comparePort(a, b), Relation.PARTIAL_OVERLAP, "port partial overlap");
        expect(RelationEngine.comparePort(c, a), Relation.SUBSET, "port subset");
        expect(RelationEngine.comparePort(a, c), Relation.SUPERSET, "port superset");
        expect(RelationEngine.comparePort(a, d), Relation.DISJOINT, "port disjoint");
    }

    private static void testMarkRelations() {
        expect(RelationEngine.compareMark(5L, 5L), Relation.EQUAL, "mark equal");
        expect(RelationEngine.compareMark(5L, null), Relation.SUBSET, "mark constant subset ANY");
        expect(RelationEngine.compareMark(null, 5L), Relation.SUPERSET, "ANY mark superset constant");
        expect(RelationEngine.compareMark(5L, 7L), Relation.DISJOINT, "different marks disjoint");
    }

    private static void testRuleRelations() {
        Rule narrow = terminalRule(1, Rule.Protocol.TCP, Rule.Effect.DROP);
        narrow.setDestinationPort(Rule.PortRange.parse("80"));
        narrow.setSourceIp(Rule.Ipv4Range.parse("10.0.0.10"));

        Rule broad = terminalRule(2, Rule.Protocol.TCP, Rule.Effect.DROP);
        broad.setDestinationPort(Rule.PortRange.parse("1-1024"));
        broad.setSourceIp(Rule.Ipv4Range.parse("10.0.0.0-10.0.0.255"));

        expect(RelationEngine.compareRules(narrow, broad), Relation.SUBSET,
                "rule subset composition");
        expect(RelationEngine.compareRules(broad, narrow), Relation.SUPERSET,
                "rule superset composition");

        Rule partialA = terminalRule(3, Rule.Protocol.TCP, Rule.Effect.ACCEPT);
        partialA.setSourceIp(Rule.Ipv4Range.parse("10.0.0.0-10.0.0.100"));
        partialA.setDestinationPort(Rule.PortRange.parse("80-100"));

        Rule partialB = terminalRule(4, Rule.Protocol.TCP, Rule.Effect.ACCEPT);
        partialB.setSourceIp(Rule.Ipv4Range.parse("10.0.0.50-10.0.0.150"));
        partialB.setDestinationPort(Rule.PortRange.parse("90-120"));

        expect(RelationEngine.compareRules(partialA, partialB), Relation.PARTIAL_OVERLAP,
                "rule partial overlap composition");

        Rule udp = terminalRule(5, Rule.Protocol.UDP, Rule.Effect.ACCEPT);
        expect(RelationEngine.compareRules(partialA, udp), Relation.DISJOINT,
                "rule protocol disjoint");
    }

    private static void testSafeDisjointSwap() {
        Rule tcpAccept = terminalRule(10, Rule.Protocol.TCP, Rule.Effect.ACCEPT);
        Rule udpDrop = terminalRule(11, Rule.Protocol.UDP, Rule.Effect.DROP);

        SwapAnalyzer.Decision d = SwapAnalyzer.canSwap(tcpAccept, udpDrop);
        expect(d.canSwap(), true, "DISJOINT terminal rules may swap");
        expect(d.getRelation(), Relation.DISJOINT, "DISJOINT swap relation");
    }

    private static void testUnsafeOverlappingVerdicts() {
        Rule a = terminalRule(20, Rule.Protocol.TCP, Rule.Effect.ACCEPT);
        a.setDestinationPort(Rule.PortRange.parse("80-100"));

        Rule b = terminalRule(21, Rule.Protocol.TCP, Rule.Effect.DROP);
        b.setDestinationPort(Rule.PortRange.parse("90-120"));

        SwapAnalyzer.Decision d = SwapAnalyzer.canSwap(a, b);
        expect(d.canSwap(), false, "overlapping ACCEPT/DROP must not swap");
        expect(d.getRelation(), Relation.PARTIAL_OVERLAP, "overlapping verdict relation");
    }

    private static void testUnsafeLogOverlap() {
        Rule log = effectRule(30, Rule.Protocol.TCP, Rule.Effect.LOG);
        Rule drop = terminalRule(31, Rule.Protocol.TCP, Rule.Effect.DROP);

        SwapAnalyzer.Decision d = SwapAnalyzer.canSwap(log, drop);
        expect(d.canSwap(), false, "overlapping LOG and terminal must not swap");
    }

    private static void testMarkRaw() {
        Rule write = effectRule(40, Rule.Protocol.ANY, Rule.Effect.MARK_WRITE);
        write.setMarkWrite(5L);

        Rule read = terminalRule(41, Rule.Protocol.ANY, Rule.Effect.DROP);
        read.setMarkRead(5L);

        expect(DependencyAnalyzer.findStateDependencies(write, read)
                        .contains(DependencyAnalyzer.Dependency.MARK_RAW),
                true, "MARK RAW detected");
        expect(SwapAnalyzer.canSwap(write, read).canSwap(), false,
                "MARK RAW blocks swap");
    }

    private static void testMarkWar() {
        Rule read = terminalRule(50, Rule.Protocol.ANY, Rule.Effect.ACCEPT);
        read.setMarkRead(5L);

        Rule write = effectRule(51, Rule.Protocol.ANY, Rule.Effect.MARK_WRITE);
        write.setMarkWrite(7L);

        expect(DependencyAnalyzer.findStateDependencies(read, write)
                        .contains(DependencyAnalyzer.Dependency.MARK_WAR),
                true, "MARK WAR detected");
        expect(SwapAnalyzer.canSwap(read, write).canSwap(), false,
                "MARK WAR blocks swap");
    }

    private static void testMarkWaw() {
        Rule a = effectRule(60, Rule.Protocol.TCP, Rule.Effect.MARK_WRITE);
        a.setMarkWrite(5L);

        Rule b = effectRule(61, Rule.Protocol.TCP, Rule.Effect.MARK_WRITE);
        b.setMarkWrite(7L);

        expect(DependencyAnalyzer.findStateDependencies(a, b)
                        .contains(DependencyAnalyzer.Dependency.MARK_WAW),
                true, "MARK WAW detected");
        expect(SwapAnalyzer.canSwap(a, b).canSwap(), false,
                "MARK WAW blocks swap");
    }

    private static void testUnknownBarrier() {
        Rule a = terminalRule(70, Rule.Protocol.TCP, Rule.Effect.ACCEPT);
        Rule barrier = terminalRule(71, Rule.Protocol.UDP, Rule.Effect.DROP);
        barrier.markUnsupported("synthetic unsupported construct");

        expect(RelationEngine.compareRules(a, barrier), Relation.UNKNOWN,
                "unsupported rule relation is UNKNOWN");
        expect(SwapAnalyzer.canSwap(a, barrier).canSwap(), false,
                "unsupported rule blocks swap");
    }

    private static Rule terminalRule(int index, Rule.Protocol protocol, Rule.Effect effect) {
        return effectRule(index, protocol, effect);
    }

    private static Rule effectRule(int index, Rule.Protocol protocol, Rule.Effect effect) {
        Rule rule = new Rule(index, "test rule " + index);
        rule.setProtocol(protocol);
        rule.setEffect(effect);
        return rule;
    }

    private static void expect(Object actual, Object expected, String name) {
        if (actual == null ? expected != null : !actual.equals(expected)) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
        passed++;
    }
}
