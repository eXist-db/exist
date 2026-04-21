# JNode Type Hierarchy Overhaul — Tasking

## Context

eXist-db 7.0 (next-v3) has initial XQuery 4.0 JNode support implemented via an
**intercept approach**: LocationStep and PathExpr detect maps/arrays/JNodes at
runtime and dispatch to parallel navigation code. This achieved +71 XQTS tests
(from 17 to ~64 of ~200 JNode path tests) but has structural limitations.

### What the intercept approach achieved
- `Type.isNavigable()` — maps/arrays accepted as LHS of `/`
- `LocationStep.evalMapArrayAxis()` — child/descendant/self on maps/arrays
- `LocationStep.evalJNodeAxis()` — all 15 XPath axes on JNodes
- `PathExpr.containsNavigableItem()` — runtime type detection
- `fn:jvalue()`/`fn:jkey()` tolerance of non-JNode inputs
- `fn:get()` returns empty for out-of-bounds
- NameTest key matching, wildcard `*` matching on JNodes

### Where the intercept approach fails (~130 remaining tests)
1. **Multi-step paths with intermediate typing** — `$map/store/book/*/author`
   loses map type after step 2, PathExpr's `gotAtomicResult` flag blocks step 3
2. **`get()` as path step** — `$array//get(3)` requires parser grammar changes
3. **Mixed XML/JSON sequences** — only first item checked for type routing
4. **Document ordering** — JNodes have no natural document order across trees
5. **`node()` type matching** — JNodes don't participate in standard `node()` tests
   outside our intercept points
6. **`gnode()` unification** — XML nodes don't implement the GNode interface

## Goal

Unify XML and JSON nodes under a common navigation interface so the path
expression engine handles both uniformly, without special intercepts.

## Design: The GNode Approach (Approach 2, extended)

### Phase 1: GNode Interface Expansion

**GNode.java** (currently 92 lines) becomes the universal navigation interface:

```java
public interface GNode extends Item {
    // Identity
    boolean isSameNode(GNode other);
    
    // Tree navigation
    GNode getGNodeParent();
    List<? extends GNode> getGNodeChildren();
    GNode getGNodeRoot();
    
    // Axes (all 15)
    List<? extends GNode> getFollowingSiblings();
    List<? extends GNode> getPrecedingSiblings();
    List<? extends GNode> getFollowing();
    List<? extends GNode> getPreceding();
    List<? extends GNode> getDescendants();
    List<? extends GNode> getAncestors();
    
    // Node properties
    String getGNodeName();           // element name or map key
    Sequence getGNodeValue();        // typed value
    int getGNodeKind();              // Type constant
    int getDocumentOrderPosition();  // for ordering across trees
}
```

**Effort**: 1-2 days. Low risk — interface only.

### Phase 2: JNode implements GNode (already done)

JNode already implements GNode with all axis methods. This phase is complete.

### Phase 3: XML Node GNode Adapter

Create `GNodeAdapter` that wraps eXist XML nodes (`NodeProxy`, `NodeImpl`) to
implement GNode:

```java
public class XmlGNodeAdapter implements GNode {
    private final NodeValue xmlNode;
    // Delegates to existing DOM/eXist node navigation
}
```

This does NOT modify NodeProxy or NodeImpl — it wraps them. The adapter is
created on-demand when XML nodes enter JNode-aware code paths.

**Key decisions**:
- Adapter created lazily (not for every XML node)
- Document ordering: XML nodes keep their existing ordering; JNodes get
  synthetic positions based on tree structure
- Mixed sequences: sort by (source-document, position) with JSON trees
  getting synthetic document IDs

**Effort**: 3-5 days. Medium risk — must not regress XML processing.

### Phase 4: LocationStep GNode Unification

Replace the current three-way dispatch (XML nodes / JNodes / maps/arrays) with
a single GNode code path:

```java
// Current: three separate paths
if (hasJNode) { evalJNodeAxis(ctx); }
else if (hasMapArray) { evalMapArrayAxis(ctx); }
else { /* normal XML processing */ }

// Target: single GNode path when context contains GNodes
if (hasGNode) { evalGNodeAxis(ctx); }
else { /* normal XML processing for persistent nodes */ }
```

