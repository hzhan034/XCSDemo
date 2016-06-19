package com.hugo.annotationprocessortest.processor;


import com.google.auto.service.AutoService;
import com.hugo.annotationprocessortest.FragmentArgs;
import com.hugo.annotationprocessortest.FragmentArgsInjector;
import com.hugo.annotationprocessortest.annnotation.Arg;
import com.hugo.annotationprocessortest.annnotation.FragmentWithArgs;
import com.hugo.annotationprocessortest.bundler.ArgsBundler;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * This is the annotation processor for FragmentArgs
 *
 * @author Hannes Dorfmann
 */
@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class ArgProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Types typeUtils;
    private Filer filer;

    private TypeElement TYPE_FRAGMENT;
    private TypeElement TYPE_SUPPORT_FRAGMENT;
    private boolean supportAnnotations = true;

    private static final String CUSTOM_BUNDLER_BUNDLE_KEY =
            "com.hannesdorfmann.fragmentargs.custom.bundler.2312A478rand.";

    private static final Map<String, String> ARGUMENT_TYPES = new HashMap<String, String>(20);

    /**
     * Annotation Processor Option
     */
    private static final String OPTION_IS_LIBRARY = "fragmentArgsLib";


    /**
     * Should the builder be annotated with support annotations?
     */
    private static final String OPTION_SUPPORT_ANNOTATIONS = "fragmentArgsSupportAnnotations";

    /**
     * Pass a list of additional annotations to annotate the generated builder classes
     */
    private static final String OPTION_ADDITIONAL_BUILDER_ANNOTATIONS =
            "fragmentArgsBuilderAnnotations";


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {


        Elements elementUtils = processingEnv.getElementUtils();
        Types typeUtils = processingEnv.getTypeUtils();
        Filer filer = processingEnv.getFiler();

        //
        // Processor options
        //
        boolean isLibrary = false;
        String fragmentArgsLib = processingEnv.getOptions().get(OPTION_IS_LIBRARY);
        if (fragmentArgsLib != null && fragmentArgsLib.equalsIgnoreCase("true")) {
            isLibrary = true;
        }

        String supportAnnotationsStr = processingEnv.getOptions().get(OPTION_SUPPORT_ANNOTATIONS);
        if (supportAnnotationsStr != null && supportAnnotationsStr.equalsIgnoreCase("false")) {
            supportAnnotations = false;
        }

        String additionalBuilderAnnotations[] = {};
        String builderAnnotationsStr =
                processingEnv.getOptions().get(OPTION_ADDITIONAL_BUILDER_ANNOTATIONS);
        if (builderAnnotationsStr != null && builderAnnotationsStr.length() > 0) {
            additionalBuilderAnnotations = builderAnnotationsStr.split(" "); // White space is delimiter
        }

        List<ProcessingException> processingExceptions = new ArrayList<ProcessingException>();

        JavaWriter jw = null;

        // REMEMBER: It's a SET! it uses .equals() .hashCode() to determine if element already in set
        Set<TypeElement> fragmentClasses = new HashSet<TypeElement>();

        Element[] origHelper = null;

        // Search for @Arg fields
        for (Element element : env.getElementsAnnotatedWith(Arg.class)) {

            try {
                TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();

                // TODO Check if its a fragment
//                if (!isFragmentClass(enclosingElement, TYPE_FRAGMENT, TYPE_SUPPORT_FRAGMENT)) {
//                    throw new ProcessingException(element,
//                            "@Arg can only be used on fragment fields (%s.%s)",
//                            enclosingElement.getQualifiedName(), element);
//                }

                if (element.getModifiers().contains(Modifier.FINAL)) {
                    throw new ProcessingException(element,
                            "@Arg fields must not be final (%s.%s)",
                            enclosingElement.getQualifiedName(), element);
                }

                if (element.getModifiers()
                        .contains(Modifier.STATIC)) {
                    throw new ProcessingException(element,
                            "@Arg fields must not be static (%s.%s)",
                            enclosingElement.getQualifiedName(), element);
                }

                // Skip abstract classes
                if (!enclosingElement.getModifiers().contains(Modifier.ABSTRACT)) {
                    fragmentClasses.add(enclosingElement);
                }
            } catch (ProcessingException e) {
                processingExceptions.add(e);
            }
        }

        //  TODO Search for "just" @InheritedFragmentArgs --> DEPRECATED
//        for (Element element : env.getElementsAnnotatedWith(FragmentArgsInherited.class)) {
//            try {
//                scanForAnnotatedFragmentClasses(env, FragmentArgsInherited.class, fragmentClasses, element);
//            } catch (ProcessingException e) {
//                processingExceptions.add(e);
//            }
//        }

        // Search for "just" @FragmentWithArgs
        for (Element element : env.getElementsAnnotatedWith(FragmentWithArgs.class)) {
            try {
                scanForAnnotatedFragmentClasses(env, FragmentWithArgs.class, fragmentClasses, element);
            } catch (ProcessingException e) {
                processingExceptions.add(e);
            }
        }

        // Store the key - value for the generated FragmentArtMap class
        Map<String, String> autoMapping = new HashMap<String, String>();

        for (TypeElement fragmentClass : fragmentClasses) {

            JavaFileObject jfo = null;
            try {

                AnnotatedFragment fragment = collectArgumentsForType(fragmentClass);

                String builder = fragment.getSimpleName() + "Builder";
                List<Element> originating = new ArrayList<Element>(10);
                originating.add(fragmentClass);
                TypeMirror superClass = fragmentClass.getSuperclass();
                while (superClass.getKind() != TypeKind.NONE) {
                    TypeElement element = (TypeElement) typeUtils.asElement(superClass);
                    if (element.getQualifiedName().toString().startsWith("android.")) {
                        break;
                    }
                    originating.add(element);
                    superClass = element.getSuperclass();
                }

                String qualifiedFragmentName = fragment.getQualifiedName().toString();
                String qualifiedBuilderName = qualifiedFragmentName + "Builder";

                Element[] orig = originating.toArray(new Element[originating.size()]);
                origHelper = orig;

                jfo = filer.createSourceFile(qualifiedBuilderName, orig);
                Writer writer = jfo.openWriter();
                jw = new JavaWriter(writer);
                writePackage(jw, fragmentClass);
                jw.emitImports("android.os.Bundle");
                if (supportAnnotations) {
                    jw.emitImports("android.support.annotation.NonNull");
                }

                jw.emitEmptyLine();

                // Additional builder annotations
                for (String builderAnnotation : additionalBuilderAnnotations) {
                    jw.emitAnnotation(builderAnnotation);
                }

                jw.beginType(builder, "class", EnumSet.of(Modifier.PUBLIC, Modifier.FINAL));

                if (!fragment.getBundlerVariableMap().isEmpty()) {
                    jw.emitEmptyLine();
                    for (Map.Entry<String, String> e : fragment.getBundlerVariableMap().entrySet()) {
                        jw.emitField(e.getKey(), e.getValue(),
                                EnumSet.of(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC),
                                "new " + e.getKey() + "()");
                    }
                }
                jw.emitEmptyLine();
                jw.emitField("Bundle", "mArguments", EnumSet.of(Modifier.PRIVATE, Modifier.FINAL),
                        "new Bundle()");
                jw.emitEmptyLine();

                Set<ArgumentAnnotatedField> required = fragment.getRequiredFields();

                String[] args = new String[required.size() * 2];
                int index = 0;

                for (ArgumentAnnotatedField arg : required) {
                    boolean annotate = supportAnnotations && !arg.isPrimitive();
                    args[index++] = annotate ? "@NonNull " + arg.getType() : arg.getType();
                    args[index++] = arg.getVariableName();
                }
                jw.beginMethod(null, builder, EnumSet.of(Modifier.PUBLIC), args);

                for (ArgumentAnnotatedField arg : required) {
                    writePutArguments(jw, arg.getVariableName(), "mArguments", arg);
                }

                jw.endMethod();

                if (!required.isEmpty()) {
                    jw.emitEmptyLine();
                    writeNewFragmentWithRequiredMethod(builder, fragmentClass, jw, args);
                }

                Set<ArgumentAnnotatedField> optionalArguments = fragment.getOptionalFields();

                for (ArgumentAnnotatedField arg : optionalArguments) {
                    writeBuilderMethod(builder, jw, arg);
                }

                jw.emitEmptyLine();
                writeBuildBundleMethod(jw);

                jw.emitEmptyLine();
                writeInjectMethod(jw, fragmentClass, fragment);

                jw.emitEmptyLine();
                writeBuildMethod(jw, fragmentClass);

                jw.emitEmptyLine();
                writeBuildSubclassMethod(jw, fragmentClass);
                jw.endType();

                autoMapping.put(qualifiedFragmentName, qualifiedBuilderName);
            } catch (IOException e) {
                processingExceptions.add(
                        new ProcessingException(fragmentClass, "Unable to write builder for type %s: %s",
                                fragmentClass, e.getMessage()));
            } catch (ProcessingException e) {
                processingExceptions.add(e);
                if (jfo != null) {
                    jfo.delete();
                }
            } finally {
                if (jw != null) {
                    try {
                        jw.close();
                    } catch (IOException e1) {
                        processingExceptions.add(new ProcessingException(fragmentClass,
                                "Unable to close javawriter while generating builder for type %s: %s",
                                fragmentClass, e1.getMessage()));
                    }
                }
            }
        }

        // Write the automapping class
        if (origHelper != null && !isLibrary) {
            try {
                writeAutoMapping(autoMapping, origHelper);
            } catch (ProcessingException e) {
                processingExceptions.add(e);
            }
        }

        // Print errors
        for (ProcessingException e : processingExceptions) {
            error(e);
        }

        return true;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> supportTypes = new LinkedHashSet<String>();
        supportTypes.add(Arg.class.getCanonicalName());
        supportTypes.add(FragmentWithArgs.class.getCanonicalName());
        return supportTypes;
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);

        elementUtils = env.getElementUtils();
        typeUtils = env.getTypeUtils();
        filer = env.getFiler();

        TYPE_FRAGMENT = elementUtils.getTypeElement("android.app.Fragment");
        TYPE_SUPPORT_FRAGMENT =
                elementUtils.getTypeElement("android.support.v4.app.Fragment");
    }


    private void scanForAnnotatedFragmentClasses(RoundEnvironment env,
                                                 Class<? extends Annotation> annotationClass, Set<TypeElement> fragmentClasses,
                                                 Element element)
            throws ProcessingException {

        if (element.getKind() != ElementKind.CLASS) {
            throw new ProcessingException(element, "%s can only be applied on Fragment classes",
                    annotationClass.getSimpleName());
        }

        TypeElement classElement = (TypeElement) element;

        // TODO  Check if its a fragment
//        if (!isFragmentClass(element, TYPE_FRAGMENT, TYPE_SUPPORT_FRAGMENT)) {
//            throw new ProcessingException(element,
//                    "%s can only be used on fragments, but %s is not a subclass of fragment",
//                    annotationClass.getSimpleName(), classElement.getQualifiedName());
//        }

        // Skip abstract classes
        if (!classElement.getModifiers().contains(Modifier.ABSTRACT)) {
            fragmentClasses.add(classElement);
        }
    }


    /**
     * Collects the fields that are annotated by the fragmentarg
     */
    private AnnotatedFragment collectArgumentsForType(TypeElement type) throws ProcessingException {

        // incl. super classes
//        if (shouldScanSuperClassesFragmentArgs(type)) {
//            return collectArgumentsForTypeInclSuperClasses(type);
//        }

        // Without super classes (inheritance)
        Map<String, ExecutableElement> setterMethodsMap = new HashMap<String, ExecutableElement>();
        AnnotatedFragment fragment = new AnnotatedFragment(type);
        for (Element element : type.getEnclosedElements()) {
            if (element.getKind() == ElementKind.FIELD) {
                Arg annotation = element.getAnnotation(Arg.class);
                if (annotation != null) {
                    ArgumentAnnotatedField field = new ArgumentAnnotatedField(element, type, annotation);
                    addAnnotatedField(field, fragment, annotation);
                }
            } else {
                // check for setter
                fragment.checkAndAddSetterMethod(element);
            }
        }
        return fragment;
    }

    /**
     * Checks if the annotated field can be added to the given fragment. Otherwise a error message
     * will be printed
     */
    private void addAnnotatedField(ArgumentAnnotatedField annotatedField, AnnotatedFragment fragment,
                                   Arg annotation) throws ProcessingException {

        if (fragment.containsField(annotatedField)) {
            // TODO  A field already with the name is here
//            throw new ProcessingException(annotatedField.getElement(),
//                    getErrorMessageDuplicatedField(fragment, annotatedField.getClassElement(),
//                            annotatedField.getVariableName()));
        } else if (fragment.containsBundleKey(annotatedField) != null) {
            //  key for bundle is already in use
            ArgumentAnnotatedField otherField = fragment.containsBundleKey(annotatedField);
            throw new ProcessingException(annotatedField.getElement(),
                    "The bundle key '%s' for field %s in %s is already used by another "
                            + "argument in %s (field name is '%s'). Bundle keys must be unique in inheritance hierarchy!",
                    annotatedField.getKey(), annotatedField.getVariableName(),
                    annotatedField.getClassElement().getQualifiedName().toString(),
                    otherField.getClassElement().getQualifiedName().toString(), otherField.getVariableName());
        } else {
            if (annotation.required()) {
                fragment.addRequired(annotatedField);
            } else {
                fragment.addOptional(annotatedField);
            }
        }
    }


    protected void writePackage(JavaWriter jw, TypeElement type) throws IOException {
        PackageElement pkg = processingEnv.getElementUtils().getPackageOf(type);
        if (!pkg.isUnnamed()) {
            jw.emitPackage(pkg.getQualifiedName().toString());
        } else {
            jw.emitPackage("");
        }
    }


    protected void writePutArguments(JavaWriter jw, String sourceVariable, String bundleVariable,
                                     ArgumentAnnotatedField arg) throws IOException, ProcessingException {

        jw.emitEmptyLine();

        if (!arg.isPrimitive()) {
            jw.beginControlFlow("if (%s == null)", sourceVariable);
            jw.emitStatement("throw new NullPointerException(\"Argument '%s' must not be null.\")",
                    arg.getName());
            jw.endControlFlow();
        }

        if (arg.hasCustomBundler()) {
            jw.emitStatement("%s.putBoolean(\"%s\", true)", bundleVariable,
                    CUSTOM_BUNDLER_BUNDLE_KEY + arg.getKey());
            jw.emitStatement("%s.put(\"%s\", %s, %s)", arg.getBundlerFieldName(), arg.getKey(),
                    sourceVariable, bundleVariable);
        } else {

            String op = getOperation(arg);

            if (op == null) {
                throw new ProcessingException(arg.getElement(),
                        "Don't know how to put %s in a Bundle. This type is not supported by default. "
                                + "However, you can specify your own %s implementation in @Arg( bundler = YourBundler.class)",
                        arg.getElement().asType().toString(), ArgsBundler.class.getSimpleName());
            }
            if ("Serializable".equals(op)) {
                processingEnv.getMessager()
                        .printMessage(Diagnostic.Kind.WARNING,
                                String.format("%1$s will be stored as Serializable", arg.getName()),
                                arg.getElement());
            }
            jw.emitStatement("%4$s.put%1$s(\"%2$s\", %3$s)", op, arg.getKey(), sourceVariable,
                    bundleVariable);
        }
    }

    protected String getOperation(ArgumentAnnotatedField arg) {
        String op = ARGUMENT_TYPES.get(arg.getRawType());
        if (op != null) {
            if (arg.isArray()) {
                return op + "Array";
            } else {
                return op;
            }
        }

        Elements elements = processingEnv.getElementUtils();
        TypeMirror type = arg.getElement().asType();
        Types types = processingEnv.getTypeUtils();
        String[] arrayListTypes = new String[] {
                String.class.getName(), Integer.class.getName(), CharSequence.class.getName()
        };
        String[] arrayListOps =
                new String[] {"StringArrayList", "IntegerArrayList", "CharSequenceArrayList"};
        for (int i = 0; i < arrayListTypes.length; i++) {
            TypeMirror tm = getArrayListType(arrayListTypes[i]);
            if (types.isAssignable(type, tm)) {
                return arrayListOps[i];
            }
        }

        if (types.isAssignable(type,
                getWildcardType(ArrayList.class.getName(), "android.os.Parcelable"))) {
            return "ParcelableArrayList";
        }
        TypeMirror sparseParcelableArray =
                getWildcardType("android.util.SparseArray", "android.os.Parcelable");

        if (types.isAssignable(type, sparseParcelableArray)) {
            return "SparseParcelableArray";
        }

        if (types.isAssignable(type, elements.getTypeElement(Serializable.class.getName()).asType())) {
            return "Serializable";
        }

        if (types.isAssignable(type, elements.getTypeElement("android.os.Parcelable").asType())) {
            return "Parcelable";
        }

        return null;
    }


    private TypeMirror getArrayListType(String elementType) {
        TypeElement arrayList = processingEnv.getElementUtils().getTypeElement("java.util.ArrayList");
        TypeMirror elType = processingEnv.getElementUtils().getTypeElement(elementType).asType();
        return processingEnv.getTypeUtils().getDeclaredType(arrayList, elType);
    }

    private TypeMirror getWildcardType(String type, String elementType) {
        TypeElement arrayList = processingEnv.getElementUtils().getTypeElement(type);
        TypeMirror elType = processingEnv.getElementUtils().getTypeElement(elementType).asType();
        return processingEnv.getTypeUtils()
                .getDeclaredType(arrayList, processingEnv.getTypeUtils().getWildcardType(elType, null));
    }

    private void writeNewFragmentWithRequiredMethod(String builder, TypeElement element,
                                                    JavaWriter jw, String[] args) throws IOException {

        if (supportAnnotations) jw.emitAnnotation("NonNull");
        jw.beginMethod(element.getQualifiedName().toString(), "new" + element.getSimpleName(),
                EnumSet.of(Modifier.STATIC, Modifier.PUBLIC), args);
        StringBuilder argNames = new StringBuilder();
        for (int i = 1; i < args.length; i += 2) {
            argNames.append(args[i]);
            if (i < args.length - 1) {
                argNames.append(", ");
            }
        }
        jw.emitStatement("return new %1$s(%2$s).build()", builder, argNames);
        jw.endMethod();
    }

    private void writeBuilderMethod(String type, JavaWriter writer, ArgumentAnnotatedField arg)
            throws IOException, ProcessingException {
        writer.emitEmptyLine();
        boolean annotate = supportAnnotations && !arg.isPrimitive();

        writer.beginMethod(type, arg.getVariableName(), EnumSet.of(Modifier.PUBLIC),
                annotate ? "@NonNull " + arg.getType() : arg.getType(),
                arg.getVariableName());
        writePutArguments(writer, arg.getVariableName(), "mArguments", arg);
        writer.emitStatement("return this");
        writer.endMethod();
    }

    /**
     * Write the buildBundle() method
     * @param jw The javawriter
     * @throws IOException
     */
    private void writeBuildBundleMethod(JavaWriter jw) throws IOException {
        jw.beginMethod("Bundle", "buildBundle", EnumSet.of(Modifier.PUBLIC));
        jw.emitStatement("return new Bundle(mArguments)");
        jw.endMethod();
    }


    private void writeInjectMethod(JavaWriter jw, TypeElement element,
                                   AnnotatedFragment fragment) throws IOException, ProcessingException {

        Set<ArgumentAnnotatedField> allArguments = fragment.getAll();

        String fragmentType = supportAnnotations ? "@NonNull " + element.getSimpleName().toString()
                : element.getSimpleName().toString();

        jw.beginMethod("void", "injectArguments",
                EnumSet.of(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC),
                fragmentType, "fragment");

        if (!allArguments.isEmpty()) {
            jw.emitStatement("Bundle args = fragment.getArguments()");

            // Check if bundle is null only if at least one required field
            if (!fragment.getRequiredFields().isEmpty()) {
                jw.beginControlFlow("if (args == null)");
                jw.emitStatement(
                        "throw new IllegalStateException(\"No arguments set. Have you setup this Fragment with the corresponding FragmentArgs Builder? \")");
                jw.endControlFlow();
            }
        }

        int setterAssignmentHelperCounter = 0;
        for (ArgumentAnnotatedField field : allArguments) {
            jw.emitEmptyLine();

            // Check if the given setter is available
            String setterMethod = null;
            boolean useSetter = field.isUseSetterMethod();
            if (useSetter) {
                ExecutableElement setterMethodElement = fragment.findSetterForField(field);
                setterMethod = setterMethodElement.getSimpleName().toString();
            }

            // Args Bundler
            if (field.hasCustomBundler()) {

                String setterAssignmentHelperStr = null;
                String assignmentStr;
                if (useSetter) {
                    setterAssignmentHelperStr = field.getType()
                            + " value"
                            + setterAssignmentHelperCounter
                            + " =  %s.get(\"%s\", args)";
                    assignmentStr = "fragment.%s( value" + setterAssignmentHelperCounter + " )";
                    setterAssignmentHelperCounter++;
                } else {
                    assignmentStr = "fragment.%s = %s.get(\"%s\", args)";
                }

                // Required
                if (field.isRequired()) {
                    jw.beginControlFlow("if (!args.containsKey(" + JavaWriter.stringLiteral(
                            CUSTOM_BUNDLER_BUNDLE_KEY + field.getKey()) + "))");
                    jw.emitStatement("throw new IllegalStateException(\"required argument %1$s is not set\")",
                            field.getKey());
                    jw.endControlFlow();
                    if (useSetter) {
                        jw.emitStatement(setterAssignmentHelperStr, field.getBundlerFieldName(),
                                field.getKey());
                        jw.emitStatement(assignmentStr, setterMethod);
                    } else {
                        jw.emitStatement(assignmentStr, field.getName(),
                                field.getBundlerFieldName(), field.getKey());
                    }
                } else {
                    // not required bundler
                    jw.beginControlFlow("if (args.getBoolean(" + JavaWriter.stringLiteral(
                            CUSTOM_BUNDLER_BUNDLE_KEY + field.getKey()) + "))");

                    if (useSetter) {
                        jw.emitStatement(setterAssignmentHelperStr, field.getBundlerFieldName(),
                                field.getKey());
                        jw.emitStatement(assignmentStr, setterMethod);
                    } else {
                        jw.emitStatement(assignmentStr, field.getName(),
                                field.getBundlerFieldName(), field.getKey());
                    }

                    jw.endControlFlow();
                }
            } else {

                // Build in functions
                String op = getOperation(field);
                if (op == null) {
                    throw new ProcessingException(element,
                            "Can't write injector, the type is not supported by default. "
                                    + "However, You can provide your own implementation by providing an %s like this: @Arg( bundler = YourBundler.class )",
                            ArgsBundler.class.getSimpleName());
                }

                String cast = "Serializable".equals(op) ? "(" + field.getType() + ") " : "";
                if (!field.isRequired()) {
                    jw.beginControlFlow(
                            "if (args != null && args.containsKey("
                                    + JavaWriter.stringLiteral(field.getKey())
                                    + "))");
                } else {
                    jw.beginControlFlow(
                            "if (!args.containsKey(" + JavaWriter.stringLiteral(field.getKey()) + "))");
                    jw.emitStatement("throw new IllegalStateException(\"required argument %1$s is not set\")",
                            field.getKey());
                    jw.endControlFlow();
                }

                if (useSetter) {
                    jw.emitStatement("fragment.%1$s( %4$sargs.get%2$s(\"%3$s\") )", setterMethod, op,
                            field.getKey(), cast);
                } else {
                    jw.emitStatement("fragment.%1$s = %4$sargs.get%2$s(\"%3$s\")", field.getName(), op,
                            field.getKey(), cast);
                }

                if (!field.isRequired()) {
                    jw.endControlFlow();
                }
            }
        }
        jw.endMethod();
    }

    private void writeBuildMethod(JavaWriter jw, TypeElement element) throws IOException {
        if (supportAnnotations) {
            jw.emitAnnotation("NonNull");
        }

        jw.beginMethod(element.getSimpleName().toString(), "build", EnumSet.of(Modifier.PUBLIC));
        jw.emitStatement("%1$s fragment = new %1$s()", element.getSimpleName().toString());
        jw.emitStatement("fragment.setArguments(mArguments)");
        jw.emitStatement("return fragment");
        jw.endMethod();
    }

    private void writeBuildSubclassMethod(JavaWriter jw, TypeElement element) throws IOException {
        if (supportAnnotations) {
            jw.emitAnnotation("NonNull");
        }
        jw.beginMethod("<F extends " + element.getSimpleName().toString() + "> F", "build",
                EnumSet.of(Modifier.PUBLIC), supportAnnotations ? "@NonNull F" : "F", "fragment");
        jw.emitStatement("fragment.setArguments(mArguments)");
        jw.emitStatement("return fragment");
        jw.endMethod();
    }

    /**
     * Key is the fully qualified fragment name, value is the fully qualified Builder class name
     */

    private void writeAutoMapping(Map<String, String> mapping, Element[] element)
            throws ProcessingException {

        try {
            JavaFileObject jfo =
                    filer.createSourceFile(FragmentArgs.AUTO_MAPPING_QUALIFIED_CLASS, element);
            Writer writer = jfo.openWriter();
            JavaWriter jw = new JavaWriter(writer);
            // Package
            jw.emitPackage(FragmentArgs.AUTO_MAPPING_PACKAGE);
            // Imports
            jw.emitImports("android.os.Bundle");

            // Class
            jw.beginType(FragmentArgs.AUTO_MAPPING_CLASS_NAME, "class",
                    EnumSet.of(Modifier.PUBLIC, Modifier.FINAL), null,
                    FragmentArgsInjector.class.getCanonicalName());

            jw.emitEmptyLine();
            // The mapping Method
            jw.beginMethod("void", "inject", EnumSet.of(Modifier.PUBLIC), "Object", "target");

            jw.emitEmptyLine();
            jw.emitStatement("Class<?> targetClass = target.getClass()");
            jw.emitStatement("String targetName = targetClass.getCanonicalName()");
            // TODO should be targetClass.getName()? Inner anonymous class not possible?

            for (Map.Entry<String, String> entry : mapping.entrySet()) {

                jw.emitEmptyLine();
                jw.beginControlFlow("if ( %s.class.getName().equals(targetName) )", entry.getKey());
                jw.emitStatement("%s.injectArguments( ( %s ) target)", entry.getValue(), entry.getKey());
                jw.emitStatement("return");
                jw.endControlFlow();
            }

            // End Mapping method
            jw.endMethod();

            jw.endType();
            jw.close();
        } catch (IOException e) {
            throw new ProcessingException(null,
                    "Unable to write the automapping class for builder to fragment: %s: %s",
                    FragmentArgs.AUTO_MAPPING_QUALIFIED_CLASS, e.getMessage());
        }
    }

    public void error(ProcessingException e) {
        String message = e.getMessage();
        if (e.getMessageArgs().length > 0) {
            message = String.format(message, e.getMessageArgs());
        }
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, e.getElement());
    }


}
