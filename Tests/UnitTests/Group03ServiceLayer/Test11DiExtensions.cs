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
using System;
using System.Linq;
using BizLayer.Startup;
using DataLayer.DataClasses;
using DataLayer.Startup;
using GenericServices;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using NUnit.Framework;
using NUnit.Framework.Legacy;
using ServiceLayer.PostServices;
using ServiceLayer.Startup;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    /// <summary>
    /// The Autofac modules (DataLayerModule/ServiceLayerModule/BizLayerModule) were replaced by the
    /// IServiceCollection extension methods AddServiceLayer()/AddBizLayer(). This fixture builds a
    /// ServiceCollection the same way the web app's Program.cs does and asserts the service layer resolves
    /// and behaves (ICrudServices/IPostCrudHelper resolve, the DbContext is scoped, and a read works).
    /// </summary>
    [TestFixture]
    public class Test11DiExtensions
    {
        private SqliteConnection _connection;
        private ServiceProvider _provider;

        [OneTimeSetUp]
        public void FixtureSetUp()
        {
            _connection = TestDbContext.CreateOpenConnection();

            var services = new ServiceCollection();
            services.AddDbContext<SampleWebAppDb>(options => options.UseSqlite(_connection));
            services.AddServiceLayer();
            services.AddBizLayer();
            _provider = services.BuildServiceProvider();

            using (var scope = _provider.CreateScope())
            {
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);
            }
        }

        [OneTimeTearDown]
        public void FixtureTearDown()
        {
            _provider?.Dispose();
            _connection?.Dispose();
        }

        //-------------------------------------
        //DataLayer - the DbContext must be scoped

        [Test]
        public void CheckDbContextIsScoped()
        {
            //SETUP & ATTEMPT & VERIFY
            SampleWebAppDb scope1Instance;
            using (var scope = _provider.CreateScope())
            {
                scope1Instance = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var sameScopeInstance = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                ClassicAssert.NotNull(scope1Instance);
                ClassicAssert.AreSame(scope1Instance, sameScopeInstance);      //same instance within a scope
            }

            using (var scope = _provider.CreateScope())
            {
                var scope2Instance = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                ClassicAssert.AreNotSame(scope1Instance, scope2Instance);      //different instance across scopes
            }
        }

        //---------------------------------------------
        //ServiceLayer, which also resolves DataLayer

        [Test]
        public void Test10ServiceLayerServicesResolve()
        {
            //SETUP & ATTEMPT & VERIFY
            using (var scope = _provider.CreateScope())
            {
                var crudServices = scope.ServiceProvider.GetService<ICrudServices>();
                var crudServicesAsync = scope.ServiceProvider.GetService<ICrudServicesAsync>();
                var postCrudHelper = scope.ServiceProvider.GetService<IPostCrudHelper>();

                ClassicAssert.NotNull(crudServices);
                ClassicAssert.NotNull(crudServicesAsync);
                ClassicAssert.NotNull(postCrudHelper);
            }
        }

        [Test]
        public void Test16UseCrudServicesReadPosts()
        {
            //SETUP & ATTEMPT
            using (var scope = _provider.CreateScope())
            {
                var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();
                var posts = service.ReadManyNoTracked<SimplePostDto>().ToList();

                //VERIFY
                posts.Count.ShouldEqual(3);
            }
        }

        /// <summary>
        /// The Post DTOs expose the Tag entity collection directly, so the GenericServices read
        /// projection has to project Post.Tags into DTO.Tags. This asserts the projection really does
        /// fill that collection, which is what the computed TagNames property renders.
        /// </summary>
        [Test]
        public void Test17CrudServicesReadProjectsTagsCollection()
        {
            //SETUP & ATTEMPT
            using (var scope = _provider.CreateScope())
            {
                var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();
                var postId = service.ReadManyNoTracked<SimplePostDto>().First().PostId;

                var simple = service.ReadManyNoTracked<SimplePostDto>()
                    .Single(x => x.PostId == postId);
                var detail = service.ReadSingle<DetailPostDto>(postId);

                //VERIFY
                ClassicAssert.NotNull(simple.Tags);
                ClassicAssert.NotNull(detail.Tags);
                ClassicAssert.IsTrue(detail.Tags.Any());
                ClassicAssert.IsFalse(string.IsNullOrEmpty(detail.TagNames));
            }
        }
    }
}
