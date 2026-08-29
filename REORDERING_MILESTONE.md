# Safe reordering milestone

## What existed before

The project already parsed the supported `ip filter INPUT` subset into `Rule`,
classified match-space relations, detected MARK dependencies, and answered
whether one adjacent pair could safely swap. It did not change rule order.

## What this pass changes

`SafeReorderOptimizer` finds conservative merge candidates and moves a later
candidate toward an anchor rule. It changes only list order. It reuses the
existing `Rule` objects and does not change their fields or text.

## Why adjacent swaps

Every ordering change is decomposed into adjacent swaps. For current order
`LEFT, MOVING`, the optimizer calls `SwapAnalyzer.canSwap(LEFT, MOVING)` before
crossing. A failed safety decision immediately stops that movement. Therefore
the output order is reachable through a finite sequence of individually
approved swaps; the pass never performs an arbitrary sort or jump.

## Merge candidate definition

In this MVP, both rules must be supported ACCEPT or DROP rules with the same
effect, equal protocol, equal MARK read, and no MARK write. Of source IP,
destination IP, source port, and destination port, exactly one field must be
DISJOINT and all other fields must be EQUAL.

## What blocks movement

The unchanged `SwapAnalyzer` blocks unsupported/UNKNOWN rules, MARK RAW/WAR/WAW
dependencies, overlapping observable LOG effects, overlapping terminal
verdicts, and every case for which the current MVP has no positive proof.

## Deliberately conservative scope

False negatives are acceptable. This milestone does not weaken safety checks
and does not claim semantic preservation outside the current `Rule` IR subset.
It does not implement chain synthesis, KnownState, CFG analysis, NAT,
conntrack, or a general nftables optimizer.

The pass does not merge rules. It only places safe merge candidates next to
each other so a later stock `nft --optimize` pass may compact them.

## Commands

```text
javac *.java
java Milestone1Tests
java ReorderingTests
java SyntheticReorderingBenchmark
java OptimizerMain input.nft optimized.nft
```
