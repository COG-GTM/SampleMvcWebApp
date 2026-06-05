using System.Linq;
using System.Threading.Tasks;
using AutoMapper;
using GenericLibsBase.Core;

namespace GenericServices.Core
{
    /// <summary>
    /// Non generic base class for all DTOs handled by the service layer. The service layer talks to
    /// the DTOs through these internal members so it does not need to know the entity (TData) type.
    /// Compatibility replacement for the original GenericServices EfGenericDtoBase.
    /// </summary>
    public abstract class EfGenericDtoBase
    {
        /// <summary>
        /// Defines which CRUD functions the DTO supports.
        /// </summary>
        protected abstract CrudFunctions SupportedFunctions { get; }

        internal abstract IQueryable BuildListQuery(IGenericServicesDbContext context, IMapper mapper);

        internal abstract object GetDetailDto(IGenericServicesDbContext context, IMapper mapper, int id);

        internal abstract Task<object> GetDetailDtoAsync(IGenericServicesDbContext context, IMapper mapper, int id);

        internal abstract void RunSetupSecondaryData(IGenericServicesDbContext context, IMapper mapper);

        internal abstract Task RunSetupSecondaryDataAsync(IGenericServicesDbContext context, IMapper mapper);

        internal abstract ISuccessOrErrors CreateInDb(IGenericServicesDbContext context, IMapper mapper);

        internal abstract Task<ISuccessOrErrors> CreateInDbAsync(IGenericServicesDbContext context, IMapper mapper);

        internal abstract ISuccessOrErrors UpdateInDb(IGenericServicesDbContext context, IMapper mapper);

        internal abstract Task<ISuccessOrErrors> UpdateInDbAsync(IGenericServicesDbContext context, IMapper mapper);
    }
}
