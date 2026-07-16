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
using Microsoft.Extensions.DependencyInjection;
using NUnit.Framework;
using NUnit.Framework.Legacy;
using Tests.DependencyItems;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    /// <summary>
    /// The application swapped Autofac for the built-in Microsoft.Extensions.DependencyInjection container.
    /// These tests assert the same lifetime/registration semantics (transient, singleton, scoped, constructor
    /// parameters, disposal, open generics, non-resolvable private constructors) against that container.
    /// </summary>
    public class Test10DiSimple
    {

        [Test]
        public void Test01SimpleResolveOk()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient<ISimpleClass, SimpleClass>();
            var provider = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                var instance = scope.ServiceProvider.GetService<ISimpleClass>();
                ClassicAssert.NotNull(instance);
                (instance is SimpleClass).ShouldEqual(true);
            }
        }

        [Test]
        public void Test02TransientGivesDifferentInstances()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient<ISimpleClass, SimpleClass>();
            var provider = services.BuildServiceProvider();

            //ATTEMPT
            ISimpleClass instance1;
            using (var scope = provider.CreateScope())
                instance1 = scope.ServiceProvider.GetService<ISimpleClass>();
            ISimpleClass instance2;
            using (var scope = provider.CreateScope())
                instance2 = scope.ServiceProvider.GetService<ISimpleClass>();

            //VERIFY
            ClassicAssert.NotNull(instance1);
            ClassicAssert.NotNull(instance2);
            ClassicAssert.AreNotSame(instance1, instance2);
        }


        [Test]
        public void Test03SingletonGivesSameInstance()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddSingleton<ISimpleClass, SimpleClass>();
            var provider = services.BuildServiceProvider();

            //ATTEMPT
            ISimpleClass instance1;
            using (var scope = provider.CreateScope())
                instance1 = scope.ServiceProvider.GetService<ISimpleClass>();
            ISimpleClass instance2;
            using (var scope = provider.CreateScope())
                instance2 = scope.ServiceProvider.GetService<ISimpleClass>();

            //VERIFY
            ClassicAssert.NotNull(instance1);
            ClassicAssert.NotNull(instance2);
            ClassicAssert.AreSame(instance1, instance2);
        }

        [Test]
        public void Test04ScopedGivesSameInstanceWithinScopeButDifferentAcrossScopes()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddScoped<ISimpleClass, SimpleClass>();
            var provider = services.BuildServiceProvider();

            //ATTEMPT and VERIFY
            ISimpleClass scope1Instance1;
            using (var scope = provider.CreateScope())
            {
                scope1Instance1 = scope.ServiceProvider.GetService<ISimpleClass>();
                var scope1Instance2 = scope.ServiceProvider.GetService<ISimpleClass>();
                ClassicAssert.NotNull(scope1Instance1);
                ClassicAssert.NotNull(scope1Instance2);
                ClassicAssert.AreSame(scope1Instance1, scope1Instance2);
            }

            using (var scope = provider.CreateScope())
            {
                var scope2Instance1 = scope.ServiceProvider.GetService<ISimpleClass>();
                ClassicAssert.NotNull(scope2Instance1);
                ClassicAssert.AreNotSame(scope1Instance1, scope2Instance1);
            }
        }

        //-----------------------------------------------------------
        //item with constructor param

        [Test]
        public void Test05ConstructorParameterOk()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient<IConstructorParamClass>(_ => new ConstructorParamClass(42));
            var provider = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                var instance = scope.ServiceProvider.GetService<IConstructorParamClass>();
                ClassicAssert.NotNull(instance);
                instance.MyInt.ShouldEqual(42);
            }
        }


        //-----------------------------------------------------------
        //tests on IDisposable items

        private int _numTimeDisposeCalled;

        [Test]
        public void Test15DisposeNotCalledWhileScopeAlive()
        {
            //SETUP
            _numTimeDisposeCalled = 0;
            Action checker = (() => _numTimeDisposeCalled++);
            var services = new ServiceCollection();
            services.AddTransient<IMyDisposableClass>(_ => new MyDisposableClass(checker));
            var provider = services.BuildServiceProvider();

            //ATTEMPT
            var scope = provider.CreateScope();
            var mydisp = scope.ServiceProvider.GetService<IMyDisposableClass>();

            //VERIFY
            ClassicAssert.NotNull(mydisp);
            _numTimeDisposeCalled.ShouldEqual(0);
        }

        [Test]
        public void Test16DisposeCalledWhenScopeDisposed()
        {
            //SETUP
            _numTimeDisposeCalled = 0;
            Action checker = (() => _numTimeDisposeCalled++);
            var services = new ServiceCollection();
            services.AddTransient<IMyDisposableClass>(_ => new MyDisposableClass(checker));
            var provider = services.BuildServiceProvider();

            //ATTEMPT
            using (var scope = provider.CreateScope())
            {
                var mydisp = scope.ServiceProvider.GetService<IMyDisposableClass>();
                ClassicAssert.NotNull(mydisp);
            }

            //VERIFY
            _numTimeDisposeCalled.ShouldEqual(1);
        }

        //--------------------------------------------------------------
        //register generic 

        [Test]
        public void Test20RegisterOpenGenericOk()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient(typeof(IGenericInterface<>), typeof(GenericInterface<>));
            var provider = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                var instance = scope.ServiceProvider.GetService<IGenericInterface<SimpleClass>>();
                ClassicAssert.NotNull(instance);
                (instance is GenericInterface<SimpleClass>).ShouldEqual(true);
                instance.GetTypeName().ShouldEqual(typeof(SimpleClass).Name);
            }
        }

        [Test]
        public void Test21RegisterOpenGenericAlongsideOtherServicesOk()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient<ISimpleClass, SimpleClass>();
            services.AddTransient<IConstructorParamClass>(_ => new ConstructorParamClass(1));
            services.AddTransient(typeof(IGenericInterface<>), typeof(GenericInterface<>));
            var provider = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                var instance = scope.ServiceProvider.GetService<IGenericInterface<SimpleClass>>();
                ClassicAssert.NotNull(instance);
                (instance is GenericInterface<SimpleClass>).ShouldEqual(true);
                instance.GetTypeName().ShouldEqual(typeof(SimpleClass).Name);
            }
        }

        //---------------------------------------------------------
        //tests on what happens if ctor is private

        [Test]
        public void Test30ResolveClassWithPrivateCtorBad()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient<IClassWithPrivateCtor, ClassWithPrivateCtor>();
            var provider = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                //The built-in container can only activate a type through a public constructor.
                var ex = Assert.Throws<InvalidOperationException>(() => scope.ServiceProvider.GetService<IClassWithPrivateCtor>());
                ex.Message.ShouldContain("ClassWithPrivateCtor");
            }
        }

        [Test]
        public void Test31ResolveOpenGenericWithPublicCtorOk()
        {
            //The old Autofac version used an OnActivating hook to inject the resolved type into a class that
            //itself only had a private-ctor dependency. The built-in container has no activation hook, so this
            //test now just verifies the open-generic type (which has a public constructor) resolves correctly.
            //SETUP
            var services = new ServiceCollection();
            services.AddTransient(typeof(IClassToTestClassWithPrivateCtor<>), typeof(ClassToTestClassWithPrivateCtor<>));
            var provider = services.BuildServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                var instance = scope.ServiceProvider.GetService<IClassToTestClassWithPrivateCtor<IClassWithPrivateCtor>>();
                ClassicAssert.NotNull(instance);
                (instance is ClassToTestClassWithPrivateCtor<IClassWithPrivateCtor>).ShouldEqual(true);
            }
        }
    }
}
