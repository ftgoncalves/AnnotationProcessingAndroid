package com.ftgoncalves.compiler;

import com.ftgoncalves.api.annotation.StaticStringUtil;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import static com.squareup.javapoet.JavaFile.builder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeName.get;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static java.util.Collections.singleton;
import static javax.lang.model.SourceVersion.latestSupported;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.STATIC;

@AutoService(Processor.class)
public class MyProcessor extends AbstractProcessor {

    private static final String ANNOTATION = "@" + StaticStringUtil.class.getSimpleName();

    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return singleton(StaticStringUtil.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ArrayList<AnnotatedClass> annotatedClasses = new ArrayList<>();
        for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(StaticStringUtil.class)) {

            TypeElement annotatedClass = (TypeElement) annotatedElement;
            if (!isValidClass(annotatedClass)) {
                return true;
            }
            try {
                annotatedClasses.add(buildAnnotatedClass(annotatedClass));
            } catch (NoPackageNameException | IOException e) {
                String message = String.format("Couldn't process class %s: %s", annotatedClass,
                        e.getMessage());
                messager.printMessage(ERROR, message, annotatedElement);
            }
        }
        try {
            generate(annotatedClasses);
        } catch (NoPackageNameException | IOException e) {
            messager.printMessage(ERROR, "Couldn't generate class");
        }
        return true;
    }

    private void generate(List<AnnotatedClass> annos) throws NoPackageNameException, IOException {
        if (annos.size() == 0) {
            return;
        }
        String packageName = getPackageName(processingEnv.getElementUtils(),
                annos.get(0).typeElement);
        TypeSpec generatedClass = CodeGenerator.generateClass(annos);

        JavaFile javaFile = builder(packageName, generatedClass).build();
        javaFile.writeTo(processingEnv.getFiler());
    }

    private AnnotatedClass buildAnnotatedClass(TypeElement annotatedClass)
            throws NoPackageNameException, IOException {
        ArrayList<String> variableNames = new ArrayList<>();
        for (Element element : annotatedClass.getEnclosedElements()) {
            if (!(element instanceof VariableElement)) {
                continue;
            }
            VariableElement variableElement = (VariableElement) element;
            variableNames.add(variableElement.getSimpleName().toString());
        }
        return new AnnotatedClass(annotatedClass , variableNames);
    }

    private boolean isValidClass(TypeElement annotatedClass) {

        if (!ClassValidator.isPublic(annotatedClass)) {
            String message = String.format("Classes annotated with %s must be public.",
                    ANNOTATION);
            messager.printMessage(ERROR, message, annotatedClass);
            return false;
        }

        if (ClassValidator.isAbstract(annotatedClass)) {
            String message = String.format("Classes annotated with %s must not be abstract.",
                    ANNOTATION);
            messager.printMessage(ERROR, message, annotatedClass);
            return false;
        }

        return true;
    }

    String getPackageName(Elements elementUtils, TypeElement type)
            throws NoPackageNameException {
        PackageElement pkg = elementUtils.getPackageOf(type);
        if (pkg.isUnnamed()) {
            throw new NoPackageNameException(type);
        }
        return pkg.getQualifiedName().toString();
    }

    static class ClassValidator {
        static boolean isPublic(TypeElement annotatedClass) {
            return annotatedClass.getModifiers().contains(PUBLIC);
        }

        static boolean isAbstract(TypeElement annotatedClass) {
            return annotatedClass.getModifiers().contains(ABSTRACT);
        }
    }

    class AnnotatedClass {
        public final String annotatedClassName;
        public final List<String> variableNames;
        public final TypeElement typeElement;

        public AnnotatedClass(TypeElement typeElement, List<String> variableNames) {
            this.annotatedClassName = typeElement.getSimpleName().toString();
            this.variableNames = variableNames;
            this.typeElement = typeElement;
        }

        public TypeMirror getType() {
            return typeElement.asType();
        }
    }

    class NoPackageNameException extends Exception {

        public NoPackageNameException(TypeElement typeElement) {
            super("The package of " + typeElement.getSimpleName() + " has no name");
        }
    }

    static class CodeGenerator {

        private static final String CLASS_NAME = "StringUtil";

        public static TypeSpec generateClass(List<AnnotatedClass> classes) {
            TypeSpec.Builder builder =  classBuilder(CLASS_NAME)
                    .addModifiers(PUBLIC, FINAL);
            for (AnnotatedClass anno : classes) {
                builder.addMethod(makeCreateStringMethod(anno));
            }
            return builder.build();
        }

        private static MethodSpec makeCreateStringMethod(AnnotatedClass annotatedClass) {
            StringBuilder builder = new StringBuilder();
            builder.append(String.format("return \"%s{\" + ", annotatedClass.annotatedClassName));
            for (String variableName : annotatedClass.variableNames) {
                builder.append(String.format(" \"%s='\" + String.valueOf(instance.%s) + \"',\" + ",
                        variableName, variableName));
            }
            builder.append("\"}\"");
            return methodBuilder("createString")
                    .addModifiers(PUBLIC, STATIC)
                    .addParameter(get(annotatedClass.getType()), "instance")
                    .addStatement(builder.toString())
                    .returns(String.class)
                    .build();
        }
    }
}




