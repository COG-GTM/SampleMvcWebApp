using System.Linq;
using System.Threading.Tasks;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using DataLayer.Startup;
using GenericServices;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using NUnit.Framework;
using NUnit.Framework.Legacy;
using ServiceLayer.BlogServices;
using ServiceLayer.PostServices;
using ServiceLayer.Startup;
using ServiceLayer.TagServices;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    [TestFixture]
    public class Test11ServiceLayerDi
    {
        private ServiceProvider _provider;

        [OneTimeSetUp]
        public void FixtureSetUp()
        {
            using (var db = TestDb.Create())
                DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);

            var services = new ServiceCollection();
            services.AddServiceLayer(TestDb.ConnectionString);
            _provider = services.BuildServiceProvider();
        }

        [OneTimeTearDown]
        public void FixtureTearDown()
        {
            _provider.Dispose();
        }

        [Test]
        public void Test01DbContextIsScoped()
        {
            using var scope = _provider.CreateScope();
            var db1 = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
            var db2 = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
            ClassicAssert.AreSame(db1, db2);
            using var scope2 = _provider.CreateScope();
            ClassicAssert.AreNotSame(db1, scope2.ServiceProvider.GetRequiredService<SampleWebAppDb>());
        }

        [Test]
        public void Test02CrudServicesResolve()
        {
            using var scope = _provider.CreateScope();
            ClassicAssert.NotNull(scope.ServiceProvider.GetRequiredService<ICrudServices>());
            ClassicAssert.NotNull(scope.ServiceProvider.GetRequiredService<ICrudServicesAsync>());
        }

        [Test]
        public void Test10ListDtos()
        {
            using var scope = _provider.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();
            var blogs = service.ReadManyNoTracked<BlogListDto>().ToList();
            blogs.Count.ShouldEqual(2);
            blogs.Sum(x => x.PostsCount).ShouldEqual(3);
            service.ReadManyNoTracked<TagListDto>().Count().ShouldEqual(3);
            var posts = service.ReadManyNoTracked<SimplePostDto>().ToList();
            posts.Count.ShouldEqual(3);
            posts.All(x => !string.IsNullOrEmpty(x.BloggerName) && x.TagNames.Length > 0).ShouldEqual(true);
        }

        [Test]
        public void Test20CreateUpdateDeletePostViaDto()
        {
            int postId;
            System.DateTime lastUpdatedBeforeEdit;
            using (var scope = _provider.CreateScope())
            {
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();
                var dto = new DetailPostDto { Title = "New post", Content = "Some content" }.SetupSecondaryData(db);
                dto.Bloggers.SelectedValue = db.Blogs.First().BlogId.ToString();
                dto.UserChosenTags.FinalSelection = db.Tags.Take(2).Select(x => x.TagId.ToString()).ToArray();
                dto.SetupRestOfDto(db).IsValid.ShouldEqual(true);
                service.CreateAndSave(dto);
                service.IsValid.ShouldEqual(true, service.GetAllErrors());
                postId = db.Posts.Single(x => x.Title == "New post").PostId;
            }
            using (var scope = _provider.CreateScope())
            {
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();
                var dto = service.ReadSingle<DetailPostDto>(postId).SetupSecondaryData(db);
                lastUpdatedBeforeEdit = dto.LastUpdated;
                dto.Tags.Count.ShouldEqual(2);
                dto.Title = "Updated post";
                dto.Bloggers.SelectedValue = dto.BlogId.ToString();
                dto.UserChosenTags.FinalSelection = new[] { db.Tags.OrderBy(x => x.TagId).Last().TagId.ToString() };
                dto.SetupRestOfDto(db).IsValid.ShouldEqual(true);
                service.UpdateAndSave(dto);
                service.IsValid.ShouldEqual(true, service.GetAllErrors());
            }
            using (var scope = _provider.CreateScope())
            {
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var post = db.Posts.Include(x => x.Tags).Single(x => x.PostId == postId);
                post.Title.ShouldEqual("Updated post");
                post.Tags.Count.ShouldEqual(1);
                (post.LastUpdated > lastUpdatedBeforeEdit).ShouldEqual(true);
                var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();
                service.DeleteAndSave<Post>(postId);
                service.IsValid.ShouldEqual(true, service.GetAllErrors());
                db.Posts.Count().ShouldEqual(3);
            }
        }

        [Test]
        public async Task Test21CreateTagWithDuplicateSlugFailsAsync()
        {
            using var scope = _provider.CreateScope();
            var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
            var service = scope.ServiceProvider.GetRequiredService<ICrudServicesAsync>();
            var existing = await db.Tags.AsNoTracking().FirstAsync();
            await service.CreateAndSaveAsync(new Tag { Name = "dup", Slug = existing.Slug });
            service.IsValid.ShouldEqual(false);
            service.GetAllErrors().ShouldContain("must be unique");
        }
    }
}
