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
using System.Linq;
using DataLayer.DataClasses.Concrete;
using DataLayer.Startup;
using NUnit.Framework;
using Tests.Helpers;

namespace Tests.UnitTests.Group01DataLayer
{
    /// <summary>
    /// EF6 ran the data annotations and IValidatableObject inside SaveChanges and GenericServices'
    /// SaveChangesWithChecking turned them into an ISuccessOrErrors. EF Core does no validation at all,
    /// so SampleWebAppDb.SaveChangesWithValidation reimplements it and returns a StatusGeneric status.
    /// This fixture is the safety net for that reimplementation.
    /// </summary>
    public class Test13Validation
    {

        [OneTimeSetUp]
        public void SetUpFixture()
        {
            TestDbHelper.ResetDatabase(TestDataSelection.Small);
        }

        [Test]
        public void Check01ValidateTagOk()
        {
            using (var db = TestDbHelper.CreateContext())
            {
                //SETUP
                var snap = new DbSnapShot(db);

                //ATTEMPT
                var dupTag = new Tag { Name = "non-duplicate slug", Slug = Guid.NewGuid().ToString("N") };
                db.Tags.Add(dupTag);
                var status = db.SaveChangesWithValidation();

                //VERIFY
                status.IsValid.ShouldEqual(true, status.Errors);
                snap.CheckSnapShot(db, 0,0,0,1);
            }
        }

        [Test]
        public void Check02ValidateTagError()
        {
            using (var db = TestDbHelper.CreateContext())
            {
                //SETUP
                var existingTag = db.Tags.First();

                //ATTEMPT
                var dupTag = new Tag {Name = "duplicate slug", Slug = existingTag.Slug};
                db.Tags.Add(dupTag);
                var status = db.SaveChangesWithValidation();

                //VERIFY
                status.IsValid.ShouldEqual(false);
                status.Errors.Count.ShouldEqual(1);
                status.Errors[0].ErrorResult.ErrorMessage.ShouldEqual("The Slug on tag 'duplicate slug' must be unique and is already being used.");
            }
        }

        [Test]
        public void Check10ValidatePostOk()
        {
            using (var db = TestDbHelper.CreateContext())
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
                db.Posts.Add(newPost);
                var status = db.SaveChangesWithValidation();

                //VERIFY
                status.IsValid.ShouldEqual(true, status.Errors);
                snap.CheckSnapShot(db,1,1);
            }
        }


        [Test]
        public void Check15ValidatePostTitleError()
        {
            using (var db = TestDbHelper.CreateContext())
            {
                //SETUP
                var existingTag = db.Tags.First();
                var existingBlogger = db.Blogs.First();

                //ATTEMPT
                var newPost = new Post()
                {
                    Blogger = existingBlogger,
                    Title = "Test post!",
                    Content = "Nothing special",
                    Tags = new[] { existingTag }
                };
                db.Posts.Add(newPost);
                var status = db.SaveChangesWithValidation();

                //VERIFY
                status.IsValid.ShouldEqual(false);
                status.Errors.Count.ShouldEqual(1);
                status.Errors[0].ErrorResult.ErrorMessage.ShouldEqual("Sorry, but you can't get too excited and include a ! in the title.");
            }
        }

        [Test]
        public void Check16ValidatePostTitleError()
        {
            using (var db = TestDbHelper.CreateContext())
            {
                //SETUP
                var existingTag = db.Tags.First();
                var existingBlogger = db.Blogs.First();

                //ATTEMPT
                var newPost = new Post()
                {
                    Blogger = existingBlogger,
                    Title = "Test post?",
                    Content = "Nothing special",
                    Tags = new[] { existingTag }
                };
                db.Posts.Add(newPost);
                var status = db.SaveChangesWithValidation();

                //VERIFY
                status.IsValid.ShouldEqual(false);
                status.Errors.Count.ShouldEqual(1);
                status.Errors[0].ErrorResult.ErrorMessage.ShouldEqual("Sorry, but you can't ask a question, i.e. the title can't end with '?'.");
            }
        }

