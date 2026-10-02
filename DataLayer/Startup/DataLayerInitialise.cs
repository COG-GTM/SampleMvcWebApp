#region licence
// The MIT License (MIT)
// 
// Filename: DataLayerInitialise.cs
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
using DataLayer.DataClasses;
using DataLayer.Startup.Internal;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Infrastructure;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;

namespace DataLayer.Startup
{
    public enum TestDataSelection { Small = 0, Medium = 1}

    public static class DataLayerInitialise
    {
        private static readonly Dictionary<TestDataSelection, string> XmlBlogsDataFileManifestPath = new Dictionary<TestDataSelection, string>
            {
                {TestDataSelection.Small, "DataLayer.Startup.Internal.BlogsContentSimple.xml"},
                {TestDataSelection.Medium, "DataLayer.Startup.Internal.BlogsContextMedium.xml"}
            };

        /// <summary>
        /// This should be called at Startup
        /// </summary>
        /// <param name="db"></param>
        /// <param name="canCreateDatabase">true if the database provider allows the app to create/migrate the database</param>
        public static void InitialiseThis(SampleWebAppDb db, bool canCreateDatabase)
        {
            if (canCreateDatabase)
                db.Database.Migrate();
        }

        public static void ResetBlogs(SampleWebAppDb db, TestDataSelection selection)
        {
            var logger = GetLogger(db);
            try
            {
                db.Posts.ToList().ForEach(x => db.Posts.Remove(x));
                db.Tags.ToList().ForEach(x => db.Tags.Remove(x));
                db.Blogs.ToList().ForEach(x => db.Blogs.Remove(x));
                db.SaveChanges();
            }
            catch (Exception ex)
            {
                logger.LogCritical(ex, "Exception when resetting the blogs");
                throw;
            }

            var bloggers = LoadDbDataFromXml.FormBlogsWithPosts(XmlBlogsDataFileManifestPath[selection]);

            db.Blogs.AddRange(bloggers);
            var status = db.SaveChangesWithChecking();
            if (!status.IsValid)
            {
                logger.LogCritical("Error when resetting courses data. Error:\n{Errors}",
                    string.Join(",", status.Errors));
                throw new FormatException("problem writing to database. See log.");
            }
        }

        /// <summary>
        /// Loads the selected test data only if there are no blogs in the database
        /// </summary>
        public static void SeedIfEmpty(SampleWebAppDb db, TestDataSelection selection)
        {
            if (!db.Blogs.Any())
                ResetBlogs(db, selection);
        }

        private static ILogger GetLogger(SampleWebAppDb db)
        {
            var loggerFactory = db.GetInfrastructure().GetService<ILoggerFactory>();
            return loggerFactory?.CreateLogger(typeof(DataLayerInitialise).FullName) ?? NullLogger.Instance;
        }
    }

}
