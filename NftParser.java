import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class NftParser {

    public List<Rule> parseFile(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        List<Rule> rules = new ArrayList<>();

        int ruleIndex = 1;
        for (String line : lines) {
            String trimmed = line.trim();

            if (!trimmed.startsWith("add rule ")) {
                continue;
            }

            Rule rule = parseRuleLine(ruleIndex, trimmed);
            rules.add(rule);
            ruleIndex++;
        }

        return rules;
    }

    private Rule parseRuleLine(int index, String line) {
        Rule rule = new Rule(index, line);

        try {
            List<String> tokens = tokenize(line);

            if (tokens.size() < 5
                    || !tokens.get(0).equals("add")
                    || !tokens.get(1).equals("rule")) {
                rule.markUnsupported("Malformed 'add rule' line");
                return rule;
            }

            String family = tokens.get(2);
            String table = tokens.get(3);
            String chain = tokens.get(4);

            // MVP za sada radi samo nad ip filter INPUT pravilima.
            if (!family.equals("ip") || !table.equals("filter") || !chain.equals("INPUT")) {
                rule.markUnsupported(
                        "Rule is outside supported subset: "
                                + family + " " + table + " " + chain);
                return rule;
            }

            int i = 5;
            while (i < tokens.size()) {
                String token = tokens.get(i);

                switch (token) {
                    case "ip":
                        i = parseIpExpression(tokens, i, rule);
                        break;

                    case "tcp":
                        i = parsePortExpression(tokens, i, rule, Rule.Protocol.TCP);
                        break;

                    case "udp":
                        i = parsePortExpression(tokens, i, rule, Rule.Protocol.UDP);
                        break;

                    case "mark":
                        i = parseMarkRead(tokens, i, rule);
                        break;

                    case "meta":
                        i = parseMetaExpression(tokens, i, rule);
                        break;

                    case "counter":
                        // Counter jos ne cuvam u Rule IR-u.
                        i++;
                        break;

                    case "accept":
                        setEffect(rule, Rule.Effect.ACCEPT);
                        i++;
                        break;

                    case "drop":
                        setEffect(rule, Rule.Effect.DROP);
                        i++;
                        break;

                    case "log":
                        i = parseLog(tokens, i, rule);
                        break;

                    case "jump":
                    case "goto":
                    case "return":
                        rule.markUnsupported("Control-flow construct is a barrier in MVP: " + token);
                        return rule;

                    default:
                        rule.markUnsupported("Unknown/unsupported token: " + token);
                        return rule;
                }

                if (!rule.isSupported()) {
                    return rule;
                }
            }

            if (rule.getEffect() == Rule.Effect.UNKNOWN) {
                rule.markUnsupported("No supported action/effect found");
                return rule;
            }

            validateRule(rule);
            return rule;

        } catch (IllegalArgumentException e) {
            rule.markUnsupported(e.getMessage());
            return rule;
        }
    }

    private int parseIpExpression(List<String> tokens, int i, Rule rule) {
        require(tokens, i, 3, "Incomplete ip expression");

        String field = tokens.get(i + 1);
        String value = tokens.get(i + 2);

        switch (field) {
            case "saddr":
                if (rule.getSourceIp() != null) {
                    throw new IllegalArgumentException("Duplicate ip saddr");
                }
                rule.setSourceIp(Rule.Ipv4Range.parse(value));
                break;

            case "daddr":
                if (rule.getDestinationIp() != null) {
                    throw new IllegalArgumentException("Duplicate ip daddr");
                }
                rule.setDestinationIp(Rule.Ipv4Range.parse(value));
                break;

            case "protocol":
                setExplicitProtocol(rule, value);
                break;

            default:
                throw new IllegalArgumentException("Unsupported ip field: " + field);
        }

        return i + 3;
    }

    private int parsePortExpression(
            List<String> tokens,
            int i,
            Rule rule,
            Rule.Protocol portProtocol) {

        require(tokens, i, 3, "Incomplete " + tokens.get(i) + " port expression");

        ensureProtocolCompatible(rule, portProtocol);

        String field = tokens.get(i + 1);
        String value = tokens.get(i + 2);
        Rule.PortRange range = Rule.PortRange.parse(value);

        switch (field) {
            case "sport":
                if (rule.getSourcePort() != null) {
                    throw new IllegalArgumentException("Duplicate source port");
                }
                rule.setSourcePort(range);
                break;

            case "dport":
                if (rule.getDestinationPort() != null) {
                    throw new IllegalArgumentException("Duplicate destination port");
                }
                rule.setDestinationPort(range);
                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported " + tokens.get(i) + " field: " + field);
        }

        return i + 3;
    }

    private int parseMarkRead(List<String> tokens, int i, Rule rule) {
        require(tokens, i, 2, "Incomplete mark read expression");

        if (rule.getMarkRead() != null) {
            throw new IllegalArgumentException("Duplicate MARK read");
        }

        rule.setMarkRead(parseMarkValue(tokens.get(i + 1)));
        return i + 2;
    }

    private int parseMetaExpression(List<String> tokens, int i, Rule rule) {
        require(tokens, i, 4, "Incomplete meta expression");

        if (!tokens.get(i + 1).equals("mark") || !tokens.get(i + 2).equals("set")) {
            throw new IllegalArgumentException(
                    "Unsupported meta expression near: " + tokens.get(i + 1));
        }

        if (rule.getMarkWrite() != null) {
            throw new IllegalArgumentException("Duplicate MARK write");
        }

        long value = parseMarkValue(tokens.get(i + 3));
        rule.setMarkWrite(value);
        setEffect(rule, Rule.Effect.MARK_WRITE);
        return i + 4;
    }

    private int parseLog(List<String> tokens, int i, Rule rule) {
        setEffect(rule, Rule.Effect.LOG);
        i++;

        if (i < tokens.size() && tokens.get(i).equals("prefix")) {
            require(tokens, i, 2, "LOG prefix is missing its string value");
            i += 2;
        }

        return i;
    }

    private void setExplicitProtocol(Rule rule, String value) {
        Rule.Protocol protocol;

        switch (value) {
            case "tcp":
                protocol = Rule.Protocol.TCP;
                break;
            case "udp":
                protocol = Rule.Protocol.UDP;
                break;
            case "icmp":
                protocol = Rule.Protocol.ICMP;
                break;
            default:
                throw new IllegalArgumentException("Unsupported IP protocol: " + value);
        }

        if (rule.getProtocol() != Rule.Protocol.ANY && rule.getProtocol() != protocol) {
            throw new IllegalArgumentException(
                    "Conflicting protocols: " + rule.getProtocol() + " and " + protocol);
        }

        rule.setProtocol(protocol);
    }

    private void ensureProtocolCompatible(Rule rule, Rule.Protocol portProtocol) {
        Rule.Protocol current = rule.getProtocol();

        if (current == Rule.Protocol.ANY) {
            // tcp/udp matcher vec odredjuje protokol i bez posebnog ip protocol izraza.
            rule.setProtocol(portProtocol);
            return;
        }

        if (current != portProtocol) {
            throw new IllegalArgumentException(
                    "Port matcher conflicts with protocol: "
                            + portProtocol + " ports with " + current);
        }
    }

    private void validateRule(Rule rule) {
        boolean hasPort = rule.getSourcePort() != null || rule.getDestinationPort() != null;
        if (hasPort
                && rule.getProtocol() != Rule.Protocol.TCP
                && rule.getProtocol() != Rule.Protocol.UDP) {
            throw new IllegalArgumentException("Ports require TCP or UDP");
        }

        if (rule.getEffect() == Rule.Effect.MARK_WRITE && rule.getMarkWrite() == null) {
            throw new IllegalArgumentException("MARK_WRITE effect without markWrite value");
        }

        if (rule.getEffect() != Rule.Effect.MARK_WRITE && rule.getMarkWrite() != null) {
            throw new IllegalArgumentException("markWrite exists but effect is not MARK_WRITE");
        }
    }

    private void setEffect(Rule rule, Rule.Effect newEffect) {
        if (rule.getEffect() != Rule.Effect.UNKNOWN) {
            throw new IllegalArgumentException(
                    "Multiple actions/effects in one rule are outside MVP: "
                            + rule.getEffect() + " and " + newEffect);
        }
        rule.setEffect(newEffect);
    }

    private long parseMarkValue(String text) {
        long value;
        try {
            if (text.startsWith("0x") || text.startsWith("0X")) {
                value = Long.parseLong(text.substring(2), 16);
            } else {
                value = Long.parseLong(text);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid MARK value: " + text);
        }

        if (value < 0 || value > 0xffff_ffffL) {
            throw new IllegalArgumentException("MARK value outside unsigned 32-bit range: " + text);
        }
        return value;
    }

    private void require(List<String> tokens, int start, int count, String message) {
        if (start + count > tokens.size()) {
            throw new IllegalArgumentException(message);
        }
    }

    private List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaping = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (escaping) {
                current.append(c);
                escaping = false;
                continue;
            }

            if (c == '\\' && inQuotes) {
                escaping = true;
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(c);
        }

        if (escaping) {
            throw new IllegalArgumentException("Dangling escape in quoted string");
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Unclosed quoted string");
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }
}
