# Compiler architecture contract

The compiler has a frontend, a shared immutable IR, target backends, and an
artifact writer. The VM is a separate runtime module.

```text
source text
  -> lexer and parser
  -> symbol declaration and name resolution
  -> type checking and language/control-flow checks
  -> normalization into IR and IR verification
  -> CheckedProgram
  -> target validation
  -> target-specific layout and PreparedProgram
  -> OutputArtifact
  -> ArtifactWriter
```

## Ownership and outcomes

The frontend alone owns mutable AST nodes, scopes, and symbols. `IrLowering`
copies their semantic data into records, immutable lists, immutable types and
integer identities. `CheckedProgram` exposes only this verified snapshot. Source
locations survive lowering. Nothing in `ir` depends on a backend or the parser.

Every frontend run creates its own symbols from immutable runtime signatures.
The signatures describe primitive/array types without referring to mutable
frontend symbols. A program is checked against the chosen target's signatures;
it can be prepared for another target only if that target provides compatible
runtime functions and supports the operations in the program.

`PhaseResult.Success<T>` contains a non-null result. `PhaseResult.Failure<T>`
contains an immutable diagnostic snapshot with at least one error. No failed
preparation exposes a usable `PreparedProgram`. The caller checks the result
before consuming it. Ordinary source or target limitations are error diagnostics;
broken compiler invariants use `CompilerInvariantException` and severe diagnostics.
Each diagnostic retains its originating phase.

The final `TargetBackend.prepare()` method enforces validation before layout.
Backends keep no per-compilation state. `VmPreparedProgram` owns the immutable
IR and frozen VM metadata. Each emission creates a fresh generator, making
repeated preparations and emissions independent.

## Language semantics

- Exactly one source function must have the signature `fn void main()`.
- Operands, receivers, indices, dimensions and function arguments evaluate left
  to right. Conditional branches and short-circuit expressions evaluate only the
  selected branch.
- An assignment or increment evaluates its target address once. Compound
  assignments read the old value before evaluating the right operand. A postfix
  increment returns the old value; a prefix increment returns the updated value.
- Every executed variable declaration establishes its initializer or language
  default. A declaration inside a loop reinitializes the variable on each visit.
- `int` is a signed 32-bit value with wrapping overflow. `double` is IEEE-754
  binary64. Existing runtime errors for division by zero are retained.
- The existing explicit unchecked casts remain part of the language. IR casts
  carry both operand and result types; backends must implement or reject them.
- `break` and `continue` refer to the nearest loop. Switch cases have no implicit
  fallthrough. `do while` executes its body before checking the condition.
- Existing string contents, reference/null behavior, arrays and struct allocation
  semantics are preserved; physical representations belong to each target.

A target must preserve these semantics. It may emulate an operation or report
that it is unsupported. Silently replacing integer arithmetic with target-native
decimal/floating arithmetic is not permitted by this contract.

## The structured IR

`Ir.Program` contains immutable function signatures and definitions, structs,
enums and the fixed language semantics. Symbols use stable integer identities;
names are display/runtime-linkage information, not storage addresses.

`Ir.Type` describes language values and nominal types. The internal `ADDRESS`
type represents an assignable location; it is separate from a struct or array
value. Backends select its physical representation. Storage definitions describe
parameters, locals, compiler temporaries and fields without byte sizes or offsets.

Statements are blocks, stores, evaluations, structured branches/loops/switches,
and abrupt exits. Expressions include literals, address calculations, loads,
typed arithmetic, explicit conversions, conditional values, calls, allocation,
length operations and sequences. The sealed node sets permit exhaustive Java
switches: a new node requires emitter/traversal updates at compile time.

Normalization performs these transformations once for all targets:

- `let int x;` becomes a store of the default integer value.
- An implicit numeric conversion becomes an explicit `Convert` node.
- `&&` and `||` become conditional expressions with short-circuit branches.
- Variadic tail arguments become an array argument unless an existing compatible
  array is passed directly.
- Assignment and increment targets are captured in temporaries. For example,
  `a[i++]++` captures the address once, reads its old value, calculates the new
  value, stores it and returns the saved old value.

`Ir.Sequence` executes its effects before yielding its result. Temporary writes
and loads make evaluation order explicit without requiring SSA. The IR retains
structured control flow so targets can choose native structured constructs or
lower them into branches. There is currently no general optimization pipeline.

`IrVerifier` checks symbol ownership, signatures, operand/result types, explicit
conversions, source locations and loop exit ownership. `--dump-ir` exposes this
representation; `--dump-symboltable` displays prepared layouts.

## Adding a target

1. Extend `TargetBackend` in a target-specific package.
2. Return immutable `BuiltInSignature` descriptions for the runtime functions.
3. Implement `validate(Ir.Program, DiagnosticBag)`. Report unsupported types,
   operations, casts, recursion or resource limits at the relevant IR source.
   `IrWalker` provides exhaustive traversal for this analysis.
4. Implement `prepareValidated()` to create target-owned layout data and a
   `PreparedProgram`. Never modify the IR or reuse mutable state between programs.
5. Emit the sealed IR operations exhaustively. Unsupported features must already
   have been rejected. Internal inconsistencies must produce invariant diagnostics.
6. Return an `OutputArtifact`. Do not read or write destination files in emitters.
7. Register the target name and extension in `CompilationTarget`, then its factory
   in `TargetBackendFactory`.

FANUC deliberately remains a placeholder that emits an empty artifact. Its
validator is the designated location for future FANUC-specific restrictions.
The placeholder does not claim semantic execution support.

The writer uses a temporary file beside the destination and atomically replaces
the destination where supported. Analysis, validation or generation failures do
not overwrite existing output. Output artifacts support both text and binary data.
