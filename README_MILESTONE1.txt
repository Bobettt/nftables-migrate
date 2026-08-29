PETNICA OPTIMIZER - MILESTONE 1

Existing files kept unchanged:
- Rule.java
- NftParser.java
- Main.java

Added files:
- Relation.java
  EQUAL / SUBSET / SUPERSET / DISJOINT / PARTIAL_OVERLAP / UNKNOWN

- RelationEngine.java
  Field relations for protocol, IPv4, port and MARK read.
  compareRules(A, B) composes them into one match-space relation.

- RuleMetadata.java
  Extracts readsMark, writesMark, terminal, LOG side effect and barrier metadata.

- DependencyAnalyzer.java
  Detects MARK RAW / WAR / WAW dependencies in original order A -> B.

- SwapAnalyzer.java
  Conservative canSwap(A, B) with a human-readable reason.
  Milestone 1 only proves a swap safe when match spaces are DISJOINT and
  there is no state dependency or barrier. Overlapping rules are rejected
  unless a later milestone adds a stronger proof.

- Milestone1Tests.java
  Small no-framework tests for field/rule relations, safe DISJOINT swap,
  unsafe ACCEPT/DROP overlap, LOG, MARK RAW/WAR/WAW and UNKNOWN barrier.

- Milestone1Demo.java
  Parses an existing .nft file and compares two parsed Rule objects by index.

Compile:
  javac *.java

Run tests:
  java Milestone1Tests

Compare two parsed rules:
  java Milestone1Demo /path/to/test100.nft 1 2
  java Milestone1Demo /path/to/test100.nft 9 10
