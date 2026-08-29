import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class NftRulesetRewriter {

    private NftRulesetRewriter() {
    }

    public static SafeReorderOptimizer.OptimizationResult rewrite(
            Path input, Path output) throws IOException {
        List<String> originalLines = Files.readAllLines(input);
        List<Rule> parsedRules = new NftParser().parseFile(input);
        SafeReorderOptimizer.OptimizationResult result =
                new SafeReorderOptimizer().optimize(parsedRules);

        int slots = 0;
        for (String line : originalLines) {
            if (line.trim().startsWith("add rule ")) {
                slots++;
            }
        }

        if (slots != parsedRules.size()
                || slots != result.getOptimizedRules().size()) {
            throw new IllegalStateException(
                    "Rule slot count mismatch: slots=" + slots
                            + ", parsed=" + parsedRules.size()
                            + ", optimized=" + result.getOptimizedRules().size());
        }

        List<String> rewritten = new ArrayList<>(originalLines.size());
        int ruleIndex = 0;
        for (String line : originalLines) {
            if (line.trim().startsWith("add rule ")) {
                rewritten.add(result.getOptimizedRules().get(ruleIndex).getOriginalText());
                ruleIndex++;
            } else {
                rewritten.add(line);
            }
        }

        Files.write(output, rewritten);
        return result;
    }
}
