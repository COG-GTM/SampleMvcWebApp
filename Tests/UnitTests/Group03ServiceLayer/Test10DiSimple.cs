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
using Tests.DependencyItems;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    class Test10DiSimple
    {
        [Test]
        public void Test01DiSimple()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient<ISimpleClass, SimpleClass>();
            using var provider = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                var instance = scope.ServiceProvider.GetService<ISimpleClass>();
                Assert.That(instance, Is.Not.Null);
                (instance is SimpleClass).ShouldEqual(true);
            }
        }

        [Test]
        public void Test02DiTransient()
        {
            //Setup
            var services = new ServiceCollection();
            services.AddTransient<ISimpleClass, SimpleClass>();
            using var provider = services.BuildServiceProvider();

            //Attempt
            ISimpleClass instance1;
            using (var scope = provider.CreateScope())
                instance1 = scope.ServiceProvider.GetRequiredService<ISimpleClass>();
            ISimpleClass instance2;
            using (var scope = provider.CreateScope())
                instance2 = scope.ServiceProvider.GetRequiredService<ISimpleClass>();

            //Verify
            Assert.That(instance1, Is.Not.Null);
            Assert.That(instance2, Is.Not.Null);
            Assert.That(instance2, Is.Not.SameAs(instance1));
        }

        [Test]
        public void Test03DiSingle()
        {
            //Setup
            var services = new ServiceCollection();
            services.AddSingleton<ISimpleClass, SimpleClass>();
            using var provider = services.BuildServiceProvider();

            //Attempt
            ISimpleClass instance1;
            using (var scope = provider.CreateScope())
                instance1 = scope.ServiceProvider.GetRequiredService<ISimpleClass>();
            ISimpleClass instance2;
            using (var scope = provider.CreateScope())
                instance2 = scope.ServiceProvider.GetRequiredService<ISimpleClass>();

            //Verify
            Assert.That(instance1, Is.Not.Null);
            Assert.That(instance2, Is.Not.Null);
            Assert.That(instance2, Is.SameAs(instance1));
        }

        [Test]
        public void Test04DiLifeTimeScope()
        {
            //Setup
            var services = new ServiceCollection();
            services.AddScoped<ISimpleClass, SimpleClass>();
            using var provider = services.BuildServiceProvider();

            //Attempt and VERIFY
            ISimpleClass scope1Instance1;
            ISimpleClass scope1Instance2;
            using (var scope = provider.CreateScope())
            {
                scope1Instance1 = scope.ServiceProvider.GetRequiredService<ISimpleClass>();
                scope1Instance2 = scope.ServiceProvider.GetRequiredService<ISimpleClass>();
                Assert.That(scope1Instance1, Is.Not.Null);
                Assert.That(scope1Instance2, Is.Not.Null);
                Assert.That(scope1Instance2, Is.SameAs(scope1Instance1));
            }
            using (var scope = provider.CreateScope())
            {
                var scope2Instance1 = scope.ServiceProvider.GetRequiredService<ISimpleClass>();
                Assert.That(scope2Instance1, Is.Not.Null);
                Assert.That(scope2Instance1, Is.Not.SameAs(scope1Instance1));
                Assert.That(scope2Instance1, Is.Not.SameAs(scope1Instance2));
            }
        }

        //-----------------------------------------------------------
        //item with constructor param

        [Test]
        public void Test05DiConstructor()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient<IConstructorParamClass>(sp => ActivatorUtilities.CreateInstance<ConstructorParamClass>(sp, 42));
            using var provider = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                var instance = scope.ServiceProvider.GetRequiredService<IConstructorParamClass>();
                Assert.That(instance, Is.Not.Null);
                instance.MyInt.ShouldEqual(42);
            }
        }

        //-----------------------------------------------------------
        //tests on IDisposable items

        private int _numTimeDisposeCalled;

        [Test]
        public void Test15DiDisposeCreate()
        {
            //Setup
            var services = new ServiceCollection();
            Action checker = (() => _numTimeDisposeCalled++);
            services.AddTransient<IMyDisposableClass>(sp => new MyDisposableClass(checker));
            using var provider = services.BuildServiceProvider();

            //Attempt
            _numTimeDisposeCalled = 0;
            var mydisp = provider.GetRequiredService<IMyDisposableClass>();

            //Verify
            Assert.That(mydisp, Is.Not.Null);
            _numTimeDisposeCalled.ShouldEqual(0);
        }

        [Test]
        public void Test16DiDisposeCalled()
        {
            //Setup
            var services = new ServiceCollection();
            Action checker = (() => _numTimeDisposeCalled++);
            services.AddTransient<IMyDisposableClass>(sp => new MyDisposableClass(checker));
            using var provider = services.BuildServiceProvider();

            //Attempt
            _numTimeDisposeCalled = 0;
            using (var scope = provider.CreateScope())
            {
                var mydisp = scope.ServiceProvider.GetRequiredService<IMyDisposableClass>();
                Assert.That(mydisp, Is.Not.Null);
            }

            //Verify
            _numTimeDisposeCalled.ShouldEqual(1);
        }

        //--------------------------------------------------------------
        //register generic 

        [Test]
        public void Test20DiRegisterGeneric()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient(typeof(IGenericInterface<>), typeof(GenericInterface<>));
            using var provider = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                var instance = scope.ServiceProvider.GetRequiredService<IGenericInterface<SimpleClass>>();
                Assert.That(instance, Is.Not.Null);
                (instance is GenericInterface<SimpleClass>).ShouldEqual(true);
                instance.GetTypeName().ShouldEqual(typeof(SimpleClass).Name);
            }
        }

        [Test]
        public void Test21DiRegisterGenericAfterRegisterAssembly()
        {
            //SETUP
            var services = new ServiceCollection();
            var concreteTypes = GetType().Assembly.GetTypes()
                .Where(x => x.Namespace == typeof(SimpleClass).Namespace
                            && x.IsClass && !x.IsAbstract && !x.IsGenericTypeDefinition);
            foreach (var type in concreteTypes)
                foreach (var implementedInterface in type.GetInterfaces())
                    services.AddTransient(implementedInterface, type);
            services.AddTransient(typeof(IGenericInterface<>), typeof(GenericInterface<>));
            using var provider = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                var instance = scope.ServiceProvider.GetRequiredService<IGenericInterface<SimpleClass>>();
                Assert.That(instance, Is.Not.Null);
                (instance is GenericInterface<SimpleClass>).ShouldEqual(true);
                instance.GetTypeName().ShouldEqual(typeof(SimpleClass).Name);
            }
        }

        //---------------------------------------------------------
        //tests on what happens if ctor is private

        [Test]
        public void Test30DiRegisterClassWithPrivateCtorBad()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient<IClassWithPrivateCtor, ClassWithPrivateCtor>();
            using var provider = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                var ex = Assert.Throws<InvalidOperationException>(() => scope.ServiceProvider.GetService<IClassWithPrivateCtor>());
                ex.Message.ShouldStartWith("A suitable constructor for type");
            }
        }

        [Test]
        public void Test31DiTryCtorWithPrivateCtorAsOptionOk()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient<IClassWithPrivateCtor, ClassWithPrivateCtor>();
            services.AddTransient<IClassToTestClassWithPrivateCtor<IClassWithPrivateCtor>>(sp =>
            {
                var instance = new ClassToTestClassWithPrivateCtor<IClassWithPrivateCtor>();
                var interfaceToLookup = instance.GetType().GetGenericArguments()[0];
                var resolvedInterface = services.SingleOrDefault(x => x.ServiceType == interfaceToLookup);
                ((ISetType)instance).SetType(resolvedInterface.ImplementationType);
                return instance;
            });
            using var provider = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                var instance = scope.ServiceProvider.GetRequiredService<IClassToTestClassWithPrivateCtor<IClassWithPrivateCtor>>();
                Assert.That(instance, Is.Not.Null);
                (instance is ClassToTestClassWithPrivateCtor<IClassWithPrivateCtor>).ShouldEqual(true);
            }
        }
    }
}