        [Test]
        public void Check20ValidatePostContentOneError()
        {
            using (var db = TestDbHelper.CreateContext())
            {
                //SETUP
                var existingTag = db.Tags.First();
                var existingBlogger = db.Blogs.First();

                //ATTEMPT
                var newPost = new Post()
                {
                    Blogger = existingBlogger,
                    Title = "Test post",
                    Content = "Should not end sentence with sheep.",
                    Tags = new[] { existingTag }
                };
                db.Posts.Add(newPost);
                var status = db.SaveChangesWithValidation();

                //VERIFY
                status.IsValid.ShouldEqual(false);
                status.Errors.Count.ShouldEqual(1);
                status.Errors[0].ErrorResult.ErrorMessage.ShouldEqual("Sorry. Not allowed to end a sentance with 'sheep'.");
            }
        }


        [Test]
        public void Check21ValidatePostContentTwoErrors()
        {
            using (var db = TestDbHelper.CreateContext())
            {
                //SETUP
                var existingTag = db.Tags.First();
                var existingBlogger = db.Blogs.First();

                //ATTEMPT
                var newPost = new Post()
                {
                    Blogger = existingBlogger,
                    Title = "Test post",
                    Content = "Should not end sentence with sheep. Nor end sentence with lamb.",
                    Tags = new[] { existingTag }
                };
                db.Posts.Add(newPost);
                var status = db.SaveChangesWithValidation();

                //VERIFY
                status.IsValid.ShouldEqual(false);
                status.Errors.Count.ShouldEqual(2);
                status.Errors[0].ErrorResult.ErrorMessage.ShouldEqual("Sorry. Not allowed to end a sentance with 'sheep'.");
                status.Errors[1].ErrorResult.ErrorMessage.ShouldEqual("Sorry. Not allowed to end a sentance with 'lamb'.");
            }
        }

        [Test]
        public void Check22ValidatePostContentCowAndCalfErrors()
        {
            using (var db = TestDbHelper.CreateContext())
            {
                //SETUP
                var existingTag = db.Tags.First();
                var existingBlogger = db.Blogs.First();

                //ATTEMPT
                var newPost = new Post()
                {
                    Blogger = existingBlogger,
                    Title = "Test post",
                    Content = "Should not end sentence with cow. Nor end sentence with calf.",
                    Tags = new[] { existingTag }
                };
                db.Posts.Add(newPost);
                var status = db.SaveChangesWithValidation();

                //VERIFY
                status.IsValid.ShouldEqual(false);
                status.Errors.Count.ShouldEqual(2);
                status.Errors[0].ErrorResult.ErrorMessage.ShouldEqual("Sorry. Not allowed to end a sentance with 'cow'.");
                status.Errors[1].ErrorResult.ErrorMessage.ShouldEqual("Sorry. Not allowed to end a sentance with 'calf'.");
            }
        }

        [Test]
        public void Check25ValidatePostNoTagsError()
        {
            using (var db = TestDbHelper.CreateContext())
            {
                //SETUP
                var existingBlogger = db.Blogs.First();

                //ATTEMPT
                var newPost = new Post()
                {
                    Blogger = existingBlogger,
                    Title = "Test post",
                    Content = "Nothing special",
                    Tags = new Tag[0]
                };
                db.Posts.Add(newPost);
                var status = db.SaveChangesWithValidation();

                //VERIFY
                status.IsValid.ShouldEqual(false);
                status.Errors.Count.ShouldEqual(1);
                status.Errors[0].ErrorResult.ErrorMessage.ShouldEqual("The post must have at least one Tag.");
                status.Errors[0].ErrorResult.MemberNames.Single().ShouldEqual("AllocatedTags");
            }
        }
    }
}
