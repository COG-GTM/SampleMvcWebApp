using System;
using System.ComponentModel.DataAnnotations;
using System.Reflection;

namespace GenericServices.Core
{
    /// <summary>
    /// Copies the writable data from a DTO onto a data entity for create/update. This replaces
    /// the AutoMapper "write" map GenericServices used in EF6. Doing the copy by reflection keeps
    /// full control over entity navigations: related entities (e.g. the chosen Tags) are assigned
    /// by reference so EF Core tracks them, rather than being cloned.
    /// Rules:
    ///  - properties marked [DoNotCopyBackToDatabase] are skipped (data layer owns them),
    ///  - [Key] properties are skipped (never overwrite the primary key),
    ///  - only properties that exist and are writable on the entity, with an assignable value, are copied.
    /// </summary>
    internal static class EntityFromDto
    {
        public static void Copy(object source, object destination)
        {
            var dtoType = source.GetType();
            var entityType = destination.GetType();

            foreach (var dtoProp in dtoType.GetProperties(BindingFlags.Public | BindingFlags.Instance))
            {
                if (!dtoProp.CanRead) continue;
                if (dtoProp.GetIndexParameters().Length != 0) continue;
                if (dtoProp.IsDefined(typeof(DoNotCopyBackToDatabaseAttribute), true)) continue;
                if (dtoProp.IsDefined(typeof(KeyAttribute), true)) continue;

                var entityProp = entityType.GetProperty(dtoProp.Name, BindingFlags.Public | BindingFlags.Instance);
                if (entityProp == null || !entityProp.CanWrite) continue;

                var value = dtoProp.GetValue(source);
                if (value != null && !entityProp.PropertyType.IsInstanceOfType(value)) continue;
                if (value == null && entityProp.PropertyType.IsValueType &&
                    Nullable.GetUnderlyingType(entityProp.PropertyType) == null)
                    continue;

                entityProp.SetValue(destination, value);
            }
        }
    }
}
