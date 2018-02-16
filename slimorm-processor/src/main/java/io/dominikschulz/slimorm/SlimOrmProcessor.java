package io.dominikschulz.slimorm;


import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes({
        "io.dominikschulz.slimorm.Field"
})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class SlimOrmProcessor extends AbstractProcessor {

    private static final ClassName LIST_TYPE = ClassName.get("java.util", "List");
    private static final ClassName CURSORUTILS_TYPE = ClassName.get("io.dominikschulz.slimorm", "CursorUtils");
    private static final ClassName ARRAY_LIST_TYPE = ClassName.get("java.util", "ArrayList");
    private static final ArrayTypeName BYTE_ARRAY_TYPE = ArrayTypeName.of(TypeName.BYTE);
    private static final ClassName STRING_TYPE = ClassName.get(String.class);

    private Messager messager;
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        generateCursorUtils();

        Map<TypeElement, List<VariableElement>> toBeProcessed = new HashMap<>();

        for (Element element : roundEnv.getElementsAnnotatedWith(Field.class)) {

            if (element.getKind() == ElementKind.FIELD) {

                if (element.getModifiers().contains(Modifier.PRIVATE)) {
                    throwError("SlimOrm can only work with package protected fields");
                }

                final Element enclosingElement = element.getEnclosingElement();
                if (enclosingElement.getKind() == ElementKind.CLASS) {
                    final TypeElement classElement = (TypeElement) enclosingElement;

                    if (!toBeProcessed.containsKey(classElement)) {
                        toBeProcessed.put(classElement, new ArrayList<VariableElement>());
                    }

                    List<VariableElement> variableElements = toBeProcessed.get(classElement);
                    variableElements.add((VariableElement) element);
                }

            }
        }

        for (Map.Entry<TypeElement, List<VariableElement>> entry : toBeProcessed.entrySet()) {
            generateClass(entry.getKey(), entry.getValue());
        }

        return false;
    }

    private void generateCursorUtils() {
        final TypeSpec.Builder classBuilder = TypeSpec.classBuilder("CursorUtils").addModifiers(Modifier.PUBLIC);

        // Primitive Types supported by Cursor
        addPrimitiveReadMethod(classBuilder, "readInt", TypeName.INT, "0");
        addPrimitiveReadMethod(classBuilder, "readFloat", TypeName.FLOAT, "0");
        addPrimitiveReadMethod(classBuilder, "readDouble", TypeName.DOUBLE, "0");
        addPrimitiveReadMethod(classBuilder, "readShort", TypeName.SHORT, "0");
        addPrimitiveReadMethod(classBuilder, "readLong", TypeName.LONG, "0");
        addPrimitiveReadMethod(classBuilder, "readBoolean", TypeName.BOOLEAN, "false");


        // Non Primitive Types and Boxed version supported by Cursor also includes blob and String
        addNonPrimitiveReadMethod(classBuilder, "readBoxedInt", TypeName.INT);
        addNonPrimitiveReadMethod(classBuilder, "readBoxedFloat", TypeName.FLOAT);
        addNonPrimitiveReadMethod(classBuilder, "readBoxedDouble", TypeName.DOUBLE);
        addNonPrimitiveReadMethod(classBuilder, "readBoxedShort", TypeName.SHORT);
        addNonPrimitiveReadMethod(classBuilder, "readBoxedLong", TypeName.LONG);
        addNonPrimitiveReadMethod(classBuilder, "readBoxedBoolean", TypeName.BOOLEAN);

        addStringReadMethod(classBuilder, "readString");
        addBlobReadMethod(classBuilder, "readBlob");

        try {
            JavaFile.builder("io.dominikschulz.slimorm", classBuilder.build())
                    .build()
                    .writeTo(filer);
        } catch (IOException e) {
        }
    }

    private void addBlobReadMethod(TypeSpec.Builder classBuilder, String methodName) {
        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(BYTE_ARRAY_TYPE)
                .addParameter(ClassName.get("android.database", "Cursor"), "cursor")
                .addParameter(STRING_TYPE, "columnName");

        methodBuilder.addStatement("$T columnIndex = cursor.getColumnIndex(columnName)", TypeName.INT);
        methodBuilder.addCode("if (columnIndex >= 0) {\n");

        methodBuilder.addCode("\tif (!cursor.isNull(columnIndex)) {\n");

        methodBuilder.addStatement("\t\treturn cursor.getBlob(columnIndex)");

        methodBuilder.addCode("\t} else {\n");
        methodBuilder.addStatement("\t\treturn $L", "null");
        methodBuilder.addCode("\t}\n");

        methodBuilder.addCode("} else {\n");
        methodBuilder.addStatement("\treturn $L", "null");
        methodBuilder.addCode("}\n");

        classBuilder.addMethod(methodBuilder.build());
    }

    private void addStringReadMethod(TypeSpec.Builder classBuilder, String methodName) {
        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(STRING_TYPE)
                .addParameter(ClassName.get("android.database", "Cursor"), "cursor")
                .addParameter(STRING_TYPE, "columnName");

        methodBuilder.addStatement("$T columnIndex = cursor.getColumnIndex(columnName)", TypeName.INT);
        methodBuilder.addCode("if (columnIndex >= 0) {\n");

        methodBuilder.addCode("\tif (!cursor.isNull(columnIndex)) {\n");

        methodBuilder.addStatement("\t\treturn cursor.getString(columnIndex)");

        methodBuilder.addCode("\t} else {\n");
        methodBuilder.addStatement("\t\treturn $L", "null");
        methodBuilder.addCode("\t}\n");

        methodBuilder.addCode("} else {\n");
        methodBuilder.addStatement("\treturn $L", "null");
        methodBuilder.addCode("}\n");

        classBuilder.addMethod(methodBuilder.build());
    }

    private void addNonPrimitiveReadMethod(TypeSpec.Builder classBuilder, String methodName, TypeName type) {
        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(type.box())
                .addParameter(ClassName.get("android.database", "Cursor"), "cursor")
                .addParameter(STRING_TYPE, "columnName");

        methodBuilder.addStatement("$T columnIndex = cursor.getColumnIndex(columnName)", TypeName.INT);
        methodBuilder.addCode("if (columnIndex >= 0) {\n");

        methodBuilder.addCode("\tif (!cursor.isNull(columnIndex)) {\n");

        if (type == TypeName.BOOLEAN) {
            methodBuilder.addStatement("\t\treturn cursor." + mapTypeToCursorGetMethod(type) + "(columnIndex) == 1");
        } else {
            methodBuilder.addStatement("\t\treturn cursor." + mapTypeToCursorGetMethod(type) + "(columnIndex)");
        }

        methodBuilder.addCode("\t} else {\n");
        methodBuilder.addStatement("\t\treturn $L", "null");
        methodBuilder.addCode("\t}\n");

        methodBuilder.addCode("} else {\n");
        methodBuilder.addStatement("\treturn $L", "null");
        methodBuilder.addCode("}\n");

        classBuilder.addMethod(methodBuilder.build());
    }

    private void addPrimitiveReadMethod(TypeSpec.Builder classBuilder, String methodName, TypeName primitiveType, String defaultValue) {
        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(primitiveType)
                .addParameter(ClassName.get("android.database", "Cursor"), "cursor")
                .addParameter(STRING_TYPE, "columnName");

        methodBuilder.addStatement("$T columnIndex = cursor.getColumnIndex(columnName)", TypeName.INT);
        methodBuilder.addCode("if (columnIndex >= 0) {\n");

        if (primitiveType == TypeName.BOOLEAN) {
            methodBuilder.addStatement("\treturn cursor." + mapTypeToCursorGetMethod(primitiveType) + "(columnIndex) == 1");
        } else {
            methodBuilder.addStatement("\treturn cursor." + mapTypeToCursorGetMethod(primitiveType) + "(columnIndex)");
        }

        methodBuilder.addCode("} else {\n");
        methodBuilder.addStatement("\treturn $L", defaultValue);
        methodBuilder.addCode("}\n");


        classBuilder.addMethod(methodBuilder.build());
    }

    private void log(String message) {
        messager.printMessage(Diagnostic.Kind.NOTE, message);
    }

    private void generateClass(TypeElement typeElement, List<VariableElement> listOfVariables) {

        final ClassName pojoType = ClassName.get(typeElement);

        TypeSpec.Builder classBuilder = TypeSpec.
                classBuilder(typeElement.getSimpleName() + "Converter")
                .addModifiers(Modifier.PUBLIC);

        classBuilder.addMethod(generateSingleRowParseMethod(typeElement, listOfVariables, "toSingleRow", Modifier.PUBLIC));
        classBuilder.addMethod(generateSingleRowParseMethod(typeElement, listOfVariables, "parseToSingleRow", Modifier.PUBLIC, Modifier.STATIC));

        classBuilder.addMethod(generateListParseMethod(pojoType, "toList", "toSingleRow", Modifier.PUBLIC));
        classBuilder.addMethod(generateListParseMethod(pojoType, "parseToList", "parseToSingleRow", Modifier.PUBLIC, Modifier.STATIC));

        classBuilder.addMethod(generateToContentValuesMethod(typeElement, listOfVariables, "toContentValues", Modifier.PUBLIC));
        classBuilder.addMethod(generateToContentValuesMethod(typeElement, listOfVariables, "parseToContentValues", Modifier.PUBLIC, Modifier.STATIC));

        try {
            JavaFile.builder(pojoType.packageName(), classBuilder.build())
                    .build()
                    .writeTo(filer);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private MethodSpec generateToContentValuesMethod(TypeElement typeElement, List<VariableElement> listOfVariables, String methodName, Modifier... modifier) {
        final String parameterName = typeElement.getSimpleName().toString().toLowerCase();
        final ClassName contentValuesClassName = ClassName.get("android.content", "ContentValues");

        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(modifier)
                .returns(contentValuesClassName)
                .addParameter(ClassName.get(typeElement), parameterName)
                .addStatement("$T contentValues = new $T()", contentValuesClassName, contentValuesClassName);

        for (VariableElement variableElement : listOfVariables) {
            Field field = variableElement.getAnnotation(Field.class);
            methodBuilder.addStatement("contentValues.put($S, $L)", field.columnName(), parameterName + "." + variableElement.getSimpleName());
        }

        methodBuilder.addStatement("return contentValues");
        methodBuilder.addJavadoc("Converts the provided " + parameterName + " to ContentValues");
        methodBuilder.addJavadoc("\n@param " + parameterName + " to convert values from");
        methodBuilder.addJavadoc("\n@returns {@code $T} with values converted from {@code $T}", contentValuesClassName, typeElement);

        return methodBuilder.build();
    }

    private MethodSpec generateListParseMethod(ClassName pojoType, String methodName, String singleParseMethodName, Modifier... modifier) {
        // Add list parser
        TypeName listOfPojo = ParameterizedTypeName.get(LIST_TYPE, pojoType);

        return MethodSpec.methodBuilder(methodName)
                .addModifiers(modifier)
                .returns(listOfPojo)
                .addParameter(ClassName.get("android.database", "Cursor"), "cursor")
                .addStatement("$T list = new $T<>()", listOfPojo, ARRAY_LIST_TYPE)
                .addCode("while (cursor.moveToNext()) {\n")
                .addStatement("     list.add($L(cursor))", singleParseMethodName)
                .addCode("}\n")
                .addStatement("return list")
                .addJavadoc("Converts the {@code cursor} to {@code $T}, \nmake sure the cursor is in the correct initial position", listOfPojo)
                .addJavadoc("\n@param cursor to convert values from")
                .addJavadoc("\n@returns {@code $T} with values converted from {@code cursor}", listOfPojo)
                .build();
    }

    private MethodSpec generateSingleRowParseMethod(TypeElement typeElement, List<VariableElement> listOfVariables, String methodName, Modifier... modifier) {
        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(modifier)
                .returns(ClassName.get(typeElement))
                .addParameter(ClassName.get("android.database", "Cursor"), "cursor");

        methodBuilder.addStatement("$T row = new $T()", typeElement, typeElement);

        for (VariableElement variableElement : listOfVariables) {

            checkIfTypeIsSupported(variableElement);

            if (ClassName.get(variableElement.asType()).isPrimitive()) {
                addPrimitiveType(variableElement, methodBuilder);
            } else {
                addNonPrimitiveType(variableElement, methodBuilder);
            }
        }

        methodBuilder.addStatement("return row");
        methodBuilder.addJavadoc("Converts the {@code cursor} in its current position to an $T, \nmake sure the cursor is in the correct position", typeElement);
        methodBuilder.addJavadoc("\n@param cursor to convert values from");
        methodBuilder.addJavadoc("\n@returns $T with values converted from {@code cursor}", typeElement);

        return methodBuilder.build();
    }

    private void checkIfTypeIsSupported(VariableElement variableElement) {
        final TypeName typeOfCurrentElement = ClassName.get(variableElement.asType());
        if (!typeOfCurrentElement.equals(STRING_TYPE) &&
                !typeOfCurrentElement.equals(BYTE_ARRAY_TYPE) &&
                !typeOfCurrentElement.isPrimitive() &&
                !typeOfCurrentElement.isBoxedPrimitive()) {

            throwError("SlimOrm: Type " + typeOfCurrentElement + " is not supported");
        } else if ((typeOfCurrentElement.isPrimitive() || typeOfCurrentElement.isBoxedPrimitive())
                && (typeOfCurrentElement.unbox().equals(TypeName.BYTE) || typeOfCurrentElement.unbox().equals(TypeName.CHAR))) {

            throwError("SlimOrm: Type " + typeOfCurrentElement + " is not supported");
        }
    }

    private void throwError(String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message);
        throw new UnsupportedOperationException(message);
    }

    private void addNonPrimitiveType(VariableElement element, MethodSpec.Builder methodBuilder) {

        Field field = element.getAnnotation(Field.class);
        final TypeName typeOfCurrentElement = ClassName.get(element.asType());

        if (typeOfCurrentElement.equals(BYTE_ARRAY_TYPE)) {
            methodBuilder.addStatement("row.$L = $T." + mapTypeToCursorUtilNonPrimitiveReadMethod(typeOfCurrentElement) + "(cursor, $S)", element.getSimpleName(), CURSORUTILS_TYPE, field.columnName());
        } else if (typeOfCurrentElement.equals(STRING_TYPE)) {
            methodBuilder.addStatement("row.$L = $T." + mapTypeToCursorUtilNonPrimitiveReadMethod(typeOfCurrentElement) + "(cursor, $S)", element.getSimpleName(), CURSORUTILS_TYPE, field.columnName());
        } else {
            methodBuilder.addStatement("row.$L = $T." + mapTypeToCursorUtilNonPrimitiveReadMethod(typeOfCurrentElement.unbox()) + "(cursor, $S)", element.getSimpleName(), CURSORUTILS_TYPE, field.columnName());
        }
    }

    private void addPrimitiveType(VariableElement element, MethodSpec.Builder methodBuilder) {
        Field field = element.getAnnotation(Field.class);
        final TypeName typeName = ClassName.get(element.asType());
        methodBuilder.addStatement("row.$L = $T." + mapTypeToCursorUtilReadMethod(typeName) + "(cursor, $S)", element.getSimpleName(), CURSORUTILS_TYPE, field.columnName());
    }

    private String mapTypeToCursorUtilReadMethod(TypeName typeName) {
        if (typeName == TypeName.BOOLEAN) {
            return "readBoolean";
        } else if (typeName == TypeName.SHORT) {
            return "readShort";
        } else if (typeName == TypeName.INT) {
            return "readInt";
        } else if (typeName == TypeName.LONG) {
            return "readLong";
        } else if (typeName == TypeName.FLOAT) {
            return "readFloat";
        } else if (typeName == TypeName.DOUBLE) {
            return "readDouble";
        }
        return "";
    }

    private String mapTypeToCursorUtilNonPrimitiveReadMethod(TypeName typeName) {
        if (typeName == TypeName.BOOLEAN) {
            return "readBoxedBoolean";
        } else if (typeName == TypeName.SHORT) {
            return "readBoxedShort";
        } else if (typeName == TypeName.INT) {
            return "readBoxedInt";
        } else if (typeName == TypeName.LONG) {
            return "readBoxedLong";
        } else if (typeName == TypeName.FLOAT) {
            return "readBoxedFloat";
        } else if (typeName == TypeName.DOUBLE) {
            return "readBoxedDouble";
        } else if (typeName.equals(BYTE_ARRAY_TYPE)) {
            return "readBlob";
        } else if (typeName.equals(STRING_TYPE)) {
            return "readString";
        }
        return "";
    }

    private String mapTypeToCursorGetMethod(TypeName typeName) {
        if (typeName == TypeName.BOOLEAN) {
            return "getInt";
        } else if (typeName == TypeName.SHORT) {
            return "getShort";
        } else if (typeName == TypeName.INT) {
            return "getInt";
        } else if (typeName == TypeName.LONG) {
            return "getLong";
        } else if (typeName == TypeName.FLOAT) {
            return "getFloat";
        } else if (typeName == TypeName.DOUBLE) {
            return "getDouble";
        }
        return "";
    }

}
