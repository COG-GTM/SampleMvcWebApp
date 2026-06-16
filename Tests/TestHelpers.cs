using System;
using AutoMapper;
using DataLayer.DataClasses;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using ServiceLayer.Startup;

namespace Tests
{
    internal static class TestHelpers
    {
        /// <summary>
        /// Creates a fresh EF Core in-memory SampleWebAppDb with a unique database name
        /// so each test is isolated from the others.
        /// </summary>
        public static SampleWebAppDb CreateInMemoryDb()
        {
            var options = new DbContextOptionsBuilder<SampleWebAppDb>()
                .UseInMemoryDatabase("TestDb-" + Guid.NewGuid())
                .ConfigureWarnings(w => w.Ignore(Microsoft.EntityFrameworkCore.Diagnostics.InMemoryEventId.TransactionIgnoredWarning))
                .Options;
            return new SampleWebAppDb(options);
        }

        /// <summary>
        /// Builds the same AutoMapper configuration the web application uses.
        /// </summary>
        public static IMapper CreateMapper()
        {
            var config = new MapperConfiguration(
                cfg => cfg.AddProfile<ServiceLayerMappingProfile>(),
                NullLoggerFactory.Instance);
            config.AssertConfigurationIsValid();
            return config.CreateMapper();
        }
    }
}
