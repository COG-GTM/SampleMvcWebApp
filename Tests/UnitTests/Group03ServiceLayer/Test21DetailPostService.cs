using System.Linq;
using System.Threading.Tasks;
using DataLayer.Startup;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using NUnit.Framework;
using ServiceLayer.PostServices;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    [TestFixture]
    public class Test21DetailPostService
    {
        private ServiceProvider _provider;
        private int _jonsBlogId;
        private int _firstPostId;
        private int _goodTagId;
        private int _badTagId;
        private int _uglyTagId;

        [OneTimeSetUp]
        public void FixtureSetUp()
        {
            _provider = TestDbHelper.CreateServiceLayerProvider();
        }

        [OneTimeTearDown]
        public void FixtureTearDown()
        {
            _provider.Dispose();
        }

        [SetUp]
        public void SetUp()
        {
            TestDbHelper.MigrateAndResetBlogs(TestDataSelection.Small);
            using var db = TestDbHelper.CreateDb();
            _jonsBlogId = db.Blogs.Single(x => x.Name == "Jon Smith").BlogId;
            _firstPostId = db.Posts.Single(x => x.Title == "First great post").PostId;
            _goodTagId = db.Tags.Single(x => x.Slug == "good").TagId;
            _badTagId = db.Tags.Single(x => x.Slug == "bad").TagId;
            _uglyTagId = db.Tags.Single(x => x.Slug == "ugly").TagId;
        }

        [Test]
        public void Check01GetNewSetsUpLists()
        {
            var dto = Use<IDetailPostService, DetailPostDto>(s => s.GetNew());

            Assert.That(dto.Bloggers.KeyValueList.Count, Is.EqualTo(3));
            Assert.That(dto.Bloggers.SelectedValue, Is.Null);
            Assert.That(dto.UserChosenTags.AllPossibleOptions.Count, Is.EqualTo(3));
            Assert.That(dto.UserChosenTags.InitialSelection, Is.Empty);
        }

        [Test]
        public void Check02GetForEditSetsUpSelections()
        {
            var dto = Use<IDetailPostService, DetailPostDto>(s => s.GetForEdit(_firstPostId));

            Assert.That(dto.PostId, Is.EqualTo(_firstPostId));
            Assert.That(dto.Bloggers.SelectedValue, Is.EqualTo(_jonsBlogId.ToString("D")));
            Assert.That(dto.UserChosenTags.InitialSelection.Select(x => x.Value),
                Is.EquivalentTo(new[] { _goodTagId, _uglyTagId }));
        }

        [Test]
        public void Check03GetDetailMissingBad()
        {
            using var scope = _provider.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<IDetailPostService>();

            var dto = service.GetDetail(int.MaxValue);

            Assert.That(dto, Is.Null);
            Assert.That(service.Status.IsValid, Is.False);
        }

        [Test]
        public void Check10CreateWithTagsOk()
        {
            DbSnapShot snap;
            using (var db = TestDbHelper.CreateDb())
                snap = new DbSnapShot(db);
            var dto = Use<IDetailPostService, DetailPostDto>(s => s.GetNew());
            dto.Title = "New post";
            dto.Content = "Some content";
            dto.Bloggers.SelectedValue = _jonsBlogId.ToString("D");
            dto.UserChosenTags.FinalSelection = new[] { _goodTagId.ToString("D"), _badTagId.ToString("D") };

            var status = Use<IDetailPostService, StatusGeneric.IStatusGeneric>(s => s.Create(dto));

            Assert.That(status.IsValid, Is.True, status.GetAllErrors());
            using (var db = TestDbHelper.CreateDb())
            {
                snap.CheckSnapShot(db, postsChange: 1, postTagLinkChange: 2);
                var post = db.Posts.Include(x => x.Tags).Single(x => x.Title == "New post");
                Assert.That(post.BlogId, Is.EqualTo(_jonsBlogId));
                Assert.That(post.Tags.Select(x => x.TagId), Is.EquivalentTo(new[] { _goodTagId, _badTagId }));
            }
        }

        [Test]
        public void Check11CreateNoBloggerBad()
        {
            var dto = Use<IDetailPostService, DetailPostDto>(s => s.GetNew());
            dto.Title = "New post";
            dto.Content = "Some content";
            dto.UserChosenTags.FinalSelection = new[] { _goodTagId.ToString("D") };

            var status = Use<IDetailPostService, StatusGeneric.IStatusGeneric>(s => s.Create(dto));

            Assert.That(status.IsValid, Is.False);
            var error = status.Errors.Single().ErrorResult;
            Assert.That(error.MemberNames, Is.EquivalentTo(new[] { "Bloggers" }));
            Assert.That(error.ErrorMessage,
                Is.EqualTo("The blogger was not selected. You must do that before the post can be saved."));
        }

        [Test]
        public void Check12CreateNoTagsBad()
        {
            var dto = Use<IDetailPostService, DetailPostDto>(s => s.GetNew());
            dto.Title = "New post";
            dto.Content = "Some content";
            dto.Bloggers.SelectedValue = _jonsBlogId.ToString("D");
            dto.UserChosenTags.FinalSelection = null;

            var status = Use<IDetailPostService, StatusGeneric.IStatusGeneric>(s => s.Create(dto));

            Assert.That(status.IsValid, Is.False);
            var error = status.Errors.Single().ErrorResult;
            Assert.That(error.MemberNames, Is.EquivalentTo(new[] { "UserChosenTags" }));
            Assert.That(error.ErrorMessage, Is.EqualTo("You must select at least one tag for the post."));
        }

        [Test]
        public void Check20UpdateChangeTagsOk()
        {
            DbSnapShot snap;
            using (var db = TestDbHelper.CreateDb())
                snap = new DbSnapShot(db);
            var dto = Use<IDetailPostService, DetailPostDto>(s => s.GetForEdit(_firstPostId));
            dto.UserChosenTags.FinalSelection = new[] { _badTagId.ToString("D") };

            var status = Use<IDetailPostService, StatusGeneric.IStatusGeneric>(s => s.Update(dto));

            Assert.That(status.IsValid, Is.True, status.GetAllErrors());
            using (var db = TestDbHelper.CreateDb())
            {
                snap.CheckSnapShot(db, postTagLinkChange: -1);
                var post = db.Posts.Include(x => x.Tags).Single(x => x.PostId == _firstPostId);
                Assert.That(post.Tags.Select(x => x.TagId), Is.EquivalentTo(new[] { _badTagId }));
            }
        }

        [Test]
        public void Check21UpdateTitleBad()
        {
            var dto = Use<IDetailPostService, DetailPostDto>(s => s.GetForEdit(_firstPostId));
            dto.Title = "Too excited!";

            var status = Use<IDetailPostService, StatusGeneric.IStatusGeneric>(s => s.Update(dto));

            Assert.That(status.IsValid, Is.False);
            var error = status.Errors.Single().ErrorResult;
            Assert.That(error.MemberNames, Is.EquivalentTo(new[] { "Title" }));
            Assert.That(error.ErrorMessage,
                Is.EqualTo("Sorry, but you can't get too excited and include a ! in the title."));
            using (var db = TestDbHelper.CreateDb())
                Assert.That(db.Posts.Find(_firstPostId).Title, Is.EqualTo("First great post"));
        }

        [Test]
        public async Task Check30CreateAsyncWithTagsOk()
        {
            DetailPostDtoAsync dto;
            using (var scope = _provider.CreateScope())
                dto = await scope.ServiceProvider.GetRequiredService<IDetailPostServiceAsync>().GetNewAsync();
            dto.Title = "New async post";
            dto.Content = "Some content";
            dto.Bloggers.SelectedValue = _jonsBlogId.ToString("D");
            dto.UserChosenTags.FinalSelection = new[] { _uglyTagId.ToString("D") };

            StatusGeneric.IStatusGeneric status;
            using (var scope = _provider.CreateScope())
                status = await scope.ServiceProvider.GetRequiredService<IDetailPostServiceAsync>().CreateAsync(dto);

            Assert.That(status.IsValid, Is.True, status.GetAllErrors());
            using (var db = TestDbHelper.CreateDb())
            {
                var post = db.Posts.Include(x => x.Tags).Single(x => x.Title == "New async post");
                Assert.That(post.Tags.Select(x => x.TagId), Is.EquivalentTo(new[] { _uglyTagId }));
            }
        }

        [Test]
        public async Task Check31UpdateAsyncChangeTagsOk()
        {
            DetailPostDtoAsync dto;
            using (var scope = _provider.CreateScope())
                dto = await scope.ServiceProvider.GetRequiredService<IDetailPostServiceAsync>().GetForEditAsync(_firstPostId);
            dto.UserChosenTags.FinalSelection = new[] { _goodTagId.ToString("D"), _badTagId.ToString("D"), _uglyTagId.ToString("D") };

            StatusGeneric.IStatusGeneric status;
            using (var scope = _provider.CreateScope())
                status = await scope.ServiceProvider.GetRequiredService<IDetailPostServiceAsync>().UpdateAsync(dto);

            Assert.That(status.IsValid, Is.True, status.GetAllErrors());
            using (var db = TestDbHelper.CreateDb())
            {
                var post = db.Posts.Include(x => x.Tags).Single(x => x.PostId == _firstPostId);
                Assert.That(post.Tags.Select(x => x.TagId), Is.EquivalentTo(new[] { _goodTagId, _badTagId, _uglyTagId }));
            }
        }

        private TResult Use<TService, TResult>(Func<TService, TResult> action)
        {
            using var scope = _provider.CreateScope();
            return action(scope.ServiceProvider.GetRequiredService<TService>());
        }
    }
}
