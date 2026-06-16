using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Linq.Expressions;
using System.Reflection;

namespace GenericServices.Core
{
    /// <summary>
    /// Helper methods used by the EfGenericDto base classes to copy a DTO back onto its
    /// data entity and to locate the primary key.
    /// </summary>
    internal static class DtoMappingHelper
    {
        /// <summary>
        /// Copies the simple (and directly assignable navigation) properties from the DTO onto the
        /// data entity. Properties marked with [DoNotCopyBackToDatabase], read only properties and
        /// properties with no assignable target on the entity are skipped.
        /// </summary>
        public static void CopyDtoToData(object source, object destination)
        {
            var destType = destination.GetType();
            foreach (var srcProp in source.GetType().GetProperties(BindingFlags.Public | BindingFlags.Instance))
            {
                if (!srcProp.CanRead)
                    continue;
                if (srcProp.GetCustomAttribute<DoNotCopyBackToDatabaseAttribute>() != null)
                    continue;
                if (srcProp.GetIndexParameters().Length != 0)
                    continue;

                var destProp = destType.GetProperty(srcProp.Name, BindingFlags.Public | BindingFlags.Instance);
                if (destProp == null || !destProp.CanWrite)
                    continue;
                if (!destProp.PropertyType.IsAssignableFrom(srcProp.PropertyType))
                    continue;

                destProp.SetValue(destination, srcProp.GetValue(source));
            }
        }

        public static string GetKeyName(Type dtoType)
        {
            var keyProp = dtoType.GetProperties(BindingFlags.Public | BindingFlags.Instance)
                .FirstOrDefault(p => p.GetCustomAttribute<KeyAttribute>() != null);
            if (keyProp == null)
                throw new InvalidOperationException(
                    string.Format("The type {0} does not have a property marked with [Key].", dtoType.Name));
            return keyProp.Name;
        }

        public static object GetKeyValue(object dto)
        {
            var keyName = GetKeyName(dto.GetType());
            return dto.GetType().GetProperty(keyName).GetValue(dto);
        }

        public static Expression<Func<TData, bool>> BuildKeyEquals<TData>(string keyName, int id)
        {
            var param = Expression.Parameter(typeof(TData), "e");
            var body = Expression.Equal(Expression.Property(param, keyName), Expression.Constant(id));
            return Expression.Lambda<Func<TData, bool>>(body, param);
        }
    }
}
