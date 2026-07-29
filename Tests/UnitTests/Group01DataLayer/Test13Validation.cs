#region licence
// The MIT License (MIT)
// 
// Filename: Test13Validation.cs
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
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using DataLayer.DataClasses.Concrete;
using DataLayer.Startup;
using Microsoft.Data.Sqlite;
using NUnit.Framework;
using Tests.Helpers;

namespace Tests.UnitTests.Group01DataLayer
{
    public class Test13Validation
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

        //--------------------------------------------------------------------
        //Tag.Slug uniqueness is now enforced by SampleWebAppDb.SaveChanges (CheckForUniqueSlugs),
        //which throws a ValidationException on a duplicate Slug. The old GenericServices
        //SaveChangesWithChecking()/SuccessOrErrors status object no longer exists.

        [Test]
        public void Check01ValidateTagOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP
                var snap = new DbSnapShot(db);

                //ATTEMPT
                var newTag = new Tag { Name = "non-duplicate slug", Slug = Guid.NewGuid().ToString("N") };
                db.Tags.Add(newTag);
                db.SaveChanges();

                //VERIFY
                snap.CheckSnapShot(db, 0, 0, 0, 1);
            }
        }

        [Test]
        public void Check02ValidateTagError()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP
                var existingTag = db.Tags.First();

                //ATTEMPT
                var dupTag = new Tag { Name = "duplicate slug", Slug = existingTag.Slug };
                db.Tags.Add(dupTag);
                var ex = Assert.Throws<ValidationException>(() => db.SaveChanges());

                //VERIFY
                ex.Message.ShouldEqual("The Slug on tag 'duplicate slug' must be unique and is already being used.");
            }
        }

        //--------------------------------------------------------------------
        //Post content/title rules live on Post as DataAnnotations + IValidatableObject. Under EF Core the
        //DbContext no longer runs entity validation on SaveChanges (that moved to MVC ModelState / the
        //PostCrudHelper), so these tests assert the Post's own validation via Validator.TryValidateObject,
        //which is exactly the rule-set the removed SaveChangesWithChecking() used to enforce.

        private static IList<ValidationResult> ValidatePost(Post post)
        {
            var results = new List<ValidationResult>();
            Validator.TryValidateObject(post, new ValidationContext(post), results, true);
            return results;
        }

        [Test]
        public void Check10ValidatePostOk()
        {
            using (var db = TestDbContext.CreateContext(_connection))
            {
                //SETUP
                var snap = new DbSnapShot(db);
                var existingTag = db.Tags.First();
                var existingBlogger = db.Blogs.First();

                //ATTEMPT
                var newPost = new Post()
                {
                    Blogger = existingBlogger,
                    Title = "Test post",
                    Content = "Nothing special",
                    Tags = new[] { existingTag }
                };
                var errors = ValidatePost(newPost);
                db.Posts.Add(newPost);
                db.SaveChanges();

                //VERIFY
                errors.Count.ShouldEqual(0);
                snap.CheckSnapShot(db, 1, 1);
            }
        }

        [Test]
        public void Check15ValidatePostTitleError()
        {
            //SETUP
            var newPost = new Post()
            {
                Title = "Test post!",
                Content = "Nothing special",
                Tags = new[] { new Tag() }
            };

            //ATTEMPT
            var errors = ValidatePost(newPost);

            //VERIFY
            errors.Count.ShouldEqual(1);
            errors[0].ErrorMessage.ShouldEqual("Sorry, but you can't get too excited and include a ! in the title.");
        }

        [Test]
        public void Check16ValidatePostTitleError()
        {
            //SETUP
            var newPost = new Post()
            {
                Title = "Test post?",
                Content = "Nothing special",
                Tags = new[] { new Tag() }
            };

            //ATTEMPT
            var errors = ValidatePost(newPost);

            //VERIFY
            errors.Count.ShouldEqual(1);
            errors[0].ErrorMessage.ShouldEqual("Sorry, but you can't ask a question, i.e. the title can't end with '?'.");
        }

        [Test]
        public void Check20ValidatePostContentOneError()
        {
            //SETUP
            var newPost = new Post()
            {
                Title = "Test post",
                Content = "Should not end sentence with sheep.",
                Tags = new[] { new Tag() }
            };

            //ATTEMPT
            var errors = ValidatePost(newPost);

            //VERIFY
            errors.Count.ShouldEqual(1);
            errors[0].ErrorMessage.ShouldEqual("Sorry. Not allowed to end a sentance with 'sheep'.");
        }

        [Test]
        public void Check21ValidatePostContentTwoErrors()
        {
            //SETUP
            var newPost = new Post()
            {
                Title = "Test post",
                Content = "Should not end sentence with sheep. Nor end sentence with lamb.",
                Tags = new[] { new Tag() }
            };

            //ATTEMPT
            var errors = ValidatePost(newPost);

            //VERIFY
            errors.Count.ShouldEqual(2);
            errors[0].ErrorMessage.ShouldEqual("Sorry. Not allowed to end a sentance with 'sheep'.");
            errors[1].ErrorMessage.ShouldEqual("Sorry. Not allowed to end a sentance with 'lamb'.");
        }
    }
}
