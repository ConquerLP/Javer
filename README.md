# Javer

Private fork of Javer with a simplified production-only build.

## Requirements

- JDK 21
- Windows for the `windows-release` export profile

## Build for IDE use

```powershell
.\mvnw.cmd clean package
```

This builds:

- `Compiler/target/Compiler-1.0-SNAPSHOT-all.jar`
- `VM/target/VM-1.0-SNAPSHOT-all.jar`
- `Application/target/Application-1.0-SNAPSHOT-all.jar`

The application build also copies the compiler and VM jars to `Application/target/tools`.

## Export Windows app image

```powershell
.\mvnw.cmd clean verify -Pwindows-release
```

The local app image is created at:

```text
Application/target/installer/Application/
```
