package ch.zhaw.it.pm4.javer.compiler.builtin;

import ch.zhaw.it.pm4.javer.compiler.ast.scope.EnumScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.GlobalScope;
import ch.zhaw.it.pm4.javer.compiler.ast.scope.StructScope;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.EnumValueEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.FieldEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.symbol.StructEntry;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.EnumTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.PrimitiveTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.StructTypeInfo;
import ch.zhaw.it.pm4.javer.compiler.ast.typeinfo.TypeInfo;
import ch.zhaw.it.pm4.javer.compiler.bytecode.VmLayout;

import java.util.List;

/**
 * Runtime-visible compiler-provided types used for primitive boxing.
 */
public final class BuiltInTypes {

    public static final String DATA_TYPE_NAME = "DataType";
    public static final String OBJECT_NAME = "Object";
    public static final String TYPE_FIELD_NAME = "type";
    public static final String VALUE_FIELD_NAME = "value";

    private static final List<WrapperType> WRAPPERS = List.of(
            new WrapperType("Int", "INT", PrimitiveTypeInfo.INT),
            new WrapperType("String", "STRING", PrimitiveTypeInfo.STRING),
            new WrapperType("Char", "CHAR", PrimitiveTypeInfo.CHAR),
            new WrapperType("Double", "DOUBLE", PrimitiveTypeInfo.DOUBLE),
            new WrapperType("Boolean", "BOOLEAN", PrimitiveTypeInfo.BOOL)
    );

    private BuiltInTypes() {
    }

    public static void register(GlobalScope globalScope) {
        EnumEntry dataType = createDataTypeEnum();
        globalScope.defineEnum(dataType);

        StructEntry object = createObjectStruct(dataType);
        globalScope.defineStruct(object);

        for (WrapperType wrapperType : WRAPPERS) {
            globalScope.defineStruct(createWrapperStruct(wrapperType, object));
        }
    }

    public static boolean isBuiltInStructName(String name) {
        return OBJECT_NAME.equals(name) || wrapperByStructName(name) != null;
    }

    public static boolean isBuiltInEnumName(String name) {
        return DATA_TYPE_NAME.equals(name);
    }

    public static boolean isBoxingConversion(TypeInfo source, TypeInfo target) {
        if (!(source instanceof PrimitiveTypeInfo sourcePrimitive)) {
            return false;
        }
        if (!(target instanceof StructTypeInfo(StructEntry targetStruct)) || targetStruct == null) {
            return false;
        }
        if (OBJECT_NAME.equals(targetStruct.getName())) {
            return true;
        }

        WrapperType wrapperType = wrapperByStructName(targetStruct.getName());
        return wrapperType != null && wrapperType.primitiveType().equals(sourcePrimitive);
    }

    public static StructEntry wrapperStructForPrimitive(GlobalScope globalScope, TypeInfo source) {
        WrapperType wrapperType = wrapperByPrimitive(source);
        return wrapperType == null ? null : globalScope.resolveStruct(wrapperType.structName());
    }

    public static EnumValueEntry dataTypeValueForPrimitive(GlobalScope globalScope, TypeInfo source) {
        WrapperType wrapperType = wrapperByPrimitive(source);
        if (wrapperType == null) {
            return null;
        }
        EnumEntry dataType = globalScope.resolveEnum(DATA_TYPE_NAME);
        if (dataType == null || dataType.getScope() == null) {
            return null;
        }
        return dataType.getScope().resolveEnumValue(wrapperType.enumValueName());
    }

    private static EnumEntry createDataTypeEnum() {
        EnumEntry entry = new EnumEntry(DATA_TYPE_NAME);
        EnumScope scope = new EnumScope();
        entry.setScope(scope);

        int offsetBytes = 0;
        for (int i = 0; i < WRAPPERS.size(); i++) {
            WrapperType wrapperType = WRAPPERS.get(i);
            scope.defineEnumValue(new EnumValueEntry(
                    wrapperType.enumValueName(),
                    entry,
                    i,
                    VmLayout.WORD_BYTES,
                    offsetBytes,
                    entry.getDataLabel()));
            offsetBytes += VmLayout.WORD_BYTES;
        }
        entry.setSizeBytes(offsetBytes);
        return entry;
    }

    private static StructEntry createObjectStruct(EnumEntry dataType) {
        StructEntry object = new StructEntry(OBJECT_NAME);
        StructScope scope = new StructScope();
        object.setScope(scope);
        scope.defineField(new FieldEntry(
                TYPE_FIELD_NAME,
                new EnumTypeInfo(dataType),
                VmLayout.WORD_BYTES,
                0));
        return object;
    }

    private static StructEntry createWrapperStruct(WrapperType wrapperType, StructEntry object) {
        StructEntry wrapper = new StructEntry(wrapperType.structName());
        wrapper.setBaseStruct(object);

        StructScope scope = new StructScope();
        wrapper.setScope(scope);
        for (FieldEntry inheritedField : object.getScope().getFields().values()) {
            scope.defineField(inheritedField);
        }
        scope.defineField(new FieldEntry(
                VALUE_FIELD_NAME,
                wrapperType.primitiveType(),
                wrapperType.primitiveType().sizeBytes(),
                object.getSizeBytes()));
        return wrapper;
    }

    private static WrapperType wrapperByPrimitive(TypeInfo type) {
        return WRAPPERS.stream()
                .filter(wrapper -> wrapper.primitiveType().equals(type))
                .findFirst()
                .orElse(null);
    }

    private static WrapperType wrapperByStructName(String name) {
        return WRAPPERS.stream()
                .filter(wrapper -> wrapper.structName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private record WrapperType(String structName, String enumValueName, PrimitiveTypeInfo primitiveType) {
    }
}
