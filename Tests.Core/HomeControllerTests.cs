using Microsoft.AspNetCore.Mvc;
using SampleWebApp.Core.Controllers;
using SampleWebApp.Core.Models;
using Xunit;

namespace Tests.Core
{
    public class HomeControllerTests
    {
        private static HomeController CreateController() => new HomeController();

        [Fact]
        public void Index_returns_default_view()
        {
            var controller = CreateController();

            var result = controller.Index();

            var viewResult = Assert.IsType<ViewResult>(result);
            Assert.True(string.IsNullOrEmpty(viewResult.ViewName));
            Assert.Null(viewResult.Model);
        }

        [Fact]
        public void About_returns_default_view_with_message_in_ViewBag()
        {
            var controller = CreateController();

            var result = controller.About();

            var viewResult = Assert.IsType<ViewResult>(result);
            Assert.True(string.IsNullOrEmpty(viewResult.ViewName));
            Assert.Equal("Your application description page.", (string)controller.ViewBag.Message);
        }

        [Fact]
        public void Contact_returns_default_view()
        {
            var controller = CreateController();

            var result = controller.Contact();

            var viewResult = Assert.IsType<ViewResult>(result);
            Assert.True(string.IsNullOrEmpty(viewResult.ViewName));
            Assert.Null(viewResult.Model);
        }

        [Fact]
        public void Internals_returns_view_with_InternalsInfo_model()
        {
            var controller = CreateController();

            var result = controller.Internals();

            var viewResult = Assert.IsType<ViewResult>(result);
            var model = Assert.IsType<InternalsInfo>(viewResult.Model);

            Assert.True(model.WorkerThreads > 0);
            Assert.True(model.AvailableThreads >= 0);
            Assert.True(model.AvailableMbytes >= 0);
            Assert.True(model.HeapMemoryUsedKbytes >= 0);
        }

        [Fact]
        public void CodeView_returns_default_view()
        {
            var controller = CreateController();

            var result = controller.CodeView();

            var viewResult = Assert.IsType<ViewResult>(result);
            Assert.True(string.IsNullOrEmpty(viewResult.ViewName));
            Assert.Null(viewResult.Model);
        }
    }
}
