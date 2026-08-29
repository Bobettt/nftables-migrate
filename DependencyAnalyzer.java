import java.util.ArrayList;
import java.util.List;

public final class DependencyAnalyzer {

    public enum Dependency {
        MARK_RAW,
        MARK_WAR,
        MARK_WAW
    }

    private DependencyAnalyzer() {
    }

    public static List<Dependency> findStateDependencies(Rule a, Rule b) {
        List<Dependency> result = new ArrayList<>();

        RuleMetadata aMeta = RuleMetadata.from(a);
        RuleMetadata bMeta = RuleMetadata.from(b);

        // A je u originalnom redosledu pre B.
        if (aMeta.writesMark() && bMeta.readsMark()) {
            result.add(Dependency.MARK_RAW);
        }

        if (aMeta.readsMark() && bMeta.writesMark()) {
            result.add(Dependency.MARK_WAR);
        }

        if (aMeta.writesMark() && bMeta.writesMark()) {
            result.add(Dependency.MARK_WAW);
        }

        return result;
    }
}
