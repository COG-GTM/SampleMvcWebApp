using System;
using System.Linq;
using System.Threading.Tasks;
using AutoMapper;
using AutoMapper.QueryableExtensions;
using GenericLibsBase.Core;
using Microsoft.EntityFrameworkCore;

namespace GenericServices.Core
{
    /// <summary>
    /// Asynchronous DTO base. Mirrors <see cref="EfGenericDto{TData,TDto}"/> but exposes async hooks.
    /// </summary>
    public abstract class EfGenericDtoAsync<TData, TDto> : EfGenericDtoBase
        where TData : class, new()
        where TDto : EfGenericDtoAsync<TData, TDto>, new()
    {
        //--------------------------------------------------
        //overridable behaviour

        protected virtual Task SetupSecondaryDataAsync(IGenericServicesDbContext context, TDto dto)
        {
            return Task.CompletedTask;
        }

        protected virtual Task<ISuccessOrErrors<TData>> CreateDataFromDtoAsync(IGenericServicesDbContext context, TDto source)
        {
            var data = new TData();
            DtoMappingHelper.CopyDtoToData(source, data);
            ISuccessOrErrors<TData> status = new SuccessOrErrors<TData>().SetSuccessWithResult(data, "Created the data from the dto.");
            return Task.FromResult(status);
        }

        protected virtual Task<ISuccessOrErrors> UpdateDataFromDtoAsync(IGenericServicesDbContext context, TDto source, TData destination)
        {
            DtoMappingHelper.CopyDtoToData(source, destination);
            return Task.FromResult(SuccessOrErrors.Success("Updated the data from the dto."));
        }

        //--------------------------------------------------
        //internal plumbing used by the service layer

        internal override IQueryable BuildListQuery(IGenericServicesDbContext context, IMapper mapper)
        {
            return context.Set<TData>().ProjectTo<TDto>(mapper.ConfigurationProvider);
        }

        internal override async Task<object> GetDetailDtoAsync(IGenericServicesDbContext context, IMapper mapper, int id)
        {
            var keyName = DtoMappingHelper.GetKeyName(typeof(TDto));
            var dto = await context.Set<TData>()
                .Where(DtoMappingHelper.BuildKeyEquals<TData>(keyName, id))
                .ProjectTo<TDto>(mapper.ConfigurationProvider)
                .SingleOrDefaultAsync()
                .ConfigureAwait(false);

            if (dto != null)
                await dto.SetupSecondaryDataAsync(context, dto).ConfigureAwait(false);
            return dto;
        }

        internal override async Task RunSetupSecondaryDataAsync(IGenericServicesDbContext context, IMapper mapper)
        {
            await SetupSecondaryDataAsync(context, (TDto)(object)this).ConfigureAwait(false);
        }

        internal override async Task<ISuccessOrErrors> CreateInDbAsync(IGenericServicesDbContext context, IMapper mapper)
        {
            var status = await CreateDataFromDtoAsync(context, (TDto)(object)this).ConfigureAwait(false);
            if (!status.IsValid)
                return status;
            context.Set<TData>().Add(status.Result);
            return await context.SaveChangesWithCheckingAsync().ConfigureAwait(false);
        }

        internal override async Task<ISuccessOrErrors> UpdateInDbAsync(IGenericServicesDbContext context, IMapper mapper)
        {
            var keyValue = DtoMappingHelper.GetKeyValue(this);
            var data = await context.Set<TData>().FindAsync(keyValue).ConfigureAwait(false);
            if (data == null)
                return new SuccessOrErrors().AddSingleError(
                    "Could not find the {0} you requested. Did another user delete it?", typeof(TData).Name);

            var status = await UpdateDataFromDtoAsync(context, (TDto)(object)this, data).ConfigureAwait(false);
            if (!status.IsValid)
                return status;
            return await context.SaveChangesWithCheckingAsync().ConfigureAwait(false);
        }

        //--------------------------------------------------
        //synchronous members are not supported on async DTOs (they are always used with async services)

        internal override object GetDetailDto(IGenericServicesDbContext context, IMapper mapper, int id)
        {
            throw new NotSupportedException("Async DTOs must be used with the async service methods.");
        }

        internal override void RunSetupSecondaryData(IGenericServicesDbContext context, IMapper mapper)
        {
            throw new NotSupportedException("Async DTOs must be used with the async service methods.");
        }

        internal override ISuccessOrErrors CreateInDb(IGenericServicesDbContext context, IMapper mapper)
        {
            throw new NotSupportedException("Async DTOs must be used with the async service methods.");
        }

        internal override ISuccessOrErrors UpdateInDb(IGenericServicesDbContext context, IMapper mapper)
        {
            throw new NotSupportedException("Async DTOs must be used with the async service methods.");
        }
    }
}
