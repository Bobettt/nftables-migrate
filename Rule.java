public class Rule {
    public enum Protocol {
        ANY, TCP, UDP, ICMP
    }

    public enum Effect {
        ACCEPT, DROP, LOG, MARK_WRITE, UNKNOWN
    }

    public static final class Ipv4Range {
        private final long start;
        private final long end;

        public Ipv4Range(long start, long end) {
            this.start = start;
            this.end = end;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        public boolean isSingleHost() {
            return start == end;
        }

        @Override
        public String toString() {
            if (isSingleHost()) {
                return longToIpv4(start);
            }
            return longToIpv4(start) + "-" + longToIpv4(end);
        }

        public static Ipv4Range parse(String text) {
            if (text.contains("/")) {
                throw new IllegalArgumentException("CIDR/prefix is not supported in this MVP: " + text);
            }

            int dash = text.indexOf('-');
            if (dash < 0) {
                long value = ipv4ToLong(text);
                return new Ipv4Range(value, value);
            }

            if (text.indexOf('-', dash + 1) >= 0) {
                throw new IllegalArgumentException("Invalid IPv4 range: " + text);
            }

            long start = ipv4ToLong(text.substring(0, dash));
            long end = ipv4ToLong(text.substring(dash + 1));
            if (start > end) {
                throw new IllegalArgumentException("IPv4 range start is greater than end: " + text);
            }

            return new Ipv4Range(start, end);
        }

        private static long ipv4ToLong(String ip) {
            String[] parts = ip.split("\\.", -1);
            if (parts.length != 4) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
            }

            long value = 0;
            for (String part : parts) {
                int octet;
                try {
                    octet = Integer.parseInt(part);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
                }

                if (octet < 0 || octet > 255) {
                    throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
                }

                value = (value << 8) | octet;
            }
            return value;
        }

        private static String longToIpv4(long value) {
            return ((value >>> 24) & 0xff) + "."
                    + ((value >>> 16) & 0xff) + "."
                    + ((value >>> 8) & 0xff) + "."
                    + (value & 0xff);
        }
    }

    public static final class PortRange {
        private final int start;
        private final int end;

        public PortRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public boolean isSinglePort() {
            return start == end;
        }

        @Override
        public String toString() {
            if (isSinglePort()) {
                return Integer.toString(start);
            }
            return start + "-" + end;
        }

        public static PortRange parse(String text) {
            int dash = text.indexOf('-');
            if (dash < 0) {
                int port = parseOnePort(text);
                return new PortRange(port, port);
            }

            if (text.indexOf('-', dash + 1) >= 0) {
                throw new IllegalArgumentException("Invalid port range: " + text);
            }

            int start = parseOnePort(text.substring(0, dash));
            int end = parseOnePort(text.substring(dash + 1));
            if (start > end) {
                throw new IllegalArgumentException("Port range start is greater than end: " + text);
            }

            return new PortRange(start, end);
        }

        private static int parseOnePort(String text) {
            int port;
            try {
                port = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port: " + text);
            }

            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Port out of range: " + text);
            }
            return port;
        }
    }

    private final int index;
    private final String originalText;

    private Ipv4Range sourceIp;
    private Ipv4Range destinationIp;
    private Protocol protocol = Protocol.ANY;
    private PortRange sourcePort;
    private PortRange destinationPort;
    private Long markRead;
    private Long markWrite;
    private Effect effect = Effect.UNKNOWN;

    private boolean supported = true;
    private String unsupportedReason;

    public Rule(int index, String originalText) {
        this.index = index;
        this.originalText = originalText;
    }

    public int getIndex() {
        return index;
    }

    public String getOriginalText() {
        return originalText;
    }

    public Ipv4Range getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(Ipv4Range sourceIp) {
        this.sourceIp = sourceIp;
    }

    public Ipv4Range getDestinationIp() {
        return destinationIp;
    }

    public void setDestinationIp(Ipv4Range destinationIp) {
        this.destinationIp = destinationIp;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public PortRange getSourcePort() {
        return sourcePort;
    }

    public void setSourcePort(PortRange sourcePort) {
        this.sourcePort = sourcePort;
    }

    public PortRange getDestinationPort() {
        return destinationPort;
    }

    public void setDestinationPort(PortRange destinationPort) {
        this.destinationPort = destinationPort;
    }

    public Long getMarkRead() {
        return markRead;
    }

    public void setMarkRead(Long markRead) {
        this.markRead = markRead;
    }

    public Long getMarkWrite() {
        return markWrite;
    }

    public void setMarkWrite(Long markWrite) {
        this.markWrite = markWrite;
    }

    public Effect getEffect() {
        return effect;
    }

    public void setEffect(Effect effect) {
        this.effect = effect;
    }

    public boolean isSupported() {
        return supported;
    }

    public String getUnsupportedReason() {
        return unsupportedReason;
    }

    public void markUnsupported(String reason) {
        supported = false;
        effect = Effect.UNKNOWN;
        unsupportedReason = reason;
    }

    public String toDebugString() {
        StringBuilder out = new StringBuilder();
        out.append("Rule #").append(index).append(' ');

        if (supported) {
            out.append("[SUPPORTED]\n");
        } else {
            out.append("[UNSUPPORTED / BARRIER]\n");
        }

        out.append("  srcIp:       ").append(sourceIp == null ? "ANY" : sourceIp).append('\n');
        out.append("  dstIp:       ").append(destinationIp == null ? "ANY" : destinationIp).append('\n');
        out.append("  protocol:    ").append(protocol).append('\n');
        out.append("  srcPort:     ").append(sourcePort == null ? "ANY" : sourcePort).append('\n');
        out.append("  dstPort:     ").append(destinationPort == null ? "ANY" : destinationPort).append('\n');
        out.append("  markRead:    ").append(formatMark(markRead)).append('\n');
        out.append("  markWrite:   ").append(formatMark(markWrite)).append('\n');
        out.append("  effect:      ").append(effect).append('\n');

        if (!supported) {
            out.append("  reason:      ").append(unsupportedReason).append('\n');
        }

        out.append("  original:    ").append(originalText);
        return out.toString();
    }

    private static String formatMark(Long mark) {
        if (mark == null) {
            return "NONE";
        }
        return "0x" + Long.toHexString(mark);
    }
}
