#region licence
// The MIT License (MIT)
// 
// Filename: Test14ReadWriteBlogs.cs
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
using System;
using System.Linq;
using System.Threading;
using DataLayer.DataClasses.Concrete;
using DataLayer.Startup;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using NUnit.Framework;
using NUnit.Framework.Legacy;
using Tests.Helpers;

namespace Tests.UnitTests.Group01DataLayer
{
    public class Test14ReadWriteBlogs
    {
        private SqliteConnection _connection;

        [SetUp]
        public void SetUp()
        {
            _connection = TestDbContext.CreateOpenConnection();
            using (var db = TestDbContext.CreateContext(_connection))
                DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);
        }

        [TearDown]
        public void TearDown()
        {
            _connection?.Dispose();
        }

        [Test]
        public void Check01ReadBlogsNoPostsOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP

                //ATTEMPT
                var blogs = db.Blogs.ToList();

                //VERIFY
                blogs.Count.ShouldEqual(2);
                //EF Core does not lazy-load, so a navigation that was not Included stays null
                blogs.All(x => x.Posts == null).ShouldEqual(true);
            }
        }

        [Test]
        public void Check02ReadBlogsWithPostsOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP

                //ATTEMPT
                var blogs = db.Blogs.Include(x => x.Posts).ToList();

                //VERIFY
                blogs.Count.ShouldEqual(2);
                blogs.All(x => x.Posts != null).ShouldEqual(true);
                blogs.All(x => x.Posts.All(y => y.Tags == null)).ShouldEqual(true);
            }
        }

        [Test]
        public void Check03ReadBlogsWithPostTagsOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP

                //ATTEMPT
                var blogs = db.Blogs.Include(x => x.Posts).ThenInclude(y => y.Tags).ToList();

                //VERIFY
                blogs.Count.ShouldEqual(2);
                blogs.All(x => x.Posts != null).ShouldEqual(true);
                blogs.All(x => x.Posts.All(y => y.Tags != null)).ShouldEqual(true);

            }
        }

        [Test]
        public void Check05ReadPostsOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP

                //ATTEMPT
                var posts = db.Posts.ToList();

                //VERIFY
                posts.Count.ShouldEqual(3);
                //Under EF6 the virtual Blogger was lazy-loaded; EF Core has no lazy loading configured,
                //so neither the Blogger nor the Tags navigation is populated without an explicit Include.
                posts.All(x => x.Blogger == null).ShouldEqual(true);
                posts.All(x => x.Tags == null).ShouldEqual(true);
            }
        }


        [Test]
        public void Check06ReadPostsWithTagsOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP

                //ATTEMPT
                var posts = db.Posts.Include(x => x.Tags).ToList();

                //VERIFY
                posts.Count.ShouldEqual(3);
                //Tags were Included so they are loaded; Blogger was not Included and (no lazy loading) stays null
                posts.All(x => x.Blogger == null).ShouldEqual(true);
                posts.All(x => x.Tags != null).ShouldEqual(true);
            }
        }

        [Test]
        public void Check10ReadTAllocatedTagsWithUglySlugOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP

                //ATTEMPT
                var postsWithuglyTags = db.Posts.Where(x => x.Tags.Any(y => y.Slug == "ugly")).ToList();
                var uglyTagPosts = db.Tags.Include(x => x.Posts).Single(y => y.Slug == "ugly").Posts;

                //VERIFY
                postsWithuglyTags.Count.ShouldEqual(2);
                uglyTagPosts.Count().ShouldEqual(2);
            }
        }

        //-----------------------------------------------------------------
        //now adding new posts, tags etc.

        [Test]
        public void Check20AddPostOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP
                var snap = new DbSnapShot(db);

                //ATTEMPT
                var uglyTag = db.Tags.Single(x => x.Slug == "ugly");
                var jonBlogger = db.Blogs.First();
                var newPost = new Post
                {
                    Blogger = jonBlogger,
                    Content = "a few simple words.",
                    Title = "A new post",
                    Tags = new[] { uglyTag }
                };

                db.Posts.Add(newPost);
                db.SaveChanges();

                //VERIFY
                snap.CheckSnapShot(db, 1, 1);
                var uglyPosts = db.Tags.Include(x => x.Posts).Single(y => y.Slug == "ugly").Posts;
                uglyPosts.Count.ShouldEqual(3);
            }
        }

        [Test]
        public void Check21CheckUpdateSimpleOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP
                var snap = new DbSnapShot(db);
                var newGuid = Guid.NewGuid().ToString();

                //ATTEMPT
                var firstPost = db.Posts.First();
                firstPost.Title = newGuid;
                db.SaveChanges();

                //VERIFY
                snap.CheckSnapShot(db);
                db.Posts.First().Title.ShouldEqual(newGuid);
            }
        }


        [Test]
        public void Check22CheckUpdateLastUpdatedOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP
                var snap = new DbSnapShot(db);
                var firstPost = db.Posts.First();
                var originalDateTime = firstPost.LastUpdated;
                Thread.Sleep(400);

                //ATTEMPT
                firstPost.Title = Guid.NewGuid().ToString();
                db.SaveChanges();

                //VERIFY
                snap.CheckSnapShot(db);
                ClassicAssert.GreaterOrEqual(db.Posts.First().LastUpdated.Subtract(originalDateTime).TotalMilliseconds, 400);
            }
        }

        [Test]
        public void Check25UpdatePostToAddTagOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP
                var snap = new DbSnapShot(db);
                var badTag = db.Tags.Single(x => x.Slug == "bad");
                var firstPost = db.Posts.First();

                //ATTEMPT
                db.Entry(firstPost).Collection(x => x.Tags).Load();
                firstPost.Tags.Add(badTag);
                db.SaveChanges();

                //VERIFY
                snap.CheckSnapShot(db, 0, 1);
                firstPost = db.Blogs.Include(x => x.Posts).ThenInclude(y => y.Tags).First().Posts.First();
                firstPost.Tags.Count.ShouldEqual(3);
            }
        }

        [Test]
        public void Check26ReplaceTagsOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP
                var snap = new DbSnapShot(db);
                var firstPost = db.Posts.First();
                var tagsNotInFirstPostTracked = db.Tags.Where(x => x.Posts.All(y => y.PostId != firstPost.PostId)).ToList();

                //ATTEMPT

                db.Entry(firstPost).Collection(x => x.Tags).Load();
                firstPost.Tags = tagsNotInFirstPostTracked;
                db.SaveChanges();

                //VERIFY
                snap.CheckSnapShot(db, 0, -1);
                firstPost = db.Blogs.Include(x => x.Posts).ThenInclude(y => y.Tags).First().Posts.First();
                firstPost.Tags.Count.ShouldEqual(1);
            }
        }

        [Test]
        public void Check30CheckCreateLastUpdatedOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP
                var snap = new DbSnapShot(db);
                var firstPostUntracked = db.Posts.AsNoTracking().First();
                var originalDateTime = firstPostUntracked.LastUpdated;
                Thread.Sleep(400);

                //ATTEMPT
                //Reset the PK so EF Core treats this detached copy as a brand-new row (EF6 ignored the
                //store-generated key on Add; EF Core would otherwise try to insert the explicit PostId).
                firstPostUntracked.PostId = 0;
                firstPostUntracked.Title = Guid.NewGuid().ToString();
                firstPostUntracked.Blogger = db.Blogs.First();
                firstPostUntracked.Tags = db.Tags.Take(2).ToList();
                db.Posts.Add(firstPostUntracked);
                db.SaveChanges();

                //VERIFY
                snap.CheckSnapShot(db,1,2);
                var loadedPost = db.Posts.Single(x => x.PostId == firstPostUntracked.PostId);
                ClassicAssert.GreaterOrEqual(loadedPost.LastUpdated.Subtract(originalDateTime).TotalMilliseconds, 400);
            }
        }

        [Test]
        public void Check31CheckCreateDataOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP
                var snap = new DbSnapShot(db);
                var firstPostUntracked = db.Posts.AsNoTracking().First();
                var firstTwoTags = db.Tags.Take(2).ToList();

                //ATTEMPT
                firstPostUntracked.PostId = 0;
                firstPostUntracked.Title = Guid.NewGuid().ToString();
                firstPostUntracked.Blogger = db.Blogs.First();
                firstPostUntracked.Tags = firstTwoTags;
                db.Posts.Add(firstPostUntracked);
                db.SaveChanges();

                //VERIFY
                snap.CheckSnapShot(db,1,2);
                var loadedPost = db.Posts.Include( x => x.Blogger).Include( x => x.Tags).Single(x => x.PostId == firstPostUntracked.PostId);
                loadedPost.Blogger.BlogId.ShouldEqual(db.Blogs.First().BlogId);
                CollectionAssert.AreEquivalent(firstTwoTags.Select(x => x.TagId), loadedPost.Tags.Select(x => x.TagId));
            }
        }
    }
}
