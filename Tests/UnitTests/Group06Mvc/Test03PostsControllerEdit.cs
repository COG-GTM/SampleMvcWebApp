#region licence
// The MIT License (MIT)
// 
// Filename: Test03PostsControllerEdit.cs
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
using System.Threading.Tasks;
using GenericServices;
using Microsoft.AspNetCore.Mvc;
using Moq;
using NUnit.Framework;
using SampleWebApp.Controllers;
using ServiceLayer.PostServices;

namespace Tests.UnitTests.Group06Mvc
{
    /// <summary>
    /// ICrudServices.ReadSingle returns null when the key does not match a row. The Post Edit GET
    /// actions then hand that result to IPostCrudHelper to fill the blogger dropdown and tag
    /// multi-select, which dereferences it, so a stale or invalid id must be turned into a
    /// NotFound result before the secondary data is set up.
    /// </summary>
    public class Test03PostsControllerEdit
    {
        [Test]
        public void Check01EditWithUnknownIdReturnsNotFound()
        {
            //SETUP
            var service = new Mock<ICrudServices>();
            service.Setup(x => x.ReadSingle<DetailPostDto>(It.IsAny<object[]>()))
                .Returns((DetailPostDto)null);
            var crudHelper = new Mock<IPostCrudHelper>(MockBehavior.Strict);
            var controller = new PostsController();

            //ATTEMPT
            var result = controller.Edit(123, service.Object, crudHelper.Object);

            //VERIFY
            Assert.That(result, Is.InstanceOf<NotFoundResult>());
            crudHelper.Verify(x => x.SetupSecondaryData(It.IsAny<DetailPostDto>()), Times.Never);
        }

        [Test]
        public void Check02EditWithKnownIdSetsUpSecondaryDataAndReturnsView()
        {
            //SETUP
            var dto = new DetailPostDto();
            var service = new Mock<ICrudServices>();
            service.Setup(x => x.ReadSingle<DetailPostDto>(It.IsAny<object[]>())).Returns(dto);
            var crudHelper = new Mock<IPostCrudHelper>();
            var controller = new PostsController();

            //ATTEMPT
            var result = controller.Edit(1, service.Object, crudHelper.Object);

            //VERIFY
            Assert.That(result, Is.InstanceOf<ViewResult>());
            Assert.That(((ViewResult)result).Model, Is.SameAs(dto));
            crudHelper.Verify(x => x.SetupSecondaryData(dto), Times.Once);
        }

        [Test]
        public async Task Check03AsyncEditWithUnknownIdReturnsNotFound()
        {
            //SETUP
            var service = new Mock<ICrudServicesAsync>();
            service.Setup(x => x.ReadSingleAsync<DetailPostDtoAsync>(It.IsAny<object[]>()))
                .ReturnsAsync((DetailPostDtoAsync)null);
            var crudHelper = new Mock<IPostCrudHelper>(MockBehavior.Strict);
            var controller = new PostsAsyncController();

            //ATTEMPT
            var result = await controller.Edit(123, service.Object, crudHelper.Object);

            //VERIFY
            Assert.That(result, Is.InstanceOf<NotFoundResult>());
            crudHelper.Verify(x => x.SetupSecondaryDataAsync(It.IsAny<DetailPostDtoAsync>()), Times.Never);
        }
    }
}
