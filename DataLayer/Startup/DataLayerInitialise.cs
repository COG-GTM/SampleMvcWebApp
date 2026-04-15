#region licence
// The MIT License (MIT)
// 
// Filename: DataLayerInitialise.cs
// Date Created: 2014/06/09
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
using System;
using System.Linq;
using DataLayer.DataClasses;
using DataLayer.Startup.Internal;
using Microsoft.Extensions.Logging;

namespace DataLayer.Startup
{
    public enum TestDataSelection
    {
        Small,
        Medium
    }

    public static class DataLayerInitialise
    {
        private static ILogger _logger;

        public static void InitialiseThis(ILoggerFactory loggerFactory = null)
        {
            if (loggerFactory != null)
                _logger = loggerFactory.CreateLogger("DataLayerInitialise");
        }

        /// <summary>
        /// Seeds the database with initial data if the database is empty,
        /// or resets the data if called explicitly.
        /// </summary>
        public static void SeedDatabase(SampleWebAppDb context, TestDataSelection selection = TestDataSelection.Small)
        {
            if (context.Blogs.Any()) return;
            ResetBlogs(context, selection);
        }

        /// <summary>
        /// This resets the blogs content by deleting all existing data and reloading from the XML file.
        /// </summary>
        public static void ResetBlogs(SampleWebAppDb context, TestDataSelection selection = TestDataSelection.Small)
        {
            try
            {
                context.Posts.RemoveRange(context.Posts);
                context.Tags.RemoveRange(context.Tags);
                context.Blogs.RemoveRange(context.Blogs);
                context.SaveChanges();

                var filepath = selection == TestDataSelection.Small
                    ? "DataLayer.Startup.Internal.BlogsContentSimple.xml"
                    : "DataLayer.Startup.Internal.BlogsContextMedium.xml";

                var blogs = LoadDbDataFromXml.FormBlogsWithPosts(filepath);
                context.Blogs.AddRange(blogs);
                context.SaveChanges();

                _logger?.LogInformation("Successfully reset blogs data with {Selection} dataset.", selection);
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Error resetting blogs data.");
                throw;
            }
        }
    }
}
