using System;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Linq.Expressions;
using System.Reflection;
using AutoMapper;
using GenericServices.Internal;

namespace GenericServices.Services
{
    /// <summary>
    /// Shared state and helpers for the concrete service classes.
    /// </summary>
    public abstract class GenericServiceBase
    {
        protected readonly IGenericServicesDbContext Context;
        protected readonly IMapper Mapper;

        protected GenericServiceBase(IGenericServicesDbContext context, IMapper mapper)
        {
            Context = context;
            Mapper = mapper;
        }

        protected static bool IsDto(Type type)
        {
            return DtoReflection.IsDto(type);
        }

        /// <summary>
        /// Builds a predicate matching the DTO's [Key] property/properties against the given key values.
        /// </summary>
        internal static Expression<Func<T, bool>> BuildKeyPredicate<T>(object[] keys)
        {
            var keyProps = typeof(T).GetProperties(BindingFlags.Public | BindingFlags.Instance)
                .Where(p => p.IsDefined(typeof(KeyAttribute), true))
                .ToArray();

            if (keyProps.Length == 0)
                throw new InvalidOperationException(
                    string.Format("The DTO '{0}' has no [Key] property to identify the row.", typeof(T).Name));

            var param = Expression.Parameter(typeof(T), "x");
            Expression body = null;
            for (var i = 0; i < keyProps.Length; i++)
            {
                var member = Expression.Property(param, keyProps[i]);
                var value = Convert.ChangeType(keys[i], keyProps[i].PropertyType);
                var equal = Expression.Equal(member, Expression.Constant(value, keyProps[i].PropertyType));
                body = body == null ? equal : Expression.AndAlso(body, equal);
            }

            return Expression.Lambda<Func<T, bool>>(body, param);
        }
    }
}
