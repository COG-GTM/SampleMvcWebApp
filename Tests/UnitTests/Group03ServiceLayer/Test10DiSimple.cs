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
            using var scope = provider.CreateScope();
            var instance = scope.ServiceProvider.GetRequiredService<ISimpleClass>();
            ClassicAssert.NotNull(instance);
            (instance is SimpleClass).ShouldEqual(true);
        }

        [Test]
        public void Test02DiTransient()
        {
            var services = new ServiceCollection();
            services.AddTransient<ISimpleClass, SimpleClass>();
            using var provider = services.BuildServiceProvider();

            ISimpleClass instance1, instance2;
            using (var scope = provider.CreateScope())
                instance1 = scope.ServiceProvider.GetRequiredService<ISimpleClass>();
            using (var scope = provider.CreateScope())
                instance2 = scope.ServiceProvider.GetRequiredService<ISimpleClass>();

            ClassicAssert.AreNotSame(instance1, instance2);
        }

        [Test]
        public void Test03DiSingleton()
        {
            var services = new ServiceCollection();
            services.AddSingleton<ISimpleClass, SimpleClass>();
            using var provider = services.BuildServiceProvider();

            ISimpleClass instance1, instance2;
            using (var scope = provider.CreateScope())
                instance1 = scope.ServiceProvider.GetRequiredService<ISimpleClass>();
            using (var scope = provider.CreateScope())
                instance2 = scope.ServiceProvider.GetRequiredService<ISimpleClass>();

            ClassicAssert.AreSame(instance1, instance2);
        }

        [Test]
        public void Test04DiScoped()
        {
            var services = new ServiceCollection();
            services.AddScoped<ISimpleClass, SimpleClass>();
            using var provider = services.BuildServiceProvider();

            ISimpleClass scope1Instance1;
            using (var scope = provider.CreateScope())
            {
                scope1Instance1 = scope.ServiceProvider.GetRequiredService<ISimpleClass>();
                ClassicAssert.AreSame(scope1Instance1, scope.ServiceProvider.GetRequiredService<ISimpleClass>());
            }
            using (var scope = provider.CreateScope())
                ClassicAssert.AreNotSame(scope1Instance1, scope.ServiceProvider.GetRequiredService<ISimpleClass>());
        }

        [Test]
        public void Test05DiConstructorParam()
        {
            var services = new ServiceCollection();
            services.AddTransient<IConstructorParamClass>(_ => new ConstructorParamClass(42));
            using var provider = services.BuildServiceProvider();

            provider.GetRequiredService<IConstructorParamClass>().MyInt.ShouldEqual(42);
        }

        //-----------------------------------------------------------
        //tests on IDisposable items

        private int _numTimeDisposeCalled;

        [Test]
        public void Test15DiDisposeCreate()
        {
            var services = new ServiceCollection();
            Action checker = () => _numTimeDisposeCalled++;
            services.AddTransient<IMyDisposableClass>(_ => new MyDisposableClass(checker));
            using var provider = services.BuildServiceProvider();

            _numTimeDisposeCalled = 0;
            using (var scope = provider.CreateScope())
            {
                ClassicAssert.NotNull(scope.ServiceProvider.GetRequiredService<IMyDisposableClass>());
                _numTimeDisposeCalled.ShouldEqual(0);
            }
        }

        [Test]
        public void Test16DiDisposeCalled()
        {
            var services = new ServiceCollection();
            Action checker = () => _numTimeDisposeCalled++;
            services.AddTransient<IMyDisposableClass>(_ => new MyDisposableClass(checker));
            using var provider = services.BuildServiceProvider();

            _numTimeDisposeCalled = 0;
            using (var scope = provider.CreateScope())
                ClassicAssert.NotNull(scope.ServiceProvider.GetRequiredService<IMyDisposableClass>());

            _numTimeDisposeCalled.ShouldEqual(1);
        }

        //--------------------------------------------------------------
        //register generic 

        [Test]
        public void Test20DiRegisterOpenGeneric()
        {
            var services = new ServiceCollection();
            services.AddTransient(typeof(IGenericInterface<>), typeof(GenericInterface<>));
            using var provider = services.BuildServiceProvider();

            var instance = provider.GetRequiredService<IGenericInterface<SimpleClass>>();
            (instance is GenericInterface<SimpleClass>).ShouldEqual(true);
            instance.GetTypeName().ShouldEqual(typeof(SimpleClass).Name);
        }
    }
}
