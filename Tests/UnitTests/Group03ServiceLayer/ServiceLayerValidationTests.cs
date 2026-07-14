using System.Linq;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using GenericServices;
using Microsoft.Extensions.DependencyInjection;
using NUnit.Framework;
using ServiceLayer.PostServices;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    [TestFixture]
    public class ServiceLayerValidationTests
    {
        [Test]
        public void CreatePost_WithNoBlogger_IsRejected()
        {
            using var app = new TestServices();
            using var scope = app.CreateScope();
            var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
            var createSetup = scope.ServiceProvider.GetRequiredService<ICreateSetupService>();
            var createSvc = scope.ServiceProvider.GetRequiredService<ICreateService>();

            var dto = createSetup.GetDto<DetailPostDto>();
            dto.Title = "No blogger post";
            dto.Content = "content";
            dto.Bloggers.SelectedValue = null;
            dto.UserChosenTags.FinalSelection = new[] { db.Tags.First().TagId.ToString() };

            var response = createSvc.Create(dto);

            Assert.That(response.IsValid, Is.False);
            Assert.That(string.Join(";", response.Errors.Select(e => e.ErrorMessage)),
                Does.Contain("The blogger was not selected"));
        }

        [Test]
        public void CreatePost_WithNoTags_IsRejected()
        {
            using var app = new TestServices();
            using var scope = app.CreateScope();
            var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
            var createSetup = scope.ServiceProvider.GetRequiredService<ICreateSetupService>();
            var createSvc = scope.ServiceProvider.GetRequiredService<ICreateService>();

            var dto = createSetup.GetDto<DetailPostDto>();
            dto.Title = "No tags post";
            dto.Content = "content";
            dto.Bloggers.SelectedValue = db.Blogs.First().BlogId.ToString();
            dto.UserChosenTags.FinalSelection = new string[0];

            var response = createSvc.Create(dto);

            Assert.That(response.IsValid, Is.False);
            Assert.That(string.Join(";", response.Errors.Select(e => e.ErrorMessage)),
                Does.Contain("at least one tag"));
        }

        [Test]
        public void CreatePost_WithExclamationInTitle_IsRejectedByEntityRule()
        {
            using var app = new TestServices();
            using var scope = app.CreateScope();
            var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
            var createSetup = scope.ServiceProvider.GetRequiredService<ICreateSetupService>();
            var createSvc = scope.ServiceProvider.GetRequiredService<ICreateService>();

            var dto = createSetup.GetDto<DetailPostDto>();
            dto.Title = "Bad!";
            dto.Content = "ok content";
            dto.Bloggers.SelectedValue = db.Blogs.First().BlogId.ToString();
            dto.UserChosenTags.FinalSelection = new[] { db.Tags.First().TagId.ToString() };

            var response = createSvc.Create(dto);

            Assert.That(response.IsValid, Is.False);
        }

        [Test]
        public void CreateTag_WithDuplicateSlug_IsRejected()
        {
            using var app = new TestServices();
            using var scope = app.CreateScope();
            var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
            var createSvc = scope.ServiceProvider.GetRequiredService<ICreateService>();

            var existingSlug = db.Tags.First().Slug;
            var response = createSvc.Create(new Tag { Name = "Some Name", Slug = existingSlug });

            Assert.That(response.IsValid, Is.False);
            Assert.That(string.Join(";", response.Errors.Select(e => e.ErrorMessage)),
                Does.Contain("must be unique"));
        }
    }
}
