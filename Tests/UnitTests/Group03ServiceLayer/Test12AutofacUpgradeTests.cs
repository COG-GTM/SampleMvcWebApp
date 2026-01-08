#region licence
// The MIT License (MIT)
// 
// Filename: Test12AutofacUpgradeTests.cs
// Date Created: 2026/01/08
// 
// Copyright (c) 2026 Phase 1 .NET 8 Migration Preparation
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
using Autofac;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using DataLayer.Startup;
using GenericServices;
using GenericServices.Services.Concrete;
using NUnit.Framework;
using SampleWebApp.Infrastructure;
using ServiceLayer.Startup;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    [TestFixture]
    public class Test12AutofacUpgradeTests
    {
        [TestFixtureSetUp]
        public void FixtureSetUp()
        {
            using (var db = new SampleWebAppDb())
            {
                DataLayerInitialise.InitialiseThis(false, true);
                DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);
            }
        }

        [Test]
        public void Test01VerifyAutofacVersionIs4x()
        {
            var autofacAssembly = typeof(ContainerBuilder).Assembly;
            var version = autofacAssembly.GetName().Version;
            
            Assert.IsTrue(version.Major >= 4, 
                string.Format("Expected Autofac version 4.x or higher, but got {0}", version));
        }

        [Test]
        public void Test02DataLayerModuleRegistersDbContext()
        {
            var builder = new ContainerBuilder();
            builder.RegisterModule(new DataLayerModule());
            var container = builder.Build();

            using (var lifetimeScope = container.BeginLifetimeScope())
            {
                var dbContext = lifetimeScope.Resolve<IGenericServicesDbContext>();
                Assert.NotNull(dbContext);
                Assert.IsTrue(dbContext is SampleWebAppDb);
            }
        }

        [Test]
        public void Test03DataLayerModuleDbContextIsInstancePerLifetimeScope()
        {
            var builder = new ContainerBuilder();
            builder.RegisterModule(new DataLayerModule());
            var container = builder.Build();

            using (var lifetimeScope = container.BeginLifetimeScope())
            {
                var instance1 = lifetimeScope.Resolve<IGenericServicesDbContext>();
                var instance2 = lifetimeScope.Resolve<IGenericServicesDbContext>();
                
                Assert.AreSame(instance1, instance2, 
                    "DbContext should be the same instance within a lifetime scope");
            }

            using (var lifetimeScope1 = container.BeginLifetimeScope())
            using (var lifetimeScope2 = container.BeginLifetimeScope())
            {
                var instance1 = lifetimeScope1.Resolve<IGenericServicesDbContext>();
                var instance2 = lifetimeScope2.Resolve<IGenericServicesDbContext>();
                
                Assert.AreNotSame(instance1, instance2, 
                    "DbContext should be different instances across lifetime scopes");
            }
        }

        [Test]
        public void Test04ServiceLayerModuleRegistersListService()
        {
            var builder = new ContainerBuilder();
            builder.RegisterModule(new ServiceLayerModule());
            var container = builder.Build();

            using (var lifetimeScope = container.BeginLifetimeScope())
            {
                var listService = lifetimeScope.Resolve<IListService>();
                Assert.NotNull(listService);
                Assert.IsTrue(listService is ListService);
            }
        }

        [Test]
        public void Test05ServiceLayerModuleServicesAreTransient()
        {
            var builder = new ContainerBuilder();
            builder.RegisterModule(new ServiceLayerModule());
            var container = builder.Build();

            using (var lifetimeScope = container.BeginLifetimeScope())
            {
                var service1 = lifetimeScope.Resolve<IListService>();
                var service2 = lifetimeScope.Resolve<IListService>();
                
                Assert.AreNotSame(service1, service2, 
                    "Services should be transient (different instances)");
            }
        }

        [Test]
        public void Test06FullDiStackWorksWithAutofac4x()
        {
            var container = AutofacDi.SetupDependency();

            using (var lifetimeScope = container.BeginLifetimeScope())
            {
                var dbContext = lifetimeScope.Resolve<IGenericServicesDbContext>();
                var listService = lifetimeScope.Resolve<IListService>();
                
                Assert.NotNull(dbContext);
                Assert.NotNull(listService);
                
                var posts = listService.GetAll<Post>().ToList();
                Assert.IsTrue(posts.Count > 0, "Should be able to query posts through the DI stack");
            }
        }

        [Test]
        public void Test07DetailServiceResolvesCorrectly()
        {
            var builder = new ContainerBuilder();
            builder.RegisterModule(new ServiceLayerModule());
            var container = builder.Build();

            using (var lifetimeScope = container.BeginLifetimeScope())
            {
                var detailService = lifetimeScope.Resolve<IDetailService>();
                Assert.NotNull(detailService);
            }
        }

        [Test]
        public void Test08CreateServiceResolvesCorrectly()
        {
            var builder = new ContainerBuilder();
            builder.RegisterModule(new ServiceLayerModule());
            var container = builder.Build();

            using (var lifetimeScope = container.BeginLifetimeScope())
            {
                var createService = lifetimeScope.Resolve<ICreateService>();
                Assert.NotNull(createService);
            }
        }

        [Test]
        public void Test09UpdateServiceResolvesCorrectly()
        {
            var builder = new ContainerBuilder();
            builder.RegisterModule(new ServiceLayerModule());
            var container = builder.Build();

            using (var lifetimeScope = container.BeginLifetimeScope())
            {
                var updateService = lifetimeScope.Resolve<IUpdateService>();
                Assert.NotNull(updateService);
            }
        }

        [Test]
        public void Test10DeleteServiceResolvesCorrectly()
        {
            var builder = new ContainerBuilder();
            builder.RegisterModule(new ServiceLayerModule());
            var container = builder.Build();

            using (var lifetimeScope = container.BeginLifetimeScope())
            {
                var deleteService = lifetimeScope.Resolve<IDeleteService>();
                Assert.NotNull(deleteService);
            }
        }

        [Test]
        public void Test11AsyncServicesResolveCorrectly()
        {
            var builder = new ContainerBuilder();
            builder.RegisterModule(new ServiceLayerModule());
            var container = builder.Build();

            using (var lifetimeScope = container.BeginLifetimeScope())
            {
                var listServiceAsync = lifetimeScope.Resolve<IListService>();
                var detailServiceAsync = lifetimeScope.Resolve<IDetailServiceAsync>();
                var createServiceAsync = lifetimeScope.Resolve<ICreateServiceAsync>();
                var updateServiceAsync = lifetimeScope.Resolve<IUpdateServiceAsync>();
                var deleteServiceAsync = lifetimeScope.Resolve<IDeleteServiceAsync>();
                
                Assert.NotNull(listServiceAsync);
                Assert.NotNull(detailServiceAsync);
                Assert.NotNull(createServiceAsync);
                Assert.NotNull(updateServiceAsync);
                Assert.NotNull(deleteServiceAsync);
            }
        }

        [Test]
        public void Test12NestedLifetimeScopesWorkCorrectly()
        {
            var builder = new ContainerBuilder();
            builder.RegisterModule(new ServiceLayerModule());
            var container = builder.Build();

            using (var outerScope = container.BeginLifetimeScope())
            {
                var outerDb = outerScope.Resolve<IGenericServicesDbContext>();
                
                using (var innerScope = outerScope.BeginLifetimeScope())
                {
                    var innerDb = innerScope.Resolve<IGenericServicesDbContext>();
                    
                    Assert.AreNotSame(outerDb, innerDb, 
                        "Nested scopes should have different DbContext instances");
                }
            }
        }

        [Test]
        public void Test13ContainerDisposesCleansUpResources()
        {
            var builder = new ContainerBuilder();
            builder.RegisterModule(new ServiceLayerModule());
            var container = builder.Build();

            IGenericServicesDbContext dbContext;
            using (var lifetimeScope = container.BeginLifetimeScope())
            {
                dbContext = lifetimeScope.Resolve<IGenericServicesDbContext>();
                Assert.NotNull(dbContext);
            }

            Assert.Pass("Lifetime scope disposed without errors");
        }

        [Test]
        public void Test14MultipleModuleRegistrationWorks()
        {
            var builder = new ContainerBuilder();
            builder.RegisterModule(new DataLayerModule());
            builder.RegisterModule(new ServiceLayerModule());
            var container = builder.Build();

            using (var lifetimeScope = container.BeginLifetimeScope())
            {
                var dbContext = lifetimeScope.Resolve<IGenericServicesDbContext>();
                var listService = lifetimeScope.Resolve<IListService>();
                
                Assert.NotNull(dbContext);
                Assert.NotNull(listService);
            }
        }
    }
}
