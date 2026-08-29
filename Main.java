import java.nio.file.Path;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <translated.nft>");
            System.exit(1);
        }

        Path input = Path.of(args[0]);
        NftParser parser = new NftParser();

        try {
            List<Rule> rules = parser.parseFile(input);

            int supported = 0;
            int unsupported = 0;

            for (Rule rule : rules) {
                System.out.println(rule.toDebugString());
                System.out.println();

                if (rule.isSupported()) {
                    supported++;
                } else {
                    unsupported++;
                }
            }

            System.out.println("====================");
            System.out.println("Parsed rules: " + rules.size());
            System.out.println("Supported:    " + supported);
            System.out.println("Unsupported:  " + unsupported);

        } catch (Exception e) {
            System.err.println("Failed to parse file: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }
}
