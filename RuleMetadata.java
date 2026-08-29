public final class RuleMetadata {
    private final boolean readsMark;
    private final boolean writesMark;
    private final boolean terminal;
    private final boolean logSideEffect;
    private final boolean barrier;

    private RuleMetadata(
            boolean readsMark,
            boolean writesMark,
            boolean terminal,
            boolean logSideEffect,
            boolean barrier) {
        this.readsMark = readsMark;
        this.writesMark = writesMark;
        this.terminal = terminal;
        this.logSideEffect = logSideEffect;
        this.barrier = barrier;
    }

    public static RuleMetadata from(Rule rule) {
        if (rule == null) {
            return new RuleMetadata(false, false, false, false, true);
        }

        boolean terminal = rule.getEffect() == Rule.Effect.ACCEPT
                || rule.getEffect() == Rule.Effect.DROP;

        boolean logSideEffect = rule.getEffect() == Rule.Effect.LOG;

        boolean barrier = !rule.isSupported()
                || rule.getEffect() == Rule.Effect.UNKNOWN;

        return new RuleMetadata(
                rule.getMarkRead() != null,
                rule.getMarkWrite() != null,
                terminal,
                logSideEffect,
                barrier);
    }

    public boolean readsMark() {
        return readsMark;
    }

    public boolean writesMark() {
        return writesMark;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean hasLogSideEffect() {
        return logSideEffect;
    }

    public boolean isBarrier() {
        return barrier;
    }

    @Override
    public String toString() {
        return "RuleMetadata{" +
                "readsMark=" + readsMark +
                ", writesMark=" + writesMark +
                ", terminal=" + terminal +
                ", logSideEffect=" + logSideEffect +
                ", barrier=" + barrier +
                '}';
    }
}
