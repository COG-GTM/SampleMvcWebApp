using System.Linq;
using System.Threading.Tasks;
using DataLayer.DataClasses;
using GenericServices;
using Microsoft.Extensions.DependencyInjection;
using NUnit.Framework;
using ServiceLayer.PostServices;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    [TestFixture]
    public class ServiceLayerAsyncTests
    {
        [Test]
        public async Task DetailAsync_ReturnsPost()
        {
            using var app = new TestServices();
            using var scope = app.CreateScope();
            var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
            var detail = scope.ServiceProvider.GetRequiredService<IDetailServiceAsync>();

            var firstId = db.Posts.OrderBy(p => p.PostId).First().PostId;
            var response = await detail.GetDetailAsync<DetailPostDtoAsync>(firstId);

            Assert.That(response.IsValid, Is.True, string.Join(";", response.Errors.Select(e => e.ErrorMessage)));
            Assert.That(response.Result.PostId, Is.EqualTo(firstId));
        }

        [Test]
        public async Task CreateAsync_WithSelectedTags_Succeeds()
        {
            using var app = new TestServices();
            int createdId;
            using (var scope = app.CreateScope())
            {
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                var createSetup = scope.ServiceProvider.GetRequiredService<ICreateSetupServiceAsync>();
                var createSvc = scope.ServiceProvider.GetRequiredService<ICreateServiceAsync>();

                var dto = await createSetup.GetDtoAsync<DetailPostDtoAsync>();
                dto.Title = "Async New Post";
                dto.Content = "Async content.";
                dto.Bloggers.SelectedValue = db.Blogs.First().BlogId.ToString();
                dto.UserChosenTags.FinalSelection = db.Tags.Take(2).Select(t => t.TagId.ToString()).ToArray();

                var response = await createSvc.CreateAsync(dto);
                Assert.That(response.IsValid, Is.True, string.Join(";", response.Errors.Select(e => e.ErrorMessage)));
            }

            using (var scope = app.CreateScope())
            {
                var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                createdId = db.Posts.Count(p => p.Title == "Async New Post");
                Assert.That(createdId, Is.EqualTo(1));
            }
        }
    }
}
