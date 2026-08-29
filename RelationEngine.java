public final class RelationEngine {

    private RelationEngine() {
    }

    public static Relation compareProtocol(Rule.Protocol a, Rule.Protocol b) {
        if (a == null || b == null) {
            return Relation.UNKNOWN;
        }

        if (a == b) {
            return Relation.EQUAL;
        }

        if (a == Rule.Protocol.ANY) {
            return Relation.SUPERSET;
        }

        if (b == Rule.Protocol.ANY) {
            return Relation.SUBSET;
        }

        return Relation.DISJOINT;
    }

    public static Relation compareIp(Rule.Ipv4Range a, Rule.Ipv4Range b) {
        // null ovde znaci ANY.
        if (a == null && b == null) {
            return Relation.EQUAL;
        }
        if (a == null) {
            return Relation.SUPERSET;
        }
        if (b == null) {
            return Relation.SUBSET;
        }

        if (a.getStart() > a.getEnd() || b.getStart() > b.getEnd()) {
            return Relation.UNKNOWN;
        }

        return compareClosedIntervals(
                a.getStart(), a.getEnd(),
                b.getStart(), b.getEnd());
    }

    public static Relation comparePort(Rule.PortRange a, Rule.PortRange b) {
        // null ovde znaci ANY.
        if (a == null && b == null) {
            return Relation.EQUAL;
        }
        if (a == null) {
            return Relation.SUPERSET;
        }
        if (b == null) {
            return Relation.SUBSET;
        }

        if (a.getStart() > a.getEnd() || b.getStart() > b.getEnd()) {
            return Relation.UNKNOWN;
        }

        return compareClosedIntervals(
                a.getStart(), a.getEnd(),
                b.getStart(), b.getEnd());
    }

    public static Relation compareMark(Long a, Long b) {
        // null ovde znaci da nema MARK uslova.
        if (a == null && b == null) {
            return Relation.EQUAL;
        }
        if (a == null) {
            return Relation.SUPERSET;
        }
        if (b == null) {
            return Relation.SUBSET;
        }

        return a.equals(b) ? Relation.EQUAL : Relation.DISJOINT;
    }

    public static Relation compareRules(Rule a, Rule b) {
        if (a == null || b == null) {
            return Relation.UNKNOWN;
        }

        // Ono sto parser ne razume ostaje barijera.
        if (!a.isSupported() || !b.isSupported()) {
            return Relation.UNKNOWN;
        }

        Relation[] fields = {
                compareProtocol(a.getProtocol(), b.getProtocol()),
                compareIp(a.getSourceIp(), b.getSourceIp()),
                compareIp(a.getDestinationIp(), b.getDestinationIp()),
                comparePort(a.getSourcePort(), b.getSourcePort()),
                comparePort(a.getDestinationPort(), b.getDestinationPort()),
                compareMark(a.getMarkRead(), b.getMarkRead())
        };

        // Jedno disjoint polje je dovoljno da cela pravila budu disjoint.
        for (Relation relation : fields) {
            if (relation == Relation.DISJOINT) {
                return Relation.DISJOINT;
            }
        }

        // Bez poznate relacije ne pokusavam da pogadjam rezultat.
        for (Relation relation : fields) {
            if (relation == Relation.UNKNOWN) {
                return Relation.UNKNOWN;
            }
        }

        boolean allEqual = true;
        boolean aSubsetOrEqual = true;
        boolean aSupersetOrEqual = true;

        for (Relation relation : fields) {
            if (relation != Relation.EQUAL) {
                allEqual = false;
            }

            if (relation != Relation.EQUAL && relation != Relation.SUBSET) {
                aSubsetOrEqual = false;
            }

            if (relation != Relation.EQUAL && relation != Relation.SUPERSET) {
                aSupersetOrEqual = false;
            }
        }

        if (allEqual) {
            return Relation.EQUAL;
        }

        if (aSubsetOrEqual) {
            return Relation.SUBSET;
        }

        if (aSupersetOrEqual) {
            return Relation.SUPERSET;
        }

        return Relation.PARTIAL_OVERLAP;
    }

    private static Relation compareClosedIntervals(
            long aStart, long aEnd,
            long bStart, long bEnd) {

        if (aStart == bStart && aEnd == bEnd) {
            return Relation.EQUAL;
        }

        if (aEnd < bStart || bEnd < aStart) {
            return Relation.DISJOINT;
        }

        if (aStart >= bStart && aEnd <= bEnd) {
            return Relation.SUBSET;
        }

        if (bStart >= aStart && bEnd <= aEnd) {
            return Relation.SUPERSET;
        }

        return Relation.PARTIAL_OVERLAP;
    }
}
