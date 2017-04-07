package com.huntero.injector.compiler;

import com.huntero.injector.annotations.permission.ApplyPermissions;
import com.huntero.injector.annotations.permission.OnDenied;
import com.huntero.injector.annotations.permission.OnNeverAskAgain;
import com.huntero.injector.annotations.permission.OnShowRationale;
import com.huntero.injector.annotations.permission.internal.PermissionAnnotationType;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * Created by huntero on 17-3-16.
 */
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class PermissionProcessor extends AbstractProcessor {

    private static final String BINDING_PERMISSION_CLASS_SUFFIX = "$PermissionAdapter";

    private Messager mLoger;
    private Elements elementUtils;
    private Filer filer;
    private Types typeUtils;

    private static final List<Class<? extends Annotation>> PERMISSION = Arrays.asList(
            ApplyPermissions.class,
            OnShowRationale.class,
            OnDenied.class,
            OnNeverAskAgain.class
    );

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        System.out.println("PermissionProcessor init");

        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
        filer = processingEnv.getFiler();

        mLoger = processingEnv.getMessager();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();

        for (int i = 0; i < PERMISSION.size(); i++) {
            types.add(PERMISSION.get(i).getCanonicalName());
        }

        return types;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Map<TypeElement, BindingClass> targetClassMap = findAndParseTargets(roundEnv);

        for (Map.Entry<TypeElement, BindingClass> entry : targetClassMap.entrySet()) {
            TypeElement typeElement = entry.getKey();
            BindingClass bindingClass = entry.getValue();

            try {
                bindingClass.brewJava().writeTo(filer);
            } catch (IOException e) {
                error(typeElement, "Unable to write view binder for type %s: %s", typeElement,
                        e.getMessage());
            }
        }
        return true;
    }

    private Map<TypeElement, BindingClass> findAndParseTargets(RoundEnvironment roundEnv) {
        Map<TypeElement, BindingClass> targetClassMap = new LinkedHashMap<>();

        // Process permission annotations
        for (int i = 0; i < PERMISSION.size(); i++) {
            findAndParsePermission(roundEnv, targetClassMap, PERMISSION.get(i));
        }

        return targetClassMap;
    }

    private void findAndParsePermission(RoundEnvironment roundEnv,
            Map<TypeElement, BindingClass> targetClassMap,
            Class<? extends Annotation> annotationClass) {
        for (Element element : roundEnv.getElementsAnnotatedWith(annotationClass)) {
            if (element.getKind() != ElementKind.METHOD) {
                error(element, "Only method can be annotated with @%s", annotationClass.getSimpleName());
            }
            try {
                parsePermissions(element, annotationClass, targetClassMap);
            } catch (Exception e) {
                StringWriter stackTrace = new StringWriter();
                e.printStackTrace(new PrintWriter(stackTrace));

                error(element, "Unable to generate permission adapter for @%s.\n\n%s",
                        annotationClass.getSimpleName(),
                        stackTrace.toString());
            }
        }
    }

    private void parsePermissions(Element element, Class<? extends Annotation> annotationClass, Map<TypeElement, BindingClass> targetClassMap) throws
            Exception {
        //Class type
        TypeElement classElement = (TypeElement) element.getEnclosingElement();

        //Assemble information on the method
        ExecutableElement executableElement = (ExecutableElement) element;
        String targetMethodName = executableElement.getSimpleName().toString();

        Parameter[] parameters = Parameter.NONE;
        List<? extends VariableElement> methodParameters = executableElement.getParameters();
        PermissionAnnotationType annotationType = annotationClass.getAnnotation(
                PermissionAnnotationType.class);
        final String[] parameterTypes = annotationType.parameters();
        if(methodParameters != null && methodParameters.size() > 0) {
            if (StringUtils.isEmpty(parameterTypes))
                throw new IllegalStateException(String.format("%s.%s should be no parameters.",
                        classElement.getQualifiedName(), targetMethodName));
            if (methodParameters.size() > parameterTypes.length)
                throw new IllegalStateException(String.format("@%s methods can have at most %s parameter(s). (%s.%s)",
                        annotationClass.getSimpleName(), parameterTypes.length,
                        classElement.getQualifiedName(), targetMethodName));

            parameters = new Parameter[methodParameters.size()];
            BitSet methodParameterUsed = new BitSet(methodParameters.size());
            for (int i = 0; i < methodParameters.size(); i++) {
                VariableElement methodParameter = methodParameters.get(i);
                TypeMirror methodParameterType = methodParameter.asType();
                if (methodParameterType instanceof TypeVariable) {
                    TypeVariable typeVariable = (TypeVariable) methodParameterType;
                    methodParameterType = typeVariable.getUpperBound();
                }

                for (int j = 0; j < parameterTypes.length; j++) {
                    if (methodParameterUsed.get(j)) {
                        continue;
                    }
                    if(isSubtypeOfType(methodParameterType, parameterTypes[j])){
                        parameters[i] = new Parameter(i, TypeName.get(methodParameterType));
                        methodParameterUsed.set(j);
                        break;
                    }
                }
                if (parameters[i] == null) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("Unable to match @")
                            .append(annotationClass.getSimpleName())
                            .append(" method arguments. (")
                            .append(classElement.getQualifiedName())
                            .append('.')
                            .append(element.getSimpleName())
                            .append(')');
                    for (int j = 0; j < parameters.length; j++) {
                        Parameter parameter = parameters[j];
                        builder.append("\n\n  Parameter #")
                                .append(j + 1)
                                .append(": ")
                                .append(methodParameters.get(j).asType().toString())
                                .append("\n    ");
                        if (parameter == null) {
                            builder.append("did not match any parameters");
                        } else {
                            builder.append("matched parameter #")
                                    .append(parameter.getPosition() + 1)
                                    .append(": ")
                                    .append(parameter.getType());
                        }
                    }
                    builder.append("\n\nMethods may have up to ")
                            .append(parameterTypes.length)
                            .append(" parameter(s):\n");
                    for (String parameterType : parameterTypes) {
                        builder.append("\n  ").append(parameterType);
                    }
                    builder.append(
                            "\n\nThese may be listed in any order but will be searched for from top to bottom.");
                    error(executableElement, builder.toString());
                    return;
                }
            }
        }

        Annotation annotation = executableElement.getAnnotation(annotationClass);
        Method annotationValue = annotationClass.getDeclaredMethod("value");
        if (annotationValue.getReturnType() != String[].class) {
            throw new IllegalStateException(
                    String.format("@%s annotation value() type not String[].", annotationClass));
        }
        String[] permissions = (String[]) annotationValue.invoke(annotation);

        //是否存在重复项
        String duplicated = findDuplicate(permissions);
        if (duplicated != null) {
            error(element, "@%s annotation for method contains duplicate permission %s. (%s.%s)",
                    ApplyPermissions.class.getSimpleName(),duplicated, classElement.getQualifiedName(),
                    element.getSimpleName());
            return;
        }

        MethodPermission methodPermission = new MethodPermission(targetMethodName, Arrays.asList(parameters));
        BindingClass bindingClass = getOrCreateTargetClass(targetClassMap, classElement);
        if(!bindingClass.addPermission(permissions, annotationType, methodPermission)) {
            error(element, "Add @%s annotation constains duplicate permission", annotationClass.getSimpleName());
        }
    }

    private boolean isSubtypeOfType(TypeMirror typeMirror, String otherType) {
        if (otherType.equals(typeMirror.toString())) {
            return true;
        }
        if(typeMirror.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declaredType = (DeclaredType) typeMirror;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.size() > 0) {
            StringBuilder typeString = new StringBuilder(declaredType.asElement().toString());
            typeString.append("<");
            for (int i = 0; i < typeArguments.size(); i++) {
                if (i > 0) {
                    typeString.append(",");
                }
                typeString.append("?");
            }
            typeString.append(">");
            if (typeString.toString().equals(otherType)) {
                return true;
            }
        }
        Element element = declaredType.asElement();
        if (!(element instanceof TypeElement)) {
            return false;
        }
        TypeElement typeElement = (TypeElement) element;
        TypeMirror superType = typeElement.getSuperclass();
        if (isSubtypeOfType(superType, otherType)) {
            return true;
        }
        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            if (isSubtypeOfType(interfaceType, otherType)) {
                return true;
            }
        }
        return false;
    }

    private BindingClass getOrCreateTargetClass(Map<TypeElement, BindingClass> targetClassMap,
            TypeElement classElement) {
        BindingClass bindingClass = targetClassMap.get(classElement);
        if (bindingClass == null) {
            TypeName targetTypeName = TypeName.get(classElement.asType());

            String packageName = getPackageName(classElement);
            ClassName className = ClassName.get(packageName,getClassName(classElement, packageName) + BINDING_PERMISSION_CLASS_SUFFIX);

            bindingClass = new BindingClass(targetTypeName, className);

            targetClassMap.put(classElement, bindingClass);
        }
        return bindingClass;
    }

    private String getClassName(TypeElement classElement, String packageName) {
        return classElement.getQualifiedName().toString().substring(packageName.length() + 1).replace(".", "$");
    }

    private String getPackageName(TypeElement classElement) {
        return elementUtils.getPackageOf(classElement).getQualifiedName().toString();
    }

    private String findDuplicate(String[] array) {
        Set<String> seenElements = new LinkedHashSet<>();
        for(String item: array) {
            if (!seenElements.add(item)) {
                return item;
            }
        }
        return null;
    }

    private void logParsingError(Element element, Class<? extends Annotation> annotation,
            Exception e) {
        StringWriter stackTrace = new StringWriter();
        if(e != null)
            e.printStackTrace(new PrintWriter(stackTrace));
        error(element, "Unable to parse @%s binding.\n\n%s", annotation.getSimpleName(), stackTrace);
    }
    private void error(Element element, String message, Object... args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }
        processingEnv.getMessager().printMessage(ERROR, message, element);
    }
}
