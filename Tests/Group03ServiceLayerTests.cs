using System.Linq;
using DataLayer.DataClasses.Concrete;
using DataLayer.Startup;
using GenericLibsBase.Core;
using GenericServices;
using ServiceLayer.BlogServices;
using ServiceLayer.PostServices;
using ServiceLayer.TagServices;
using Xunit;

namespace Tests
{
    public class Group03ServiceLayerTests
    {
        [Fact]
        public void ListServiceProjectsTagsWithPostCount()
        {
            using var db = TestHelpers.CreateInMemoryDb();
            DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);
            var service = new ListService(db, TestHelpers.CreateMapper());

            var tags = service.GetAll<TagListDto>().ToList();

            Assert.Equal(3, tags.Count);
            Assert.True(tags.Sum(x => x.PostsCount) > 0);
        }

        [Fact]
        public void ListServiceProjectsBlogsWithPostCount()
        {
            using var db = TestHelpers.CreateInMemoryDb();
            DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);
            var service = new ListService(db, TestHelpers.CreateMapper());

            var blogs = service.GetAll<BlogListDto>().ToList();

            Assert.Equal(2, blogs.Count);
            Assert.Equal(3, blogs.Sum(x => x.PostsCount));
        }

        [Fact]
        public void CreateServiceAddsTag()
        {
            using var db = TestHelpers.CreateInMemoryDb();
            DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);
            var service = new CreateService(db, TestHelpers.CreateMapper());

            var status = service.Create(new Tag { Name = "C# Tag", Slug = "csharptag" });

            Assert.True(status.IsValid, string.Join(",", status.Errors));
            Assert.Equal(4, db.Tags.Count());
        }

        [Fact]
        public void CreateServiceRejectsDuplicateSlug()
        {
            using var db = TestHelpers.CreateInMemoryDb();
            DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);
            var existingSlug = db.Tags.First().Slug;
            var service = new CreateService(db, TestHelpers.CreateMapper());

            var status = service.Create(new Tag { Name = "Dupe", Slug = existingSlug });

            Assert.False(status.IsValid);
        }

        [Fact]
        public void UpdateServiceModifiesTag()
        {
            using var db = TestHelpers.CreateInMemoryDb();
            DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);
            var tag = db.Tags.First();
            var service = new UpdateService(db, TestHelpers.CreateMapper());

            tag.Name = "Renamed Tag";
            var status = service.Update(tag);

            Assert.True(status.IsValid, string.Join(",", status.Errors));
            Assert.Equal("Renamed Tag", db.Tags.First(x => x.TagId == tag.TagId).Name);
        }

        [Fact]
        public void DeleteServiceRemovesTag()
        {
            using var db = TestHelpers.CreateInMemoryDb();
            DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);
            //pick a tag that is not referenced by any post so the delete is not blocked
            var tag = new Tag { Name = "Orphan", Slug = "orphan" };
            db.Tags.Add(tag);
            db.SaveChanges();
            var before = db.Tags.Count();
            var service = new DeleteService(db);

            var status = service.Delete<Tag>(tag.TagId);

            Assert.True(status.IsValid, string.Join(",", status.Errors));
            Assert.Equal(before - 1, db.Tags.Count());
        }

        [Fact]
        public void ListServiceProjectsPosts()
        {
            using var db = TestHelpers.CreateInMemoryDb();
            DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);
            var service = new ListService(db, TestHelpers.CreateMapper());

            var posts = service.GetAll<SimplePostDto>().ToList();

            Assert.Equal(3, posts.Count);
            Assert.All(posts, p => Assert.False(string.IsNullOrEmpty(p.Title)));
        }

        [Fact]
        public void TagNamesGetterIsNullSafeOnPostedBackDto()
        {
            //ASP.NET Core's validation visitor invokes computed getters during
            //model binding on the posted-back DTO, where Tags is null.
            var dto = new DetailPostDto();
            Assert.Null(dto.Tags);
            Assert.Equal(string.Empty, dto.TagNames);

            var simpleDto = new SimplePostDto();
            Assert.Equal(string.Empty, simpleDto.TagNames);
        }

        [Fact]
        public void ErrorsAsHtmlEncodesMessageButKeepsLineBreaks()
        {
            //ErrorsAsHtml output is rendered with @Html.Raw, so message text
            //(which can contain user input like a tag name) must be HTML-encoded
            //to prevent stored XSS, while the <br/> separators stay as markup.
            var status = new SuccessOrErrors();
            status.AddSingleError("Bad tag '{0}' rejected.", "<script>alert('xss')</script>");
            status.AddSingleError("Second error.");

            var html = status.ErrorsAsHtml();

            Assert.DoesNotContain("<script>", html);
            Assert.Contains("&lt;script&gt;", html);
            Assert.Contains("<br/>", html);
        }

        [Fact]
        public void DetailServiceReturnsPost()
        {
            using var db = TestHelpers.CreateInMemoryDb();
            DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);
            var postId = db.Posts.First().PostId;
            var service = new DetailService(db, TestHelpers.CreateMapper());

            var status = service.GetDetail<DetailPostDto>(postId);

            Assert.True(status.IsValid, string.Join(",", status.Errors));
            Assert.Equal(postId, status.Result.PostId);
        }
    }
}
