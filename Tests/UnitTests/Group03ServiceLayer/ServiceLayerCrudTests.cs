using System.Linq;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using GenericServices;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using NUnit.Framework;
using ServiceLayer.BlogServices;
using ServiceLayer.PostServices;
using ServiceLayer.TagServices;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    [TestFixture]
    public class ServiceLayerCrudTests
    {
        [Test]
        public void BlogListDto_FlattensPostsCount()
        {
            using var app = new TestServices();
            using var scope = app.CreateScope();
            var list = scope.ServiceProvider.GetRequiredService<IListService>();

            var blogs = list.GetAll<BlogListDto>().ToList();

            Assert.That(blogs.Count, Is.EqualTo(2));
            Assert.That(blogs.Sum(b => b.PostsCount), Is.EqualTo(3));
        }

        [Test]
        public void TagListDto_FlattensPostsCount()
        {
            using var app = new TestServices();
            using var scope = app.CreateScope();
            var list = scope.ServiceProvider.GetRequiredService<IListService>();

            var tags = list.GetAll<TagListDto>().ToList();

            Assert.That(tags.Count, Is.EqualTo(3));
            Assert.That(tags.All(t => !string.IsNullOrEmpty(t.Slug)));
        }

        [Test]
        public void SimplePostDto_FlattensBloggerNameAndProjectsTags()
        {
            using var app = new TestServices();
            using var scope = app.CreateScope();
            var list = scope.ServiceProvider.GetRequiredService<IListService>();

            var posts = list.GetAll<SimplePostDto>().ToList();

            Assert.That(posts.Count, Is.EqualTo(3));
            Assert.That(posts.All(p => !string.IsNullOrEmpty(p.BloggerName)));
            Assert.That(posts.All(p => p.Tags != null));
        }

        [Test]
        public void DetailSetup_PopulatesBloggerAndTagPickers()
        {
            using var app = new TestServices();
            using var scope = app.CreateScope();
            var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
            var setup = scope.ServiceProvider.GetRequiredService<IUpdateSetupService>();

            var firstId = db.Posts.OrderBy(p => p.PostId).First().PostId;
            var dto = setup.GetOriginal<DetailPostDto>(firstId).Result;

            Assert.That(dto.Bloggers.KeyValueList, Is.Not.Empty);
            Assert.That(dto.UserChosenTags.AllPossibleOptions, Is.Not.Empty);
            Assert.That(dto.UserChosenTags.FinalSelection, Is.Not.Empty);
        }

        [Test]
        public void UpdatePost_ChangesTitleAndReplacesTags()
        {
            using var app = new TestServices();
            int postId;
            using (var scope = app.CreateScope())
            {
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var setupSvc = scope.ServiceProvider.GetRequiredService<IUpdateSetupService>();
                var updateSvc = scope.ServiceProvider.GetRequiredService<IUpdateService>();

                postId = db.Posts.OrderBy(p => p.PostId).First().PostId;
                var dto = setupSvc.GetOriginal<DetailPostDto>(postId).Result;
                dto.Title = "UPDATED TITLE";
                dto.Bloggers.SelectedValue = dto.BlogId.ToString();
                dto.UserChosenTags.FinalSelection =
                    new[] { dto.UserChosenTags.AllPossibleOptions.First().Value.ToString() };

                var response = updateSvc.Update(dto);
                Assert.That(response.IsValid, Is.True, string.Join(";", response.Errors.Select(e => e.ErrorMessage)));
                Assert.That(response.SuccessMessage, Does.Contain("updated"));
            }

            using (var scope = app.CreateScope())
            {
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var post = db.Posts.Include(p => p.Tags).First(p => p.PostId == postId);
                Assert.That(post.Title, Is.EqualTo("UPDATED TITLE"));
                Assert.That(post.Tags.Count, Is.EqualTo(1));
            }
        }

        [Test]
        public void CreatePost_WithSelectedTags_Succeeds()
        {
            using var app = new TestServices();
            using (var scope = app.CreateScope())
            {
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var createSetup = scope.ServiceProvider.GetRequiredService<ICreateSetupService>();
                var createSvc = scope.ServiceProvider.GetRequiredService<ICreateService>();

                var dto = createSetup.GetDto<DetailPostDto>();
                dto.Title = "Brand New Post";
                dto.Content = "Some content here.";
                dto.Bloggers.SelectedValue = db.Blogs.First().BlogId.ToString();
                dto.UserChosenTags.FinalSelection = db.Tags.Take(2).Select(t => t.TagId.ToString()).ToArray();

                var response = createSvc.Create(dto);
                Assert.That(response.IsValid, Is.True, string.Join(";", response.Errors.Select(e => e.ErrorMessage)));
                Assert.That(response.SuccessMessage, Does.Contain("created"));
            }

            using (var scope = app.CreateScope())
            {
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var created = db.Posts.Include(p => p.Tags).FirstOrDefault(p => p.Title == "Brand New Post");
                Assert.That(created, Is.Not.Null);
                Assert.That(created.Tags.Count, Is.EqualTo(2));
            }
        }

        [Test]
        public void DeletePost_RemovesIt()
        {
            using var app = new TestServices();
            using var scope = app.CreateScope();
            var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
            var deleteSvc = scope.ServiceProvider.GetRequiredService<IDeleteService>();

            var postId = db.Posts.OrderBy(p => p.PostId).First().PostId;
            var response = deleteSvc.Delete<Post>(postId);

            Assert.That(response.IsValid, Is.True, string.Join(";", response.Errors.Select(e => e.ErrorMessage)));
            Assert.That(db.Posts.Any(p => p.PostId == postId), Is.False);
        }
    }
}
