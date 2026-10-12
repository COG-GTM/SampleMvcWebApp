#region licence
// The MIT License (MIT)
// 
// Filename: Test11ServiceCollectionSetup.cs
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
using System.Linq;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using DataLayer.Startup;
using GenericServices;
using GenericServices.PublicButHidden;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using NUnit.Framework;
using NUnit.Framework.Legacy;
using ServiceLayer.PostServices;
using ServiceLayer.Startup;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    [TestFixture]
    public class Test11ServiceCollectionSetup
    {

        [OneTimeSetUp]
        public void FixtureSetUp()
        {
            TestDbHelper.MigrateAndResetBlogs(TestDataSelection.Small);
        }


        //-------------------------------------
        //DataLayer

        [Test]
        public void CheckSetupDbContextLifetimeScopeItems()
        {
            //SETUP
            var services = new ServiceCollection();
            services.AddDataLayer(TestDbHelper.ConnectionString);
            using var container = services.BuildServiceProvider(validateScopes: true);

            //ATTEMPT & VERIFY
            using (var lifetimeScope = container.CreateScope())
            {
                var instance1 = lifetimeScope.ServiceProvider.GetService<SampleWebAppDb>();
                var instance2 = lifetimeScope.ServiceProvider.GetService<SampleWebAppDb>();
                ClassicAssert.NotNull(instance1);
                (instance1 is SampleWebAppDb).ShouldEqual(true);
                ClassicAssert.AreSame(instance1, instance2);                       //check that lifetimescope is working
            }
        }


        //---------------------------------------------
        //ServiceLayer, which also resolves DataLayer

        [Test]
        public void Test10ServiceSetupServiceLayer()
        {
            //SETUP
            using var container = TestDbHelper.CreateServiceLayerProvider();

            //ATTEMPT & VERIFY
            CheckExampleServicesResolve(container);
        }


        [Test]
        public void Test15SetupServiceLayerDirectGenerics()
        {
            //SETUP
            using var container = TestDbHelper.CreateServiceLayerProvider();

            //ATTEMPT & VERIFY
            using (var lifetimeScope = container.CreateScope())
            {
                var instance = lifetimeScope.ServiceProvider.GetService<ICrudServices>();
                ClassicAssert.NotNull(instance);
                (instance is CrudServices).ShouldEqual(true);
            }
        }

        [Test]
        public void Test16UseServiceLayerDirectGenerics()
        {
            //SETUP
            using var container = TestDbHelper.CreateServiceLayerProvider();

            //ATTEMPT & VERIFY
            using (var lifetimeScope = container.CreateScope())
            {
                var service = lifetimeScope.ServiceProvider.GetService<ICrudServices>();
                var posts = service.ReadManyNoTracked<Post>().ToList();
                posts.Count.ShouldEqual(3);
            }
        }

        //-------------------------------------------------------
        //private helper

        internal static void CheckExampleServicesResolve(IServiceProvider container)
        {
            using (var lifetimeScope = container.CreateScope())
            {
                var provider = lifetimeScope.ServiceProvider;

                //DataLayer
                var db1 = provider.GetService<SampleWebAppDb>();
                var db2 = provider.GetService<SampleWebAppDb>();
                ClassicAssert.NotNull(db1);
                ClassicAssert.AreSame(db1, db2);
                ClassicAssert.AreSame(db1, provider.GetService<DbContext>());

                //ServiceLayer - generic
                var service1 = provider.GetService<ICrudServices>();
                var service2 = provider.GetService<ICrudServices>();
                ClassicAssert.NotNull(service1);
                ClassicAssert.AreNotSame(service1, service2);
                (service1 is CrudServices).ShouldEqual(true);
                var asyncService = provider.GetService<ICrudServicesAsync>();
                ClassicAssert.NotNull(asyncService);
                (asyncService is CrudServicesAsync).ShouldEqual(true);

                //ServiceLayer - post services
                ClassicAssert.NotNull(provider.GetService<IDetailPostService>());
                ClassicAssert.NotNull(provider.GetService<IDetailPostServiceAsync>());
            }
        }
    }
}
