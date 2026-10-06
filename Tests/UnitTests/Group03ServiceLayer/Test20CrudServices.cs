using System.Linq;
using System.Threading.Tasks;
using DataLayer.DataClasses.Concrete;
using DataLayer.Startup;
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
    public class Test20CrudServices
    {
        private ServiceProvider _provider;
        private ServiceProvider _testDtoProvider;

        [OneTimeSetUp]
        public void FixtureSetUp()
        {
            TestDbHelper.MigrateAndResetBlogs(TestDataSelection.Small);
            _provider = TestDbHelper.CreateServiceLayerProvider();
            _testDtoProvider = TestDbHelper.CreateProviderWithTestDtos();
        }

        [OneTimeTearDown]
        public void FixtureTearDown()
        {
            _provider.Dispose();
            _testDtoProvider.Dispose();
        }

        [SetUp]
        public void SetUp()
        {
            TestDbHelper.MigrateAndResetBlogs(TestDataSelection.Small);
        }

        [Test]
        public void Check01ReadManyTagListDtoPostsCount()
        {
            using var scope = _provider.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();

            var tags = service.ReadManyNoTracked<TagListDto>().ToList();

            Assert.That(service.IsValid, Is.True, service.GetAllErrors());
            Assert.That(tags.Count, Is.EqualTo(3));
            Assert.That(tags.Single(x => x.Slug == "good").PostsCount, Is.EqualTo(1));
            Assert.That(tags.Single(x => x.Slug == "bad").PostsCount, Is.EqualTo(2));
            Assert.That(tags.Single(x => x.Slug == "ugly").PostsCount, Is.EqualTo(2));
        }

        [Test]
        public async Task Check02ReadManyTagListDtoAsyncPostsCount()
        {
            using var scope = _provider.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<ICrudServicesAsync>();

            var tags = await service.ReadManyNoTracked<TagListDto>().ToListAsync();

            Assert.That(tags.Sum(x => x.PostsCount), Is.EqualTo(5));
        }

        [Test]
        public void Check03ReadManyBlogListDtoPostsCount()
        {
            using var scope = _provider.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();

            var blogs = service.ReadManyNoTracked<BlogListDto>().ToList();

            Assert.That(blogs.Count, Is.EqualTo(2));
            Assert.That(blogs.Single(x => x.Name == "Jon Smith").PostsCount, Is.EqualTo(2));
            Assert.That(blogs.Single(x => x.Name == "Fred Bloggs").PostsCount, Is.EqualTo(1));
        }

        [Test]
        public void Check04ReadManySimplePostDtoFilterByBlogId()
        {
            int jonsBlogId;
            using (var db = TestDbHelper.CreateDb())
                jonsBlogId = db.Blogs.Single(x => x.Name == "Jon Smith").BlogId;
            using var scope = _provider.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();

            var allPosts = service.ReadManyNoTracked<SimplePostDto>().FilterByBlogId(null).ToList();
            var jonsPosts = service.ReadManyNoTracked<SimplePostDto>().FilterByBlogId(jonsBlogId).ToList();

            Assert.That(allPosts.Count, Is.EqualTo(3));
            Assert.That(jonsPosts.Count, Is.EqualTo(2));
            Assert.That(jonsPosts.All(x => x.BloggerName == "Jon Smith"), Is.True);
            Assert.That(jonsPosts.Single(x => x.Title == "First great post").TagNames,
                Does.Contain("Good post").And.Contain("Ugly post"));
        }

        [Test]
        public void Check10CreateTagOk()
        {
            DbSnapShot snap;
            using (var db = TestDbHelper.CreateDb())
                snap = new DbSnapShot(db);
            using var scope = _provider.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();

            service.CreateAndSave(new Tag { Name = "New tag", Slug = "newtag" });

            Assert.That(service.IsValid, Is.True, service.GetAllErrors());
            using (var db = TestDbHelper.CreateDb())
                snap.CheckSnapShot(db, tagsChange: 1);
        }

        [Test]
        public void Check11CreateTagDuplicateSlugBad()
        {
            DbSnapShot snap;
            using (var db = TestDbHelper.CreateDb())
                snap = new DbSnapShot(db);
            using var scope = _provider.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();

            service.CreateAndSave(new Tag { Name = "Duplicate", Slug = "ugly" });

            CheckDuplicateSlugError(service, "Duplicate");
            using (var db = TestDbHelper.CreateDb())
                snap.CheckSnapShot(db);
        }

        [Test]
        public void Check12CreateTagViaDtoDuplicateSlugBad()
        {
            using var scope = _testDtoProvider.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();

            service.CreateAndSave(new SimpleTagDto { Name = "Duplicate", Slug = "good" });

            CheckDuplicateSlugError(service, "Duplicate");
        }

        [Test]
        public void Check13UpdateTagDuplicateSlugBad()
        {
            int goodTagId;
            using (var db = TestDbHelper.CreateDb())
                goodTagId = db.Tags.Single(x => x.Slug == "good").TagId;
            using var scope = _testDtoProvider.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();

            service.UpdateAndSave(new SimpleTagDto { TagId = goodTagId, Name = "Good post", Slug = "bad" });

            CheckDuplicateSlugError(service, "Good post");
            using (var db = TestDbHelper.CreateDb())
                Assert.That(db.Tags.Find(goodTagId).Slug, Is.EqualTo("good"));
        }

        [Test]
        public async Task Check14CreateTagAsyncDuplicateSlugBad()
        {
            using var scope = _testDtoProvider.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<ICrudServicesAsync>();

            await service.CreateAndSaveAsync(new SimpleTagDtoAsync { Name = "Duplicate", Slug = "bad" });

            CheckDuplicateSlugError(service, "Duplicate");
        }

        [Test]
        public void Check20DeletePostOk()
        {
            DbSnapShot snap;
            int postId;
            using (var db = TestDbHelper.CreateDb())
            {
                snap = new DbSnapShot(db);
                postId = db.Posts.Single(x => x.Title == "First great post").PostId;
            }
            using var scope = _provider.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();

            service.DeleteAndSave<Post>(postId);

            Assert.That(service.IsValid, Is.True, service.GetAllErrors());
            using (var db = TestDbHelper.CreateDb())
            {
                snap.CheckSnapShot(db, postsChange: -1, postTagLinkChange: -2);
                Assert.That(db.Tags.Count(), Is.EqualTo(3));
            }
        }

        [Test]
        public void Check21DeletePostMissingBad()
        {
            using var scope = _provider.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<ICrudServices>();

            service.DeleteAndSave<Post>(int.MaxValue);

            Assert.That(service.IsValid, Is.False);
        }

        [Test]
        public async Task Check22DeletePostAsyncOk()
        {
            DbSnapShot snap;
            int postId;
            using (var db = TestDbHelper.CreateDb())
            {
                snap = new DbSnapShot(db);
                postId = db.Posts.Single(x => x.Title == "Freds good post").PostId;
            }
            using var scope = _provider.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<ICrudServicesAsync>();

            await service.DeleteAndSaveAsync<Post>(postId);

            Assert.That(service.IsValid, Is.True, service.GetAllErrors());
            using (var db = TestDbHelper.CreateDb())
                snap.CheckSnapShot(db, postsChange: -1, postTagLinkChange: -2);
        }

        private static void CheckDuplicateSlugError(StatusGeneric.IStatusGeneric status, string tagName)
        {
            Assert.That(status.IsValid, Is.False);
            var error = status.Errors.Single().ErrorResult;
            Assert.That(error.MemberNames, Is.EquivalentTo(new[] { "Slug" }));
            Assert.That(error.ErrorMessage,
                Is.EqualTo($"The Slug on tag '{tagName}' must be unique and is already being used."));
        }
    }
}
