#region licence
// The MIT License (MIT)
// 
// Filename: Test11AutoFacModules.cs
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
using ServiceLayer.PostServices;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    [TestFixture]
    public class Test11ServiceLayerSetup
    {
        [OneTimeSetUp]
        public void FixtureSetUp()
        {
            TestDb.ResetBlogs(TestDataSelection.Small);
        }

        //-------------------------------------
        //DataLayer

        [Test]
        public void CheckSetupDbContextLifetimeScopeItems()
        {
            //SETUP
            using var provider = TestDb.CreateServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                var instance1 = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var instance2 = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                Assert.That(instance1, Is.Not.Null);
                Assert.That(instance2, Is.SameAs(instance1));                       //check that scope is working
                instance1.Database.GetDbConnection().Database.ShouldEqual(TestDb.DatabaseName);
            }
        }

        //---------------------------------------------
        //ServiceLayer, which also resolves DataLayer

        [Test]
        public void Test10ServiceSetupServiceLayer()
        {
            //SETUP
            using var provider = TestDb.CreateServiceProvider();

            //ATTEMPT & VERIFY
            CheckExampleServicesResolve(provider);
        }

        [Test]
        public void Test15SetupServiceLayerDirectGenerics()
        {
            //SETUP
            using var provider = TestDb.CreateServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                var instance = scope.ServiceProvider.GetRequiredService<ICrudServices>();
                Assert.That(instance, Is.Not.Null);
                (instance is CrudServices).ShouldEqual(true);
            }
        }

        [Test]
        public void Test16UseServiceLayerDirectGenerics()
        {
            //SETUP
            using var provider = TestDb.CreateServiceProvider();

            //ATTEMPT & VERIFY
            using (var scope = provider.CreateScope())
            {
                var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();
                var posts = service.ReadManyNoTracked<Post>().ToList();
                posts.Count.ShouldEqual(3);
            }
        }

        //------------------------------------------------------
        //MVC layer

        [Test]
        public void Test20ViaMvcSetup()
        {
            //SETUP
            using var factory = new TestWebApplicationFactory();

            //ATTEMPT & VERIFY
            CheckExampleServicesResolve(factory.Services);
        }

        //-------------------------------------------------------
        //private helper

        private static void CheckExampleServicesResolve(System.IServiceProvider provider)
        {
            using (var scope = provider.CreateScope())
            {
                var db1 = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var db2 = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                Assert.That(db1, Is.Not.Null);
                Assert.That(db2, Is.SameAs(db1));                       //check that scope is working
                db1.Database.GetDbConnection().Database.ShouldEqual(TestDb.DatabaseName);

                var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();
                Assert.That(service, Is.Not.Null);
                (service is CrudServices).ShouldEqual(true);
                (scope.ServiceProvider.GetRequiredService<ICrudServicesAsync>() is CrudServicesAsync).ShouldEqual(true);

                var postService1 = scope.ServiceProvider.GetRequiredService<IPostService>();
                var postService2 = scope.ServiceProvider.GetRequiredService<IPostService>();
                Assert.That(postService2, Is.SameAs(postService1));
                Assert.That(scope.ServiceProvider.GetRequiredService<IPostServiceAsync>(), Is.Not.Null);
            }
        }
    }
}
