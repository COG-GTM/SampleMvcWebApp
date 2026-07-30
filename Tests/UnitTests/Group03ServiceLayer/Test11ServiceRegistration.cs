#region licence
// The MIT License (MIT)
// 
// Filename: Test11ServiceRegistration.cs
// Date Created: 2014/05/20
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
using DataLayer.Startup;
using GenericServices;
using Microsoft.Extensions.DependencyInjection;
using NUnit.Framework;
using ServiceLayer.PostServices;
using ServiceLayer.Startup;
using ServiceLayer.TagServices;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    /// <summary>
    /// Autofac and its DataLayerModule/ServiceLayerModule/AutofacDi classes have been dropped in
    /// favour of the built-in container (MIGRATION_NOTES.md section 8.2), so this checks the
    /// AddDataLayer/AddServiceLayer registrations that replaced them.
    /// </summary>
    public class Test11ServiceRegistration
    {

        [OneTimeSetUp]
        public void FixtureSetUp()
        {
            TestDbHelper.ResetDatabase(TestDataSelection.Small);
        }

        private static ServiceProvider BuildDataLayerProvider()
        {
            var services = new ServiceCollection();
            services.AddDataLayer(TestDbHelper.ConnectionString);
            return services.BuildServiceProvider();
        }

        private static ServiceProvider BuildServiceLayerProvider()
        {
            var services = new ServiceCollection();
            services.AddServiceLayer(TestDbHelper.ConnectionString);
            return services.BuildServiceProvider();
        }

        //-------------------------------------
        //DataLayer

        [Test]
        public void CheckSetupDbContextLifetimeScopeItems()
        {
            //SETUP
            using (var provider = BuildDataLayerProvider())
            {
                //ATTEMPT & VERIFY
                using (var scope = provider.CreateScope())
                {
                    var instance1 = scope.ServiceProvider.GetService<SampleWebAppDb>();
                    var instance2 = scope.ServiceProvider.GetService<SampleWebAppDb>();
                    Assert.That(instance1, Is.Not.Null);
                    Assert.That(instance1, Is.SameAs(instance2));            //check that the scope is working
                }

                using (var scope = provider.CreateScope())
                using (var otherScope = provider.CreateScope())
                {
                    var instance1 = scope.ServiceProvider.GetService<SampleWebAppDb>();
                    var instance2 = otherScope.ServiceProvider.GetService<SampleWebAppDb>();
                    Assert.That(instance1, Is.Not.SameAs(instance2));        //DbContext must be scoped, not singleton
                }
            }
        }

        //---------------------------------------------
        //ServiceLayer, which also resolves DataLayer

        [Test]
        public void Test10ServiceSetupServiceLayer()
        {
            //SETUP
            using (var provider = BuildServiceLayerProvider())
            {
                //ATTEMPT & VERIFY
                CheckExampleServicesResolve(provider);
            }
        }

        [Test]
        public void Test15SetupServiceLayerDirectGenerics()
        {
            //SETUP
            using (var provider = BuildServiceLayerProvider())
            {
                //ATTEMPT & VERIFY
                using (var scope = provider.CreateScope())
                {
                    var syncService = scope.ServiceProvider.GetService<ICrudServices>();
                    var asyncService = scope.ServiceProvider.GetService<ICrudServicesAsync>();
                    Assert.That(syncService, Is.Not.Null);
                    Assert.That(asyncService, Is.Not.Null);
                }
            }
        }

        [Test]
        public void Test16UseServiceLayerDirectGenerics()
        {
            //SETUP
            using (var provider = BuildServiceLayerProvider())
            {
                //ATTEMPT & VERIFY
                using (var scope = provider.CreateScope())
                {
                    var service = scope.ServiceProvider.GetService<ICrudServices>();
                    var posts = service.ReadManyNoTracked<SimplePostDto>().ToList();
                    var tags = service.ReadManyNoTracked<TagListDto>().ToList();
                    service.IsValid.ShouldEqual(true, service.GetAllErrors());
                    posts.Count.ShouldEqual(3);
                    tags.Count.ShouldEqual(3);
                }
            }
        }

        //------------------------------------------------------
        //MVC layer

        [Test]
        public void Test20ViaMvcSetup()
        {
            //SETUP
            //this is what SampleWebApp's Program.cs does
            var services = new ServiceCollection();
            services.AddControllersWithViews();
            services.AddServiceLayer(TestDbHelper.ConnectionString);

            //ATTEMPT & VERIFY
            using (var provider = services.BuildServiceProvider())
            {
                CheckExampleServicesResolve(provider);
            }
        }

        //-------------------------------------------------------
        //private helper

        private static void CheckExampleServicesResolve(ServiceProvider provider)
        {
            using (var scope = provider.CreateScope())
            {
                //DataLayer
                var db1 = scope.ServiceProvider.GetService<SampleWebAppDb>();
                var db2 = scope.ServiceProvider.GetService<SampleWebAppDb>();
                Assert.That(db1, Is.Not.Null);
                Assert.That(db1, Is.SameAs(db2));                   //check that the scope is working

                //ServiceLayer - EfCore.GenericServices
                var crudService = scope.ServiceProvider.GetService<ICrudServices>();
                var crudServiceAsync = scope.ServiceProvider.GetService<ICrudServicesAsync>();
                Assert.That(crudService, Is.Not.Null);
                Assert.That(crudServiceAsync, Is.Not.Null);

                //ServiceLayer - hand-written services
                var postService = scope.ServiceProvider.GetService<IPostDtoService>();
                var postServiceAsync = scope.ServiceProvider.GetService<IPostDtoServiceAsync>();
                Assert.That(postService, Is.Not.Null);
                Assert.That(postServiceAsync, Is.Not.Null);
                (postService is PostDtoService).ShouldEqual(true);
            }
        }
    }
}
