package com.samplemvcwebapp.group03servicelayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samplemvcwebapp.group03servicelayer.dependencyitems.ClassWithPrivateCtor;
import com.samplemvcwebapp.group03servicelayer.dependencyitems.ConstructorParamClass;
import com.samplemvcwebapp.group03servicelayer.dependencyitems.GenericClass;
import com.samplemvcwebapp.group03servicelayer.dependencyitems.GenericInterface;
import com.samplemvcwebapp.group03servicelayer.dependencyitems.LifetimeScope;
import com.samplemvcwebapp.group03servicelayer.dependencyitems.MyDisposableClass;
import com.samplemvcwebapp.group03servicelayer.dependencyitems.SimpleClass;
import com.samplemvcwebapp.group03servicelayer.dependencyitems.SimpleClassInterface;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.ResolvableType;

/**
 * Port of Tests/UnitTests/Group03ServiceLayer/Test10DiSimple.cs (11 tests). These were Autofac container tests; each
 * port checks the same container behaviour with a plain Spring ApplicationContext: Autofac InstancePerDependency is
 * Spring prototype, SingleInstance is singleton, InstancePerLifetimeScope is a custom scope, RegisterGeneric is
 * generic-type-aware resolution and RegisterAssemblyTypes is component scanning.
 */
class Test10DiSimpleTest {

    @Test
    void test01AutoFacSimple() {
        try (var ctx = context()) {
            ctx.registerBean(SimpleClassInterface.class, SimpleClass::new);
            ctx.refresh();

            assertThat(ctx.getBean(SimpleClassInterface.class)).isInstanceOf(SimpleClass.class);
        }
    }

    @Test
    void test02AutoFacTransient() {
        try (var ctx = context()) {
            ctx.registerBean(SimpleClassInterface.class, SimpleClass::new,
                    bd -> bd.setScope(BeanDefinition.SCOPE_PROTOTYPE));
            ctx.refresh();

            assertThat(ctx.getBean(SimpleClassInterface.class)).isNotSameAs(ctx.getBean(SimpleClassInterface.class));
        }
    }

    @Test
    void test03AutoFacSingle() {
        try (var ctx = context()) {
            ctx.registerBean(SimpleClassInterface.class, SimpleClass::new,
                    bd -> bd.setScope(BeanDefinition.SCOPE_SINGLETON));
            ctx.refresh();

            assertThat(ctx.getBean(SimpleClassInterface.class)).isSameAs(ctx.getBean(SimpleClassInterface.class));
        }
    }

    @Test
    void test04AutoFacLifeTimeScope() {
        LifetimeScope scope = new LifetimeScope();
        try (var ctx = context()) {
            ctx.getBeanFactory().registerScope(LifetimeScope.NAME, scope);
            ctx.registerBean(SimpleClassInterface.class, SimpleClass::new, bd -> bd.setScope(LifetimeScope.NAME));
            ctx.refresh();

            scope.beginNewScope();
            SimpleClassInterface inScope1a = ctx.getBean(SimpleClassInterface.class);
            SimpleClassInterface inScope1b = ctx.getBean(SimpleClassInterface.class);
            scope.beginNewScope();
            SimpleClassInterface inScope2 = ctx.getBean(SimpleClassInterface.class);

            assertThat(inScope1a).isSameAs(inScope1b);
            assertThat(inScope1a).isNotSameAs(inScope2);
        }
    }

    @Test
    void test05AutoFacConstructor() {
        try (var ctx = context()) {
            ctx.registerBeanDefinition("constructorParamClass", BeanDefinitionBuilder
                    .genericBeanDefinition(ConstructorParamClass.class).addConstructorArgValue(123).getBeanDefinition());
            ctx.refresh();

            assertThat(ctx.getBean(ConstructorParamClass.class).getMyInt()).isEqualTo(123);
        }
    }

    @Test
    void test15AutoFacDisposeCreate() {
        AtomicBoolean disposeCalled = new AtomicBoolean();
        try (var ctx = context()) {
            ctx.registerBean(MyDisposableClass.class, () -> new MyDisposableClass(() -> disposeCalled.set(true)));
            ctx.refresh();

            assertThat(ctx.getBean(MyDisposableClass.class)).isNotNull();
            assertThat(disposeCalled).isFalse();
        }
    }

    @Test
    void test16AutoFacDisposeCalled() {
        AtomicBoolean disposeCalled = new AtomicBoolean();
        var ctx = context();
        ctx.registerBean(MyDisposableClass.class, () -> new MyDisposableClass(() -> disposeCalled.set(true)));
        ctx.refresh();
        ctx.getBean(MyDisposableClass.class);

        ctx.close();

        assertThat(disposeCalled).isTrue();
    }

    @Test
    void test20AutoFacRegisterGeneric() {
        try (var ctx = context()) {
            registerGenerics(ctx);
            ctx.refresh();

            assertThat(genericFor(ctx, SimpleClass.class).getTypeName()).isEqualTo("SimpleClass");
            assertThat(genericFor(ctx, String.class).getTypeName()).isEqualTo("String");
        }
    }

    @Test
    void test21AutoFacRegisterGenericAfterRegisterAssembly() {
        try (var ctx = context()) {
            ctx.scan(SimpleClass.class.getPackageName());
            registerGenerics(ctx);
            ctx.refresh();

            assertThat(ctx.getBean(SimpleClassInterface.class)).isInstanceOf(SimpleClass.class);
            assertThat(genericFor(ctx, SimpleClass.class).getTypeName()).isEqualTo("SimpleClass");
        }
    }

    /** Legacy: Autofac cannot build a class whose only constructor is private. Spring cannot satisfy it either. */
    @Test
    void test30AutoFacRegisterClassWithPrivateCtorBad() {
        var ctx = context();
        ctx.registerBean(ClassWithPrivateCtor.class);

        assertThatThrownBy(ctx::refresh).isInstanceOf(BeanCreationException.class);
    }

    /** Legacy: resolve via a helper that calls the private ctor. Spring: register the type's factory method. */
    @Test
    void test31AutoFacTryCtorWithPrivateCtorAsOptionOk() {
        try (var ctx = context()) {
            ctx.registerBean(ClassWithPrivateCtor.class, ClassWithPrivateCtor::create);
            ctx.refresh();

            assertThat(ctx.getBean(ClassWithPrivateCtor.class)).isNotNull();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static AnnotationConfigApplicationContext context() {
        return new AnnotationConfigApplicationContext();
    }

    private static void registerGenerics(AnnotationConfigApplicationContext ctx) {
        registerGeneric(ctx, SimpleClass.class);
        registerGeneric(ctx, String.class);
    }

    private static <T> void registerGeneric(AnnotationConfigApplicationContext ctx, Class<T> type) {
        RootBeanDefinition bd = new RootBeanDefinition(GenericClass.class);
        bd.setInstanceSupplier(() -> new GenericClass<>(type));
        bd.setTargetType(ResolvableType.forClassWithGenerics(GenericClass.class, type));
        ctx.registerBeanDefinition("generic" + type.getSimpleName(), bd);
    }

    @SuppressWarnings("unchecked")
    private static <T> GenericInterface<T> genericFor(AnnotationConfigApplicationContext ctx, Class<T> type) {
        return (GenericInterface<T>) ctx.getBeanProvider(
                ResolvableType.forClassWithGenerics(GenericInterface.class, type)).getObject();
    }
}