The `evalGNodeAxis` method works uniformly on any GNode, whether it wraps an
XML node, a JNode, or a map/array.

**Critical**: The normal XML processing path (persistent NodeSets, structural
indexes) must NOT be affected. GNode routing only activates for in-memory nodes
and JNodes.

**Effort**: 5-7 days. High risk — touches the core evaluation loop.

### Phase 5: PathExpr Simplification

With GNode unification, PathExpr no longer needs:
- `containsNavigableItem()` hack
- `Type.isNavigable()` (replaced by `instanceof GNode` checks)
- `gotAtomicResult` special cases for maps/arrays

The type flow becomes: if a step returns GNodes, the next step can navigate them.
If it returns atomics, XPTY0019 fires. Clean and structural.

**Effort**: 2-3 days. Medium risk — simplification, not new code.

### Phase 6: Map/Array Auto-wrapping

When a raw map or array enters a path expression context, auto-wrap it as a
JNode tree (equivalent to implicit `jtree()`). This eliminates the need for
separate `evalMapArrayAxis` — all map/array navigation goes through JNode.

```java
// In LocationStep, when context item is a map/array:
if (item instanceof MapType || item instanceof ArrayType) {
    item = JNode.wrap(item);  // Creates JNode tree on the fly
}
// Then handle as GNode
```

**Effort**: 2-3 days. Medium risk.

### Phase 7: Parser Grammar Additions

Independent of the type hierarchy, but needed for full compliance:

1. **`get()` as path step** — `child::get("key")`, `descendant::get(3)`
2. **`jnode(*, type)` kind test** — `jnode(*, xs:integer)`, `jnode(*, record(...))`
3. **`gnode()` kind test** — matches any GNode (XML or JSON)
4. **Union types in kind tests** — `jnode(*, xs:string | xs:integer)`

Grammar changes in XQuery.g (ANTLR 2) and XQueryParser.java (RD parser).

**Effort**: 3-5 days. Medium risk — parser changes are well-understood.

## Risk Analysis

| Phase | Risk | Mitigation |
|-------|------|------------|
| 1-2 | Low | Interface-only, JNode already works |
| 3 | Medium | Adapter pattern isolates changes from XML code |
| 4 | **High** | Core eval loop; extensive regression testing needed |
| 5 | Medium | Removing hacks, not adding complexity |
| 6 | Medium | JNode.wrap() is simple, but ordering semantics are complex |
| 7 | Medium | Parser changes are well-understood from XQ4 work |

## Testing Strategy

1. **Before starting**: Baseline XQTS scores for QT4, XQ 3.1, and FTTS
2. **After each phase**: Run full XQTS + JNode JUnit tests + XQSuite
3. **XQ 3.1 regression gate**: Must not drop below current 93%
4. **QT4 target**: 75%+ of JNode path tests (from current ~32%)

## Estimated Total Effort

- **Minimum viable** (Phases 1-4): 10-15 days
- **Full overhaul** (Phases 1-7): 20-25 days
- **Recommended approach**: Ship Phases 1-4 as `v4/gnode-overhaul`, then
  Phases 5-7 as follow-ups

## Dependencies

- No external dependencies
- Should be based on `next-v3` (which has the intercept approach as foundation)
- Parser changes (Phase 7) can proceed in parallel with Phases 3-5

## Reference Implementations

- **BaseX** (`org.basex.query.value.node`): Single `FNode` hierarchy for all
  node types (XML elements, JSON nodes, function items). ~8 classes, ~2000 lines.
  Key insight: JSON nodes are first-class nodes with document ordering.
- **Saxon**: Separate `NodeInfo` interface implemented by both XML and JSON
  node implementations. More similar to our GNode approach.

## Open Questions

1. **Document ordering across JSON trees**: How should two independent JNode
   trees be ordered relative to each other? BaseX assigns synthetic document
   IDs. We need the same for `<<` / `>>` operators.
2. **Schema typing**: JNode values are untyped. Should `jnode(*, xs:integer)`
   check the dynamic type of the value, or require schema annotation?
3. **Mutability**: XQuery Update on JNodes? BaseX supports it. For v4, we
   could defer this.
4. **Serialization**: JNode trees in `fn:serialize()` with method=json. Our
   current JNode serializer works but isn't integrated with the main
   serialization pipeline.
