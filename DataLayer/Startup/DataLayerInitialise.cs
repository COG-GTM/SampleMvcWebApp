using System;
using System.Collections.Generic;
using System.Linq;
using DataLayer.DataClasses;
using DataLayer.Startup.Internal;
using GenericLibsBase;
using GenericServices;
using Microsoft.EntityFrameworkCore;

namespace DataLayer.Startup
{
    public enum TestDataSelection { Small = 0, Medium = 1 }

    public static class DataLayerInitialise
    {
        private static IGenericLogger _logger;

        private static readonly Dictionary<TestDataSelection, string> XmlBlogsDataFileManifestPath =
            new Dictionary<TestDataSelection, string>
            {
                { TestDataSelection.Small, "DataLayer.Startup.Internal.BlogsContentSimple.xml" },
                { TestDataSelection.Medium, "DataLayer.Startup.Internal.BlogsContextMedium.xml" }
            };

        /// <summary>
        /// Called at startup. Ensures the database exists (creating it if the provider allows)
        /// and seeds it with test data if it is empty.
        /// </summary>
        public static void InitialiseThis(SampleWebAppDb context, bool canCreateDatabase,
            TestDataSelection selection = TestDataSelection.Medium)
        {
            _logger = GenericLibsBaseConfig.GetLogger("DataLayerInitialise");

            if (canCreateDatabase)
                context.Database.EnsureCreated();

            if (!context.Blogs.Any())
                ResetBlogs(context, selection);
        }

        public static void ResetBlogs(SampleWebAppDb context, TestDataSelection selection)
        {
            _logger = _logger ?? GenericLibsBaseConfig.GetLogger("DataLayerInitialise");

            try
            {
                context.Posts.ToList().ForEach(x => context.Posts.Remove(x));
                context.Tags.ToList().ForEach(x => context.Tags.Remove(x));
                context.Blogs.ToList().ForEach(x => context.Blogs.Remove(x));
                context.SaveChanges();
            }
            catch (Exception ex)
            {
                _logger.Critical("Exception when resetting the blogs", ex);
                throw;
            }

            var bloggers = LoadDbDataFromXml.FormBlogsWithPosts(XmlBlogsDataFileManifestPath[selection]);

            context.Blogs.AddRange(bloggers);
            var status = context.SaveChangesWithChecking();
            if (!status.IsValid)
            {
                _logger.CriticalFormat("Error when resetting courses data. Error:\n{0}",
                    string.Join(",", status.Errors));
                throw new FormatException("problem writing to database. See log.");
            }
        }
    }
}
