using System;
using System.Threading.Tasks;
using GenericLibsBase.Core;

namespace GenericServices.Core
{
    /// <summary>
    /// Base class for an asynchronous DTO mapped to <typeparamref name="TEntity"/>.
    /// </summary>
    public abstract class EfGenericDtoAsync<TEntity, TDto> : EfGenericDtoBase<TEntity, TDto>, IAsyncInternalDtoCrud
        where TEntity : class
        where TDto : EfGenericDtoAsync<TEntity, TDto>
    {
        protected internal virtual Task SetupSecondaryDataAsync(IGenericServicesDbContext context, TDto dto)
        {
            return Task.CompletedTask;
        }

        protected internal virtual Task<ISuccessOrErrors<TEntity>> CreateDataFromDtoAsync(IGenericServicesDbContext context, TDto source)
        {
            var entity = System.Activator.CreateInstance<TEntity>();
            EntityFromDto.Copy(source, entity);
            ISuccessOrErrors<TEntity> status = new SuccessOrErrors<TEntity>().SetSuccessWithResult(entity, string.Empty);
            return Task.FromResult(status);
        }

        protected internal virtual Task<ISuccessOrErrors> UpdateDataFromDtoAsync(IGenericServicesDbContext context, TDto source, TEntity destination)
        {
            EntityFromDto.Copy(source, destination);
            return Task.FromResult(SuccessOrErrors.Success(string.Empty));
        }

        //--------------------------------------------------
        //non-generic entry points used by the service layer

        Type IInternalDtoCrud.AssociatedEntityType
        {
            get { return EntityTypeInternal; }
        }

        object[] IInternalDtoCrud.GetKeyValues()
        {
            return GetKeyValuesInternal();
        }

        CrudFunctions IInternalDtoCrud.GetSupportedFunctions()
        {
            return GetSupportedFunctionsInternal();
        }

        async Task IAsyncInternalDtoCrud.RunSetupSecondaryDataAsync(IGenericServicesDbContext context)
        {
            await SetupSecondaryDataAsync(context, Self).ConfigureAwait(false);
        }

        async Task<ISuccessOrErrors> IAsyncInternalDtoCrud.RunCreateDataFromDtoAsync(IGenericServicesDbContext context)
        {
            var status = await CreateDataFromDtoAsync(context, Self).ConfigureAwait(false);
            if (status.IsValid)
                context.Set<TEntity>().Add(status.Result);
            return status;
        }

        async Task<ISuccessOrErrors> IAsyncInternalDtoCrud.RunUpdateDataFromDtoAsync(IGenericServicesDbContext context, object destinationEntity)
        {
            return await UpdateDataFromDtoAsync(context, Self, (TEntity)destinationEntity).ConfigureAwait(false);
        }

        private TDto Self
        {
            get { return (TDto)(object)this; }
        }
    }
}
