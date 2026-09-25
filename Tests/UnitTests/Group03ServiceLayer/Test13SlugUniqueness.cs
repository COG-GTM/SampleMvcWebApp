#region licence
// The MIT License (MIT)
// 
// Filename: Test13SlugUniqueness.cs
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
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using NUnit.Framework;
using ServiceLayer.Startup;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    /// <summary>
    /// A duplicate Tag.Slug is rejected by the data layer with a ValidationException on a direct SaveChanges,
    /// but going through GenericServices that would escape as an unhandled HTTP 500. AddServiceLayer wires a
    /// GenericServices BeforeSaveChanges hook so the same problem is reported as a status/validation error
    /// (which controllers copy into ModelState) and SaveChanges is never reached.
    /// </summary>
    public class Test13SlugUniqueness
    {
        private SqliteConnection _connection;
        private ServiceProvider _provider;

        [SetUp]
        public void SetUp()
        {
            _connection = TestDbContext.CreateOpenConnection();

            var services = new ServiceCollection();
            services.AddDbContext<SampleWebAppDb>(options => options.UseSqlite(_connection));
            services.AddServiceLayer();
            _provider = services.BuildServiceProvider();

            using (var scope = _provider.CreateScope())
            {
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);
            }
        }

        [TearDown]
        public void TearDown()
        {
            _provider?.Dispose();
            _connection?.Dispose();
        }

        [Test]
        public void Check01CreateTagWithUniqueSlugOk()
        {
            using (var scope = _provider.CreateScope())
            {
                //SETUP
                var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();
                var startCount = service.ReadManyNoTracked<Tag>().Count();

                //ATTEMPT
                service.CreateAndSave(new Tag { Name = "brand new", Slug = "brandnew" });

                //VERIFY
                service.IsValid.ShouldEqual(true);
                service.ReadManyNoTracked<Tag>().Count().ShouldEqual(startCount + 1);
            }
        }

        [Test]
        public void Check05CreateTagWithDuplicateSlugReturnsErrorNotException()
        {
            using (var scope = _provider.CreateScope())
            {
                //SETUP
                var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();
                var existingSlug = service.ReadManyNoTracked<Tag>().First().Slug;
                var startCount = service.ReadManyNoTracked<Tag>().Count();

                //ATTEMPT
                service.CreateAndSave(new Tag { Name = "duplicate slug", Slug = existingSlug });

                //VERIFY
                service.IsValid.ShouldEqual(false);
                service.GetAllErrors().ShouldEqual("The Slug on tag 'duplicate slug' must be unique and is already being used.");
                service.ReadManyNoTracked<Tag>().Count().ShouldEqual(startCount);
            }
        }
    }
}
