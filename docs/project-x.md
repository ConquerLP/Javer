# Project-X SPS VM contract

Compile with --target project-x. Output uses .jbc and all existing VM instructions,
layouts, types and evaluation semantics. The Java VM does not execute the new instructions.

## Additional built-ins / stack operands (bottom to top)

| Built-in | Instruction | Operands | Result |
|---|---|---|---|
| move_fast(x,y,z) | MOVE_FAST | double x,y,z | void |
| move_linear(x,y,z,feed) | MOVE_LINEAR | double x,y,z; int feed | void |
| move_cw(x,y,z,r,feed) | MOVE_CW | double x,y,z,r; int feed | void |
| move_ccw(x,y,z,r,feed) | MOVE_CCW | double x,y,z,r; int feed | void |
| wait_ms(duration) | WAIT_MS | int duration | void |
| driveBool(device,action,value) | BDRIVE | string device,action; bool value | void |
| driveInt(device,action,value) | IDRIVE | string device,action; int value | void |
| driveDouble(device,action,value) | DDRIVE | string device,action; double value | void |
| readBool(device,property) | BREAD | string device,property | bool |
| readInt(device,property) | IREAD | string device,property | int |
| readDouble(device,property) | DREAD | string device,property | double |

Each device instruction is a distinct opcode without an immediate type operand. Arguments evaluate left to right. Every instruction consumes all operands.
BREAD/IREAD/DREAD push one value using the existing VM representation. Strings use existing
VM string references and may be computed expressions.

## Movement and completion

Coordinates are absolute millimetres; feed is an integer in millimetres/minute.
MOVE_FAST has no feed operand: the SPS chooses rapid speed.
Arcs use G17 (XY). The SPS computes geometry from the target and radius:
positive radius selects the short arc (up to 180 degrees), negative the long arc.
Coincident start/end points (full circles) are unsupported initially.

Movement, BDRIVE/IDRIVE/DDRIVE and WAIT_MS start an operation exactly once and suspend program
progress until completion while SPS cycles continue. Operands must not be consumed
again or operations restarted on later cycles. Completion of BDRIVE/IDRIVE/DDRIVE is defined by
the device binding (e.g. cylinder end position or confirmed laser power).
Persistent outputs stay in their commanded state after completion.
BREAD/IREAD/DREAD return a current typed property value.

WAIT_MS uses integer milliseconds; zero completes immediately, negative is an error.
The SPS validates geometry, numeric values, device IDs, actions and property types.
Unknown devices/actions/properties, type mismatch, failed operations and timeouts
stop execution with a runtime error. Device bindings own completion and timeout policy.

New devices require SPS configuration or device integration, but no instruction-set
or interpreter changes when representable by these operations and value types.
