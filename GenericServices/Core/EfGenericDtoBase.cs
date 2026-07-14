using System;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Reflection;

namespace GenericServices.Core
{
    /// <summary>
    /// Non-generic root of the DTO hierarchy. Lets non-generic code (the service layer,
    /// the mapper configuration) recognise DTOs and read their metadata.
    /// </summary>
    public abstract class EfGenericDtoBase
    {
        /// <summary>
        /// Which CRUD functions this DTO supports. Override in the concrete DTO.
        /// </summary>
        protected internal virtual CrudFunctions SupportedFunctions
        {
            get { return CrudFunctions.None; }
        }

        internal CrudFunctions GetSupportedFunctionsInternal()
        {
            return SupportedFunctions;
        }

        /// <summary>
        /// Reads the values of the DTO properties marked with <see cref="KeyAttribute"/>,
        /// used to locate the matching data entity for update.
        /// </summary>
        internal object[] GetKeyValuesInternal()
        {
            var keyProps = GetType().GetProperties(BindingFlags.Public | BindingFlags.Instance)
                .Where(p => p.IsDefined(typeof(KeyAttribute), true))
                .ToArray();
            return keyProps.Select(p => p.GetValue(this)).ToArray();
        }
    }

    /// <summary>
    /// Generic DTO base carrying the associated entity/DTO types. Shared by the sync and
    /// async DTO base classes.
    /// </summary>
    public abstract class EfGenericDtoBase<TEntity, TDto> : EfGenericDtoBase
        where TEntity : class
        where TDto : EfGenericDtoBase<TEntity, TDto>
    {
        internal Type EntityTypeInternal
        {
            get { return typeof(TEntity); }
        }
    }
}
