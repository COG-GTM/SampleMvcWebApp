#region licence
// The MIT License (MIT)
// 
// Filename: Test12PostDtoValidation.cs
// Date Created: 2014/08/16
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
using System.Collections.Generic;
using DataLayer.DataClasses.Concrete;
using NUnit.Framework;
using ServiceLayer.PostServices;
using Tests.Helpers;

namespace Tests.UnitTests.Group03ServiceLayer
{
    /// <summary>
    /// On a Create/Edit POST the DetailPostDto is model-bound and ASP.NET Core's validation visitor reads
    /// every gettable property, including the computed TagNames. The Tags collection is not posted, so it
    /// is null at that point; TagNames must not throw (an unhandled ArgumentNullException surfaced as an
    /// HTTP 500 and stopped Posts create/edit before the controller ran).
    /// </summary>
    public class Test12PostDtoValidation
    {
        [Test]
        public void Check01TagNamesWhenTagsNullReturnsEmpty()
        {
            //SETUP
            var dto = new DetailPostDto { Tags = null };

            //ATTEMPT
            var tagNames = dto.TagNames;

            //VERIFY
            tagNames.ShouldEqual(string.Empty);
        }

        [Test]
        public void Check02TagNamesAsyncWhenTagsNullReturnsEmpty()
        {
            //SETUP
            var dto = new DetailPostDtoAsync { Tags = null };

            //ATTEMPT
            var tagNames = dto.TagNames;

            //VERIFY
            tagNames.ShouldEqual(string.Empty);
        }

        [Test]
        public void Check05TagNamesWhenTagsPopulatedJoinsNames()
        {
            //SETUP
            var dto = new DetailPostDto
            {
                Tags = new List<Tag>
                {
                    new Tag { Name = "Programming" },
                    new Tag { Name = "Education" }
                }
            };

            //ATTEMPT
            var tagNames = dto.TagNames;

            //VERIFY
            tagNames.ShouldEqual("Programming, Education");
        }
    }
}
