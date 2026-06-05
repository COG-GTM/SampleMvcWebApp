using System.Linq;
using System.Threading.Tasks;
using AutoMapper;
using AutoMapper.QueryableExtensions;
using GenericLibsBase.Core;

namespace GenericServices.Core
{
    /// <summary>
    /// Synchronous DTO base. TData is the database entity, TDto is the derived DTO type
    /// (curiously recurring generic pattern, matching the original GenericServices design).
    /// </summary>
    public abstract class EfGenericDto<TData, TDto> : EfGenericDtoBase
        where TData : class, new()
        where TDto : EfGenericDto<TData, TDto>, new()
    {
        //--------------------------------------------------
        //overridable behaviour

        /// <summary>
        /// Override to set up any secondary data (drop down lists etc.) needed by the view.
        /// </summary>
        protected virtual void SetupSecondaryData(IGenericServicesDbContext context, TDto dto)
        {
        }

        /// <summary>
        /// Override to control how a new entity is created from the DTO.
        /// </summary>
        protected virtual ISuccessOrErrors<TData> CreateDataFromDto(IGenericServicesDbContext context, TDto source)
        {
            var data = new TData();
            DtoMappingHelper.CopyDtoToData(source, data);
            return new SuccessOrErrors<TData>().SetSuccessWithResult(data, "Created the data from the dto.");
        }

        /// <summary>
        /// Override to control how an existing entity is updated from the DTO.
        /// </summary>
        protected virtual ISuccessOrErrors UpdateDataFromDto(IGenericServicesDbContext context, TDto source, TData destination)
        {
            DtoMappingHelper.CopyDtoToData(source, destination);
            return SuccessOrErrors.Success("Updated the data from the dto.");
        }

        //--------------------------------------------------
        //internal plumbing used by the service layer

        internal override IQueryable BuildListQuery(IGenericServicesDbContext context, IMapper mapper)
        {
            return context.Set<TData>().ProjectTo<TDto>(mapper.ConfigurationProvider);
        }

        internal override object GetDetailDto(IGenericServicesDbContext context, IMapper mapper, int id)
        {
            var keyName = DtoMappingHelper.GetKeyName(typeof(TDto));
            var dto = context.Set<TData>()
                .Where(DtoMappingHelper.BuildKeyEquals<TData>(keyName, id))
                .ProjectTo<TDto>(mapper.ConfigurationProvider)
                .SingleOrDefault();

            if (dto != null)
                dto.SetupSecondaryData(context, dto);
            return dto;
        }

        internal override Task<object> GetDetailDtoAsync(IGenericServicesDbContext context, IMapper mapper, int id)
        {
            return Task.FromResult(GetDetailDto(context, mapper, id));
        }

        internal override void RunSetupSecondaryData(IGenericServicesDbContext context, IMapper mapper)
        {
            SetupSecondaryData(context, (TDto)(object)this);
        }

        internal override Task RunSetupSecondaryDataAsync(IGenericServicesDbContext context, IMapper mapper)
        {
            RunSetupSecondaryData(context, mapper);
            return Task.CompletedTask;
        }

        internal override ISuccessOrErrors CreateInDb(IGenericServicesDbContext context, IMapper mapper)
        {
            var status = CreateDataFromDto(context, (TDto)(object)this);
            if (!status.IsValid)
                return status;
            context.Set<TData>().Add(status.Result);
            return context.SaveChangesWithChecking();
        }

        internal override Task<ISuccessOrErrors> CreateInDbAsync(IGenericServicesDbContext context, IMapper mapper)
        {
            return Task.FromResult(CreateInDb(context, mapper));
        }

        internal override ISuccessOrErrors UpdateInDb(IGenericServicesDbContext context, IMapper mapper)
        {
            var keyValue = DtoMappingHelper.GetKeyValue(this);
            var data = context.Set<TData>().Find(keyValue);
            if (data == null)
                return new SuccessOrErrors().AddSingleError(
                    "Could not find the {0} you requested. Did another user delete it?", typeof(TData).Name);

            var status = UpdateDataFromDto(context, (TDto)(object)this, data);
            if (!status.IsValid)
                return status;
            return context.SaveChangesWithChecking();
        }

        internal override Task<ISuccessOrErrors> UpdateInDbAsync(IGenericServicesDbContext context, IMapper mapper)
        {
            return Task.FromResult(UpdateInDb(context, mapper));
        }
    }
}
