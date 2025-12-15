#region licence
// The MIT License (MIT)
// 
// Filename: DataLayerCoreInitialise.cs
// Date Created: 2014/05/20
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
using System.Collections.Generic;
using System.Linq;
using DataLayer.Core.DataClasses;
using DataLayer.Core.Startup.Internal;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace DataLayer.Core.Startup
{
    public enum TestDataSelection { Small = 0, Medium = 1 }

    public static class DataLayerCoreInitialise
    {
        private static ILogger _logger;

        private static readonly Dictionary<TestDataSelection, string> XmlBlogsDataFileManifestPath = new Dictionary<TestDataSelection, string>
        {
            { TestDataSelection.Small, "DataLayer.Core.Startup.Internal.BlogsContentSimple.xml" },
            { TestDataSelection.Medium, "DataLayer.Core.Startup.Internal.BlogsContextMedium.xml" }
        };

        public static void InitialiseThis(SampleWebAppDbCore context, ILogger logger = null, bool ensureCreated = true)
        {
            _logger = logger;

            if (ensureCreated)
            {
                context.Database.EnsureCreated();
            }
        }

        public static void MigrateDatabase(SampleWebAppDbCore context, ILogger logger = null)
        {
            _logger = logger;

            try
            {
                context.Database.Migrate();
                _logger?.LogInformation("Database migration completed successfully.");
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Error during database migration.");
                throw;
            }
        }

        public static void ResetBlogs(SampleWebAppDbCore context, TestDataSelection selection)
        {
            try
            {
                context.Posts.ToList().ForEach(x => context.Posts.Remove(x));
                context.Tags.ToList().ForEach(x => context.Tags.Remove(x));
                context.Blogs.ToList().ForEach(x => context.Blogs.Remove(x));
                context.SaveChanges();
            }
            catch (Exception ex)
            {
                _logger?.LogCritical(ex, "Exception when resetting the blogs");
                throw;
            }

            var bloggers = LoadDbDataFromXml.FormBlogsWithPosts(XmlBlogsDataFileManifestPath[selection]);

            context.Blogs.AddRange(bloggers);
            
            try
            {
                context.SaveChanges();
            }
            catch (Exception ex)
            {
                _logger?.LogCritical(ex, "Error when resetting courses data.");
                throw new FormatException("Problem writing to database. See log.", ex);
            }
        }
    }
}
