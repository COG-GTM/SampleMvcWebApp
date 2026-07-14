using System;
using GenericLibsBase.Core;

namespace GenericServices.Core
{
    /// <summary>
    /// Base class for a synchronous DTO mapped to <typeparamref name="TEntity"/>.
    /// Concrete DTOs override the hooks to shape data and add business logic.
    /// </summary>
    public abstract class EfGenericDto<TEntity, TDto> : EfGenericDtoBase<TEntity, TDto>, ISyncInternalDtoCrud
        where TEntity : class
        where TDto : EfGenericDto<TEntity, TDto>
    {
        /// <summary>
        /// Override to fill in any secondary data (dropdown lists etc.) needed by the view.
        /// </summary>
        protected internal virtual void SetupSecondaryData(IGenericServicesDbContext context, TDto dto)
        {
        }

        /// <summary>
        /// Builds a new entity from this DTO. Default behaviour maps the DTO onto a new entity.
        /// </summary>
        protected internal virtual ISuccessOrErrors<TEntity> CreateDataFromDto(IGenericServicesDbContext context, TDto source)
        {
            var entity = System.Activator.CreateInstance<TEntity>();
            EntityFromDto.Copy(source, entity);
            return new SuccessOrErrors<TEntity>().SetSuccessWithResult(entity, string.Empty);
        }

        /// <summary>
        /// Copies this DTO onto an existing entity. Default behaviour maps the DTO onto the entity.
        /// </summary>
        protected internal virtual ISuccessOrErrors UpdateDataFromDto(IGenericServicesDbContext context, TDto source, TEntity destination)
        {
            EntityFromDto.Copy(source, destination);
            return SuccessOrErrors.Success(string.Empty);
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

        void ISyncInternalDtoCrud.RunSetupSecondaryData(IGenericServicesDbContext context)
        {
            SetupSecondaryData(context, Self);
        }

        ISuccessOrErrors ISyncInternalDtoCrud.RunCreateDataFromDto(IGenericServicesDbContext context)
        {
            var status = CreateDataFromDto(context, Self);
            if (status.IsValid)
                context.Set<TEntity>().Add(status.Result);
            return status;
        }

        ISuccessOrErrors ISyncInternalDtoCrud.RunUpdateDataFromDto(IGenericServicesDbContext context, object destinationEntity)
        {
            return UpdateDataFromDto(context, Self, (TEntity)destinationEntity);
        }

        private TDto Self
        {
            get { return (TDto)(object)this; }
        }
    }
}
