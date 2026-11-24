#region licence
// The MIT License (MIT)
// 
// Filename: Test10DiSimple.cs
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
using System.Threading;
using System.Threading.Tasks;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using DataLayer.Startup;
using GenericServices;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using NUnit.Framework;
using ServiceLayer.PostServices;
using ServiceLayer.Startup;
using ServiceLayer.TagServices;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    class Test20CrudServices
    {
        private ServiceProvider _provider;

        [OneTimeSetUp]
        public void FixtureSetUp()
        {
            _provider = TestDb.CreateServiceProvider();
        }

        [OneTimeTearDown]
        public void FixtureTearDown()
        {
            _provider.Dispose();
        }

        [SetUp]
        public void SetUp()
        {
            TestDb.ResetBlogs(TestDataSelection.Small);
        }

        [Test]
        public void Check01ServiceLayerInitialiseSeedsMediumWhenEmpty()
        {
            //SETUP
            using (var db = TestDb.CreateDb())
            {
                db.Posts.RemoveRange(db.Posts);
                db.Tags.RemoveRange(db.Tags);
                db.Blogs.RemoveRange(db.Blogs);
                db.SaveChanges();
            }

            //ATTEMPT
            ServiceLayerInitialise.InitialiseThis(_provider, true);

            //VERIFY
            using (var db = TestDb.CreateDb())
            {
                db.Blogs.Count().ShouldEqual(4);
                db.Posts.Count().ShouldEqual(17);
                db.Tags.Count().ShouldEqual(8);
            }
        }

        [Test]
        public void Check05ReadTagListDto()
        {
            using (var scope = _provider.CreateScope())
            {
                //SETUP
                var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();

                //ATTEMPT
                var tags = service.ReadManyNoTracked<TagListDto>().OrderBy(x => x.TagId).ToList();

                //VERIFY
                tags.Count.ShouldEqual(3);
                tags.Single(x => x.Slug == "ugly").PostsCount.ShouldEqual(2);
            }
        }

        [Test]
        public void Check10CreateTagDirectOk()
        {
            using (var scope = _provider.CreateScope())
            {
                //SETUP
                var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var snap = new DbSnapShot(db);

                //ATTEMPT
                service.CreateAndSave(new Tag { Name = "New tag", Slug = "newtag" });

                //VERIFY
                service.IsValid.ShouldEqual(true, service.GetAllErrors());
                snap.CheckSnapShot(db, tagsChange: 1);
            }
        }

        [Test]
        public void Check11CreateTagDirectBadSlugAttribute()
        {
            using (var scope = _provider.CreateScope())
            {
                //SETUP
                var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();

                //ATTEMPT
                service.CreateAndSave(new Tag { Name = "New tag", Slug = "bad slug" });

                //VERIFY
                service.IsValid.ShouldEqual(false);
                service.Errors.Single().ErrorResult.ErrorMessage.ShouldEqual("The slug must not contain spaces or non-alphanumeric characters.");
            }
        }

        [Test]
        public void Check12CreateTagDuplicateSlugGivesErrorOnSlug()
        {
            using (var scope = _provider.CreateScope())
            {
                //SETUP
                var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var snap = new DbSnapShot(db);

                //ATTEMPT
                service.CreateAndSave(new Tag { Name = "Duplicate", Slug = "ugly" });

                //VERIFY
                service.IsValid.ShouldEqual(false);
                var error = service.Errors.Single().ErrorResult;
                error.ErrorMessage.ShouldEqual("The Slug on tag 'Duplicate' must be unique and is already being used.");
                error.MemberNames.ShouldEqual(new[] { "Slug" });
                db.ChangeTracker.Clear();
                snap.CheckSnapShot(db);
            }
        }

        [Test]
        public async Task Check13UpdateTagDuplicateSlugAsyncGivesErrorOnSlug()
        {
            using (var scope = _provider.CreateScope())
            {
                //SETUP
                var service = scope.ServiceProvider.GetRequiredService<ICrudServicesAsync>();
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var tag = db.Tags.Single(x => x.Slug == "bad");

                //ATTEMPT
                tag.Slug = "ugly";
                await service.UpdateAndSaveAsync(tag);

                //VERIFY
                service.IsValid.ShouldEqual(false);
                service.Errors.Single().ErrorResult.MemberNames.ShouldEqual(new[] { "Slug" });
            }
        }

        [Test]
        public void Check20PostServiceUpdateChangesLastUpdated()
        {
            int postId;
            DateTime originalLastUpdated;
            using (var db = TestDb.CreateDb())
            {
                var post = db.Posts.AsNoTracking().OrderBy(x => x.PostId).First();
                postId = post.PostId;
                originalLastUpdated = post.LastUpdated;
            }
            Thread.Sleep(400);

            using (var scope = _provider.CreateScope())
            {
                //SETUP
                var service = scope.ServiceProvider.GetRequiredService<IPostService>();
                var dto = service.GetForEdit(postId);
                dto.Title = "Updated title";
                dto.UserChosenTags.FinalSelection = dto.UserChosenTags.InitialSelection.Select(x => x.Value.ToString()).ToArray();

                //ATTEMPT
                var status = service.Update(dto);

                //VERIFY
                status.IsValid.ShouldEqual(true, status.GetAllErrors());
            }
            using (var db = TestDb.CreateDb())
            {
                var post = db.Posts.Single(x => x.PostId == postId);
                post.Title.ShouldEqual("Updated title");
                Assert.That(post.LastUpdated.Subtract(originalLastUpdated).TotalMilliseconds, Is.GreaterThanOrEqualTo(400));
            }
        }

        [Test]
        public async Task Check21PostServiceAsyncCreateOk()
        {
            using (var scope = _provider.CreateScope())
            {
                //SETUP
                var service = scope.ServiceProvider.GetRequiredService<IPostServiceAsync>();
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var snap = new DbSnapShot(db);
                var dto = await service.GetNewForCreateAsync();
                dto.Title = "New async post";
                dto.Content = "Some content";
                dto.Bloggers.SelectedValue = dto.Bloggers.KeyValueList.Last().Value;
                dto.UserChosenTags.FinalSelection = dto.UserChosenTags.AllPossibleOptions.Take(2).Select(x => x.Value.ToString()).ToArray();

                //ATTEMPT
                var status = await service.CreateAsync(dto);

                //VERIFY
                status.IsValid.ShouldEqual(true, status.GetAllErrors());
                snap.CheckSnapShot(db, 1, 2);
            }
        }

        [Test]
        public void Check22PostServiceCreateNoTagsError()
        {
            using (var scope = _provider.CreateScope())
            {
                //SETUP
                var service = scope.ServiceProvider.GetRequiredService<IPostService>();
                var dto = service.GetNewForCreate();
                dto.Title = "New post";
                dto.Content = "Some content";
                dto.Bloggers.SelectedValue = dto.Bloggers.KeyValueList.Last().Value;

                //ATTEMPT
                var status = service.Create(dto);

                //VERIFY
                status.IsValid.ShouldEqual(false);
                status.Errors.Single().ErrorResult.ErrorMessage.ShouldEqual(PostServiceHelpers.NoTagsSelected);
            }
        }
    }
}
