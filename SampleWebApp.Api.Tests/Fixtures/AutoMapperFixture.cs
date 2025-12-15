using AutoMapper;
using SampleWebApp.Core.Mapping;

namespace SampleWebApp.Api.Tests.Fixtures
{
    public class AutoMapperFixture
    {
        public IMapper Mapper { get; }

        public AutoMapperFixture()
        {
            var config = new MapperConfiguration(cfg =>
            {
                cfg.AddProfile<MappingProfile>();
            });

            Mapper = config.CreateMapper();
        }
    }
}
