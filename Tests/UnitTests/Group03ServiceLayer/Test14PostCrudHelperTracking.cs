#region licence
// The MIT License (MIT)
// 
// Filename: Test14PostCrudHelperTracking.cs
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
using DataLayer.DataClasses.Concrete;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using NUnit.Framework;
using ServiceLayer.PostServices;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    /// <summary>
    /// A create that fails validation must not leave an Added Post behind in the request-scoped
    /// change tracker, otherwise any later SaveChanges on the same scope would persist a
    /// half-built row that the user never successfully created.
    /// </summary>
    public class Test14PostCrudHelperTracking
    {
        private SqliteConnection _connection;

        [SetUp]
        public void SetUp()
        {
            _connection = TestDbContext.CreateOpenConnection();
        }

        [TearDown]
        public void TearDown()
        {
            _connection.Dispose();
        }

        [Test]
        public void Check01FailedCreateLeavesNoAddedPostTracked()
        {
            //SETUP
            using var db = TestDbContext.CreateContext(_connection);
            var helper = new PostCrudHelper(db);
            //no blogger selected and no tags selected, so the create must fail
            var dto = new DetailPostDto { Title = "Test title", Content = "Test content" };

            //ATTEMPT
            var status = helper.CreatePost(dto);

            //VERIFY
            Assert.That(status.IsValid, Is.False);
            Assert.That(db.ChangeTracker.Entries<Post>()
                .Any(x => x.State == EntityState.Added), Is.False);

            //a later save on the same scope must not persist anything
            db.SaveChanges();
            Assert.That(db.Posts.Count(), Is.EqualTo(0));
        }
    }
}
