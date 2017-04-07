package com.huntero.injector.compiler;

import com.huntero.injector.annotations.permission.ApplyPermissions;
import com.huntero.injector.annotations.permission.PermissionRequest;
import com.huntero.injector.annotations.permission.internal.PermissionAnnotationType;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.lang.model.element.Modifier;

/**
 * Created by huntero on 17-3-30.
 */

final class BindingClass {
    private static final ClassName PERMISSIONUTILS =  ClassName.get("com.huntero.injector","PermissionUtils");
    private static final ClassName ACTIVITYCOMPAT = ClassName.get("android.support.v4.app", "ActivityCompat");

    private static final String PREFIX_REQUEST_CODE = "REQUEST_CODE_";
    private static final String PREFIX_PERMISSION = "PERMISSIONS_";
    private static final String SUFFIX_INVOKE_METHOD = "WithCheck";

    private final Map<String, PermissionBinding> permissionBindingMap = new LinkedHashMap<>();

    private final TypeName targetTypeName;
    private final ClassName generatedClassName;

    BindingClass(TypeName targetTypeName, ClassName generatedClassName) {
        this.targetTypeName = targetTypeName;
        this.generatedClassName = generatedClassName;
    }

    public boolean addPermission(String[] permissions, PermissionAnnotationType permissionType, MethodPermission targetMethod) {
        final String permissionKey = generatePermissionKey(permissions);
        PermissionBinding binding = permissionBindingMap.get(permissionKey);
        if (binding != null) {
            try {
                Field field = binding.getClass().getDeclaredField(permissionType.field());
                field.setAccessible(true);
                if(field.get(binding) != null){
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        } else {
            binding = new PermissionBinding();
            binding.setPermissions(permissions);
            permissionBindingMap.put(permissionKey, binding);
        }
        try {
            Method setMethod = binding.getClass().getDeclaredMethod(
                    "set" + StringUtils.upperFirstLetter(permissionType.field()),
                    MethodPermission.class);
            setMethod.invoke(binding, targetMethod);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    JavaFile brewJava() {
        TypeSpec.Builder result = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(Modifier.FINAL);

        int codeIndex = 1;
        CodeBlock.Builder switchCase = CodeBlock.builder();

        for (Map.Entry<String, PermissionBinding> entry : permissionBindingMap.entrySet()) {
            PermissionBinding binding = entry.getValue();
            if (binding.getMethodForApply() == null) {
                throw new IllegalStateException(
                        String.format("@%s annotation in class %s not found", ApplyPermissions.class.getSimpleName(),
                                generatedClassName.simpleName()));
            }

            final String upperKeyWords = binding.getMethodForApply().getName().toUpperCase();
            //Permission string array const
            FieldSpec permissionsField = FieldSpec.builder(String[].class, PREFIX_PERMISSION + upperKeyWords)
                    .addModifiers(Modifier.FINAL, Modifier.STATIC)
                    .initializer(formatArrayCode(binding.getPermissions())).build();
            result.addField(permissionsField);

            //Permission request code const
            FieldSpec requestCodeField = FieldSpec.builder(int.class, PREFIX_REQUEST_CODE + upperKeyWords)
                    .addModifiers(Modifier.STATIC, Modifier.FINAL).initializer("$L", codeIndex)
                    .build();
            result.addField(requestCodeField);

            //The main method to be called
            createMainInvokeMethod(result, binding, permissionsField, requestCodeField);

            //OnRequestPermissionResult switch case code
            CodeBlock.Builder logicCode = createCaseLogic(binding, permissionsField);
            switchCase.add("case $N:\n", requestCodeField).add(logicCode.build())
                    .addStatement("break");

            codeIndex++;
        }

        ParameterSpec requestCode = ParameterSpec.builder(int.class, "requestCode").build();
        ParameterSpec grantResults = ParameterSpec.builder(int[].class, "grantResults").build();
        MethodSpec.Builder onResultMethod = MethodSpec.methodBuilder("onRequestPermissionsResult")
                .addModifiers(Modifier.STATIC).returns(void.class)
                .addParameter(targetTypeName, "target").addParameter(requestCode)
                .addParameter(grantResults)
                .addCode("switch ($N) {\n", requestCode)
                .addCode(switchCase.build())
                .addCode("}\n");
        result.addMethod(onResultMethod.build());

        return JavaFile.builder(generatedClassName.packageName(), result.build())
                .addFileComment("Generated code from Huntero Inject. Do not modify!").build();
    }

    private void createMainInvokeMethod(TypeSpec.Builder result, PermissionBinding binding,
            FieldSpec permissionsField, FieldSpec requestCodeField) {
        MethodSpec.Builder checkMethod = MethodSpec.methodBuilder(binding.getMethodForApply().getName() + SUFFIX_INVOKE_METHOD)
                .addModifiers(Modifier.STATIC).returns(void.class)
                .addParameter(targetTypeName, "target", Modifier.FINAL)
                .beginControlFlow("if($T.hasSelfPermissions(target, $N))", PERMISSIONUTILS,
                        permissionsField).addStatement("target.$N()", binding.getMethodForApply().getName())
                .nextControlFlow("else");

        final MethodPermission methodForShowRationale = binding.getMethodForShowRationale();
        if (methodForShowRationale != null) {
            checkMethod.beginControlFlow("if($T.shouldShowRequestPermissionRationale(target, $N))",
                    PERMISSIONUTILS, permissionsField);
            if (methodForShowRationale.getParameters().size() > 0) {
                //Create dialog callback class implement PermissionRequest interface
                TypeSpec rationaleCallback = createRationaleCallback(binding, permissionsField,
                        requestCodeField);
                result.addType(rationaleCallback);

                checkMethod.addStatement("target.$N(new $N(target))",
                        methodForShowRationale.getName(), rationaleCallback);
            } else {
                checkMethod.addStatement("target.$N()",
                        methodForShowRationale.getName());
            }
            checkMethod.nextControlFlow("else")
                        .addStatement("$T.requestPermissions(target,$N,$N)", ACTIVITYCOMPAT,
                            permissionsField, requestCodeField).endControlFlow();
        } else {
            checkMethod.addStatement("$T.requestPermissions(target,$N,$N)", ACTIVITYCOMPAT,
                    permissionsField, requestCodeField);
        }
        checkMethod.endControlFlow();
        result.addMethod(checkMethod.build());
    }

    private TypeSpec createRationaleCallback(PermissionBinding binding, FieldSpec permissionsField,
            FieldSpec requestCodeField) {
        final String generateSubClassName =  StringUtils.upperFirstLetter(binding.getMethodForApply().getName()) + "PermissionRequest";
        TypeSpec.Builder rationaleCallback = TypeSpec.classBuilder(generateSubClassName)
                .addModifiers(Modifier.STATIC, Modifier.FINAL, Modifier.PRIVATE);
        rationaleCallback.addSuperinterface(PermissionRequest.class);

        ClassName weakRefClass = ClassName.get("java.lang.ref", "WeakReference");
        TypeName weakRefType = ParameterizedTypeName.get(weakRefClass, targetTypeName);
        FieldSpec context = FieldSpec.builder(weakRefType, "weakTarget")
                .addModifiers(Modifier.FINAL).build();
        rationaleCallback.addField(context);

        MethodSpec constructor = MethodSpec.constructorBuilder().addParameter(targetTypeName, "target")
                .addStatement("this.weakTarget = new $T(target)", weakRefType).build();
        rationaleCallback.addMethod(constructor);

        CodeBlock proceedCode = CodeBlock.builder().addStatement("$T target = $N.get()", targetTypeName, context)
                .addStatement("if (target == null) return")
                .addStatement("$T.requestPermissions(target,$N,$N)", ACTIVITYCOMPAT, permissionsField, requestCodeField)
                .build();
        MethodSpec proceed = MethodSpec.methodBuilder("proceed").addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(void.class)
                .addCode(proceedCode).build();
        rationaleCallback.addMethod(proceed);

        CodeBlock cancelCode = null;
        if(binding.getMethodForDenied() != null) {
             cancelCode = CodeBlock.builder()
                    .addStatement("$T target = $N.get()", targetTypeName, context).addStatement("if (target == null) return")
                    .addStatement("target.$N()", binding.getMethodForDenied().getName()).build();
        }
        MethodSpec.Builder cancel = MethodSpec.methodBuilder("cancel").addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(void.class);
        if(cancelCode != null) {
            cancel.addCode(cancelCode);
        }
        rationaleCallback.addMethod(cancel.build());

        return rationaleCallback.build();
    }

    /*
    onRequestPermissionResult -> switch -> case
     */
    private CodeBlock.Builder createCaseLogic(PermissionBinding binding,
            FieldSpec permissionsField) {
        CodeBlock.Builder logicCode = CodeBlock.builder();
        logicCode.beginControlFlow("if($T.getTargetSdkVersion(target) < 23 && !$T.hasSelfPermissions(target, $N))",
                PERMISSIONUTILS,PERMISSIONUTILS,permissionsField);
        if(binding.getMethodForDenied() != null) {
            logicCode.addStatement("target.$N()", binding.getMethodForDenied().getName());
        }
        logicCode.addStatement("return").endControlFlow();

        logicCode.beginControlFlow("if ($T.verifyPermissions(grantResults))",PERMISSIONUTILS)
                .addStatement("target.$N()", binding.getMethodForApply().getName());

        if (binding.getMethodForNeverAskAgain() != null) {
            logicCode.nextControlFlow("else")
                    .beginControlFlow("if (!$T.shouldShowRequestPermissionRationale(target, $N))",
                            PERMISSIONUTILS, permissionsField)
                    .addStatement("target.$N()", binding.getMethodForNeverAskAgain().getName());
        }
        if (binding.getMethodForDenied() != null) {
            logicCode.nextControlFlow("else").addStatement("target.$N()", binding.getMethodForDenied().getName());
        }
        if (binding.getMethodForNeverAskAgain() != null) {
            logicCode.endControlFlow();
        }

        logicCode.endControlFlow();
        return logicCode;
    }

    private CodeBlock formatArrayCode(String[] arrays) {
        CodeBlock.Builder block = CodeBlock.builder();
        block.add("new String[]{");
        for (int i = 0; i < arrays.length; i++) {
            block.add("$S",arrays[i]);
            if (i != arrays.length - 1) {
                block.add(", ");
            }
        }
        block.add("}");
        return block.build();
    }

    private static String generatePermissionKey(String[] permissions) {
        Arrays.sort(permissions);
        return Encrypt.Md5(Arrays.toString(permissions));
    }
}
