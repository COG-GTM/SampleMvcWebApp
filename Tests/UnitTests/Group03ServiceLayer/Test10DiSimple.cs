#region licence
// The MIT License (MIT)
// 
// Filename: Test10DiSimple.cs
// Date Created: 2014/05/22
// 
// Copyright (c) 2014 Jon Smith (www.selectiveanalytics.com & www.thereformedprogrammer.net)
// 
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
// 
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
#endregion
using System;
using System.Linq;
using Microsoft.Extensions.DependencyInjection;
using NUnit.Framework;
using NUnit.Framework.Legacy;
using Tests.DependencyItems;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    //These were Autofac tests. They are now the equivalent tests on the built-in Microsoft.Extensions.DependencyInjection
    //container (Autofac InstancePerDependency/SingleInstance/InstancePerLifetimeScope = Transient/Singleton/Scoped)
    public class Test10DiSimple
    {

        [Test]
        public void Test01ServiceCollectionSimple()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient<ISimpleClass, SimpleClass>();
            using var container = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var lifetimeScope = container.CreateScope())
            {
                var instance = lifetimeScope.ServiceProvider.GetService<ISimpleClass>();
                ClassicAssert.NotNull(instance);
                (instance is SimpleClass).ShouldEqual(true);
            }

        }

        [Test]
        public void Test02ServiceCollectionTransient()
        {
            //Setup
            var services = new ServiceCollection();
            services.AddTransient<ISimpleClass, SimpleClass>();
            using var container = services.BuildServiceProvider();

            //Attempt
            ISimpleClass instance1;
            using (var lifetimeScope = container.CreateScope())
                instance1 = lifetimeScope.ServiceProvider.GetService<ISimpleClass>();
            ISimpleClass instance2;
            using (var lifetimeScope = container.CreateScope())
                instance2 = lifetimeScope.ServiceProvider.GetService<ISimpleClass>();

            //Verify
            ClassicAssert.NotNull(instance1);
            ClassicAssert.NotNull(instance2);
            ClassicAssert.AreNotSame(instance1, instance2);

        }


        [Test]
        public void Test03ServiceCollectionSingle()
        {
            //Setup
            var services = new ServiceCollection();
            services.AddSingleton<ISimpleClass, SimpleClass>();
            using var container = services.BuildServiceProvider();

            //Attempt
            ISimpleClass instance1;
            using (var lifetimeScope = container.CreateScope())
                instance1 = lifetimeScope.ServiceProvider.GetService<ISimpleClass>();
            ISimpleClass instance2;
            using (var lifetimeScope = container.CreateScope())
                instance2 = lifetimeScope.ServiceProvider.GetService<ISimpleClass>();

            //Verify
            ClassicAssert.NotNull(instance1);
            ClassicAssert.NotNull(instance2);
            ClassicAssert.AreSame(instance1, instance2);

        }

        [Test]
        public void Test04ServiceCollectionScoped()
        {
            //Setup
            var services = new ServiceCollection();
            services.AddScoped<ISimpleClass, SimpleClass>();
            using var container = services.BuildServiceProvider();

            //Attempt and VERIFY
            ISimpleClass scope1Instance1;
            ISimpleClass scope1Instance2;
            using (var lifetimeScope = container.CreateScope())
            {
                scope1Instance1 = lifetimeScope.ServiceProvider.GetService<ISimpleClass>();
                scope1Instance2 = lifetimeScope.ServiceProvider.GetService<ISimpleClass>();
                ClassicAssert.NotNull(scope1Instance1);
                ClassicAssert.NotNull(scope1Instance2);
                ClassicAssert.AreSame(scope1Instance1, scope1Instance2);
            }

            using (var lifetimeScope = container.CreateScope())
            {
                ISimpleClass scope2Instance1 = lifetimeScope.ServiceProvider.GetService<ISimpleClass>();
                ClassicAssert.NotNull(scope2Instance1);
                ClassicAssert.NotNull(scope1Instance1);
                ClassicAssert.NotNull(scope1Instance2);
                ClassicAssert.AreNotSame(scope1Instance1, scope2Instance1);
                ClassicAssert.AreNotSame(scope1Instance1, scope2Instance1);
            }

        }

        //-----------------------------------------------------------
        //item with constructor param

        [Test]
        public void Test05ServiceCollectionConstructor()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient<IConstructorParamClass>(sp =>
                ActivatorUtilities.CreateInstance<ConstructorParamClass>(sp, 42));     //equivalent of Autofac's WithParameter("myInt", 42)
            using var container = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var lifetimeScope = container.CreateScope())
            {
                var instance = lifetimeScope.ServiceProvider.GetService<IConstructorParamClass>();
                ClassicAssert.NotNull(instance);
                instance.MyInt.ShouldEqual(42);
            }

        }


        //-----------------------------------------------------------
        //tests on IDisposable items

        private int _numTimeDisposeCalled;

        [Test]
        public void Test15ServiceCollectionDisposeCreate()
        {
            //Setup
            var services = new ServiceCollection();
            Action checker = (() => _numTimeDisposeCalled++);
            services.AddTransient<IMyDisposableClass>(sp => ActivatorUtilities.CreateInstance<MyDisposableClass>(sp, checker));
            var container = services.BuildServiceProvider();

            //Attempt
            _numTimeDisposeCalled = 0;
            var mydisp = container.GetService<IMyDisposableClass>();

            //Verify
            ClassicAssert.NotNull(mydisp);
            _numTimeDisposeCalled.ShouldEqual(0);

        }

        [Test]
        public void Test16ServiceCollectionDisposeCalled()
        {
            //Setup
            var services = new ServiceCollection();
            Action checker = (() => _numTimeDisposeCalled++);
            services.AddTransient<IMyDisposableClass>(sp => ActivatorUtilities.CreateInstance<MyDisposableClass>(sp, checker));
            using var container = services.BuildServiceProvider();

            //Attempt
            _numTimeDisposeCalled = 0;
            using (var lifetimeScope = container.CreateScope())
            {
                var mydisp = lifetimeScope.ServiceProvider.GetService<IMyDisposableClass>();
                ClassicAssert.NotNull(mydisp);
            }

            //Verify
            _numTimeDisposeCalled.ShouldEqual(1);

        }

        //--------------------------------------------------------------
        //register generic 

        [Test]
        public void Test20ServiceCollectionRegisterGeneric()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient(typeof(IGenericInterface<>), typeof(GenericInterface<>));
            using var container = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var lifetimeScope = container.CreateScope())
            {
                var instance = lifetimeScope.ServiceProvider.GetService<IGenericInterface<SimpleClass>>();
                ClassicAssert.NotNull(instance);
                (instance is GenericInterface<SimpleClass>).ShouldEqual(true);
                instance.GetTypeName().ShouldEqual(typeof(SimpleClass).Name);
            }

        }

        [Test]
        public void Test21ServiceCollectionRegisterGenericAfterRegisterAssembly()
        {
            //SETUP
            var services = new ServiceCollection();
            //The built-in container has no assembly scanning, so this is the equivalent of Autofac's
            //RegisterAssemblyTypes(assembly).AsImplementedInterfaces()
            foreach (var classType in GetType().Assembly.GetTypes()
                .Where(x => x.IsClass && !x.IsAbstract && !x.IsGenericTypeDefinition))
                foreach (var interfaceType in classType.GetInterfaces()
                    .Where(x => x != typeof(IDisposable) && !x.ContainsGenericParameters))
                    services.AddTransient(interfaceType, classType);
            services.AddTransient(typeof(IGenericInterface<>), typeof(GenericInterface<>));
            using var container = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var lifetimeScope = container.CreateScope())
            {
                var instance = lifetimeScope.ServiceProvider.GetService<IGenericInterface<SimpleClass>>();
                ClassicAssert.NotNull(instance);
                (instance is GenericInterface<SimpleClass>).ShouldEqual(true);
                instance.GetTypeName().ShouldEqual(typeof(SimpleClass).Name);
            }

        }

        //---------------------------------------------------------
        //tests on what happens if ctor is private

        [Test]
        public void Test30ServiceCollectionRegisterClassWithPrivateCtorBad()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient<IClassWithPrivateCtor, ClassWithPrivateCtor>();
            using var container = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var lifetimeScope = container.CreateScope())
            {
                var ex = Assert.Throws<InvalidOperationException>(() => lifetimeScope.ServiceProvider.GetService<IClassWithPrivateCtor>());
                ex.Message.ShouldStartWith("A suitable constructor for type 'Tests.DependencyItems.ClassWithPrivateCtor' could not be located.");
            }

        }

        [Test]
        public void Test31ServiceCollectionTryCtorWithPrivateCtorAsOptionOk()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient<IClassWithPrivateCtor, ClassWithPrivateCtor>();
            //The built-in container has no OnActivating or open-generic factories, so the closed generic is registered
            //with a factory that looks up the registered implementation type (Autofac's ComponentRegistry lookup)
            services.AddTransient<IClassToTestClassWithPrivateCtor<IClassWithPrivateCtor>>(sp =>
                CreateWithResolvedType<IClassWithPrivateCtor>(services));
            using var container = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var lifetimeScope = container.CreateScope())
            {
                var instance = lifetimeScope.ServiceProvider.GetService<IClassToTestClassWithPrivateCtor<IClassWithPrivateCtor>>();
                ClassicAssert.NotNull(instance);
                (instance is ClassToTestClassWithPrivateCtor<IClassWithPrivateCtor>).ShouldEqual(true);
            }

        }

        private static IClassToTestClassWithPrivateCtor<TInterface> CreateWithResolvedType<TInterface>(IServiceCollection services)
        {
            var instance = new ClassToTestClassWithPrivateCtor<TInterface>();
            var resolvedInterface = services.SingleOrDefault(x => x.ServiceType == typeof(TInterface));
            ((ISetType)instance).SetType(resolvedInterface.ImplementationType);
            return instance;
        }
    }
}
